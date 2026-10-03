import json
import os
import sys

import oracle

SPEC_VERSION = 1
VECTOR_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "vectors")
MISSING = object()

documents = {}


def add_document(name, description, sections):
    documents[name] = {"specVersion": SPEC_VERSION, "description": description, **sections}


def bool_variants():
    return [{"key": "on", "value": True}, {"key": "off", "value": False}]


def make_flag(key="flag", type="boolean", enabled=True, kill=False, salt="a1b2c3", variants=None, off="off", rules=None, fall=None):
    return {
        "key": key,
        "type": type,
        "enabled": enabled,
        "killSwitch": kill,
        "salt": salt,
        "variants": variants if variants is not None else bool_variants(),
        "offVariant": off,
        "rules": rules if rules is not None else [],
        "fallthrough": fall if fall is not None else serve("off"),
    }


def serve(variant):
    return {"variant": variant}


def rollout(*pairs):
    return {"rollout": [{"variant": variant, "weight": weight} for variant, weight in pairs]}


def attr_cond(attribute, operator, *values, negate=False):
    condition = {"attribute": attribute, "operator": operator, "values": list(values)}
    if negate:
        condition["negate"] = True
    return condition


def seg_cond(segment, negate=False):
    condition = {"segment": segment}
    if negate:
        condition["negate"] = True
    return condition


def rule(rule_id, conditions, serve_value):
    return {"id": rule_id, "conditions": conditions, "serve": serve_value}


def segment(key, included=(), excluded=(), rules=()):
    return {"key": key, "included": list(included), "excluded": list(excluded), "rules": [list(group) for group in rules]}


def context(key, **attributes):
    return {"key": key, "attributes": {name: value for name, value in attributes.items() if value is not MISSING}}


def result(variant_key, value, reason, rule_index=None, rule_id=None, bucket=None):
    return {"variantKey": variant_key, "value": value, "reason": reason, "ruleIndex": rule_index, "ruleId": rule_id, "bucket": bucket}


ON = result("on", True, "FALLTHROUGH")


def pick(rollout_entries, bucket):
    running = 0
    for entry in rollout_entries:
        running += entry["weight"]
        if running > bucket:
            return entry["variant"]
    raise AssertionError("weights do not sum to 100000")


class Cases:
    def __init__(self, prefix):
        self.prefix = prefix
        self.items = []
        self.ids = set()

    def add(self, case_id, description, flag, ctx, expected, segments=None):
        full_id = self.prefix + "-" + case_id
        assert full_id not in self.ids, full_id
        self.ids.add(full_id)
        self.items.append({"id": full_id, "description": description, "flag": flag, "segments": segments or [], "context": ctx, "expected": expected})


def variant_value(flag, variant_key):
    return next(variant["value"] for variant in flag["variants"] if variant["key"] == variant_key)


def served(flag, variant_key, reason, rule_index=None, rule_id=None, bucket=None):
    return result(variant_key, variant_value(flag, variant_key), reason, rule_index, rule_id, bucket)


class BucketIndex:
    def __init__(self, flag_key, salt, prefix):
        self.flag_key = flag_key
        self.salt = salt
        self.prefix = prefix
        self.next_index = 0
        self.first_key_by_bucket = {}

    def grow(self):
        candidate = self.prefix + str(self.next_index)
        self.next_index += 1
        self.first_key_by_bucket.setdefault(oracle.bucket(self.flag_key, self.salt, candidate), candidate)

    def find(self, low, high):
        while True:
            for bucket_value, key in self.first_key_by_bucket.items():
                if low <= bucket_value <= high:
                    return key
            for _ in range(2000):
                self.grow()


bucket_indexes = {}


def find_key(flag_key, salt, low, high, prefix="user-"):
    index = bucket_indexes.setdefault((flag_key, salt, prefix), BucketIndex(flag_key, salt, prefix))
    return index.find(low, high)


def find_exact(flag_key, salt, wanted, prefix="exact-"):
    return find_key(flag_key, salt, wanted, wanted, prefix)


def rollout_case(cases, case_id, description, flag, ctx, segments=None, matched_rule=None):
    bucket = oracle.bucket(flag["key"], flag["salt"], ctx["key"])
    if matched_rule is None:
        entries = flag["fallthrough"]["rollout"]
        expected = served(flag, pick(entries, bucket), "FALLTHROUGH", bucket=bucket)
    else:
        entries = flag["rules"][matched_rule]["serve"]["rollout"]
        expected = served(flag, pick(entries, bucket), "RULE_MATCH", matched_rule, flag["rules"][matched_rule]["id"], bucket)
    cases.add(case_id, description, flag, ctx, expected, segments)


def build_core():
    cases = Cases("core")
    single = lambda flag_rules, fall="off", **kw: make_flag(rules=flag_rules, fall=serve(fall), **kw)

    cases.add("empty-rules-fallthrough-on", "no rules, fallthrough serves on", single([], "on"), context("u1"), served(single([], "on"), "on", "FALLTHROUGH"))
    cases.add("empty-rules-fallthrough-off", "no rules, fallthrough serves off", single([]), context("u1"), served(single([]), "off", "FALLTHROUGH"))
    cases.add("empty-rules-empty-context", "no rules and an empty context key", single([], "on"), context(""), served(single([], "on"), "on", "FALLTHROUGH"))

    disabled = single([rule("r1", [], serve("on"))], "on", enabled=False)
    cases.add("disabled-ignores-rules", "disabled flag serves the off variant even when a rule would match", disabled, context("u1"), served(disabled, "off", "OFF"))
    disabled_rollout = make_flag(enabled=False, fall=rollout(("on", 100000), ("off", 0)))
    cases.add("disabled-no-bucket", "disabled flag with a rollout does not compute a bucket", disabled_rollout, context("u1"), served(disabled_rollout, "off", "OFF"))
    disabled_empty = single([], "on", enabled=False)
    cases.add("disabled-no-rules", "disabled flag without rules", disabled_empty, context("u1"), served(disabled_empty, "off", "OFF"))

    killed = single([rule("r1", [], serve("on"))], "on", kill=True)
    cases.add("kill-switch-wins", "kill switch beats an enabled flag with a matching rule", killed, context("u1"), served(killed, "off", "KILL_SWITCH"))
    killed_disabled = single([], "on", enabled=False, kill=True)
    cases.add("kill-switch-before-disabled", "kill switch is reported before disabled", killed_disabled, context("u1"), served(killed_disabled, "off", "KILL_SWITCH"))
    killed_rollout = make_flag(kill=True, fall=rollout(("on", 100000), ("off", 0)))
    cases.add("kill-switch-no-bucket", "kill switch does not compute a bucket", killed_rollout, context("u1"), served(killed_rollout, "off", "KILL_SWITCH"))

    string_variants = [{"key": "control", "value": "classic"}, {"key": "blue", "value": "ocean blue"}, {"key": "green", "value": "forest green"}]
    string_flag = make_flag(type="string", variants=string_variants, off="control", fall=serve("blue"))
    cases.add("string-fallthrough", "string flag serves a variant value", string_flag, context("u1"), served(string_flag, "blue", "FALLTHROUGH"))
    string_off = make_flag(type="string", variants=string_variants, off="control", enabled=False, fall=serve("blue"))
    cases.add("string-off-variant", "off variant is not the first variant", string_off, context("u1"), served(string_off, "control", "OFF"))
    string_kill = make_flag(type="string", variants=string_variants, off="green", kill=True, fall=serve("blue"))
    cases.add("string-kill-variant", "kill switch serves the configured off variant", string_kill, context("u1"), served(string_kill, "green", "KILL_SWITCH"))

    number_variants = [{"key": "low", "value": 0}, {"key": "mid", "value": 2.5}, {"key": "high", "value": -1000000.125}]
    number_flag = make_flag(type="number", variants=number_variants, off="low", rules=[rule("big-spenders", [attr_cond("spend", "gte", 100)], serve("high"))], fall=serve("mid"))
    cases.add("number-rule", "number flag served by a rule", number_flag, context("u1", spend=150), served(number_flag, "high", "RULE_MATCH", 0, "big-spenders"))
    cases.add("number-fallthrough", "number flag served by the fallthrough", number_flag, context("u1", spend=5), served(number_flag, "mid", "FALLTHROUGH"))

    json_variants = [
        {"key": "empty", "value": None},
        {"key": "list", "value": [1, "two", [3], {"four": 4}]},
        {"key": "object", "value": {"theme": {"name": "dark", "accent": ["#000", "#fff"]}, "limit": 10}},
        {"key": "text", "value": "just a string"},
    ]
    json_flag = make_flag(type="json", variants=json_variants, off="empty", rules=[rule("r1", [attr_cond("tier", "equals", "a")], serve("object")), rule("r2", [attr_cond("tier", "equals", "b")], serve("list"))], fall=serve("text"))
    cases.add("json-object", "json flag serves a nested object", json_flag, context("u1", tier="a"), served(json_flag, "object", "RULE_MATCH", 0, "r1"))
    cases.add("json-array", "json flag serves an array", json_flag, context("u1", tier="b"), served(json_flag, "list", "RULE_MATCH", 1, "r2"))
    cases.add("json-string", "json flag serves a string value", json_flag, context("u1", tier="c"), served(json_flag, "text", "FALLTHROUGH"))
    json_off = make_flag(type="json", variants=json_variants, off="empty", enabled=False, fall=serve("text"))
    cases.add("json-null-off", "json null is a legal off value", json_off, context("u1"), served(json_off, "empty", "OFF"))

    two_match = single([rule("first", [attr_cond("plan", "equals", "pro")], serve("on")), rule("second", [attr_cond("plan", "equals", "pro")], serve("off"))], "off")
    cases.add("first-match-wins", "two rules match, the first wins", two_match, context("u1", plan="pro"), served(two_match, "on", "RULE_MATCH", 0, "first"))
    only_second = single([rule("first", [attr_cond("plan", "equals", "team")], serve("off")), rule("second", [attr_cond("plan", "equals", "pro")], serve("on"))], "off")
    cases.add("second-rule-matches", "only the second rule matches", only_second, context("u1", plan="pro"), served(only_second, "on", "RULE_MATCH", 1, "second"))
    cases.add("no-rule-matches", "no rule matches so the fallthrough applies", only_second, context("u1", plan="free"), served(only_second, "off", "FALLTHROUGH"))

    catch_all = single([rule("everyone", [], serve("on"))], "off")
    cases.add("rule-without-conditions", "a rule without conditions matches every context", catch_all, context("u1"), served(catch_all, "on", "RULE_MATCH", 0, "everyone"))
    cases.add("rule-without-conditions-empty-key", "a rule without conditions matches the empty key", catch_all, context(""), served(catch_all, "on", "RULE_MATCH", 0, "everyone"))
    after_miss = single([rule("vip", [attr_cond("vip", "equals", True)], serve("off")), rule("everyone", [], serve("on"))], "off")
    cases.add("catch-all-after-miss", "a catch-all rule after a rule that does not match", after_miss, context("u1", vip=False), served(after_miss, "on", "RULE_MATCH", 1, "everyone"))
    catch_all_first = single([rule("everyone", [], serve("on")), rule("vip", [attr_cond("vip", "equals", True)], serve("off"))], "off")
    cases.add("catch-all-shadows", "a catch-all rule first shadows the rest", catch_all_first, context("u1", vip=True), served(catch_all_first, "on", "RULE_MATCH", 0, "everyone"))

    and_flag = single([rule("both", [attr_cond("plan", "equals", "pro"), attr_cond("age", "gte", 18)], serve("on"))], "off")
    cases.add("and-all-true", "all conditions of a rule match", and_flag, context("u1", plan="pro", age=30), served(and_flag, "on", "RULE_MATCH", 0, "both"))
    cases.add("and-first-false", "first condition fails", and_flag, context("u1", plan="free", age=30), served(and_flag, "off", "FALLTHROUGH"))
    cases.add("and-second-false", "second condition fails", and_flag, context("u1", plan="pro", age=17), served(and_flag, "off", "FALLTHROUGH"))
    cases.add("and-second-missing", "second attribute missing", and_flag, context("u1", plan="pro"), served(and_flag, "off", "FALLTHROUGH"))

    many = single([rule("r" + str(index), [attr_cond("n", "equals", index)], serve("off")) for index in range(9)] + [rule("r9", [attr_cond("n", "equals", 9)], serve("on"))], "off")
    cases.add("many-rules-last-matches", "the tenth rule is the first to match", many, context("u1", n=9), served(many, "on", "RULE_MATCH", 9, "r9"))
    cases.add("many-rules-middle-matches", "the fourth rule matches", many, context("u1", n=3), served(many, "off", "RULE_MATCH", 3, "r3"))

    same_variant = single([rule("r1", [attr_cond("x", "equals", "y")], serve("on"))], "on")
    cases.add("rule-and-fallthrough-same-variant", "reason tells a rule match from the fallthrough", same_variant, context("u1", x="y"), served(same_variant, "on", "RULE_MATCH", 0, "r1"))
    cases.add("fallthrough-same-variant", "same variant through the fallthrough", same_variant, context("u1", x="z"), served(same_variant, "on", "FALLTHROUGH"))

    reordered = make_flag(variants=[{"key": "off", "value": False}, {"key": "on", "value": True}], fall=serve("on"))
    cases.add("variant-order-irrelevant", "variant list order does not matter for direct serves", reordered, context("u1"), served(reordered, "on", "FALLTHROUGH"))

    empty_attributes = single([rule("r1", [attr_cond("plan", "equals", "pro")], serve("on"))], "off")
    cases.add("empty-attributes", "no attributes at all", empty_attributes, {"key": "u1", "attributes": {}}, served(empty_attributes, "off", "FALLTHROUGH"))
    cases.add("extra-attributes-ignored", "attributes the flag never reads", empty_attributes, context("u1", plan="pro", unrelated="x", other=1), served(empty_attributes, "on", "RULE_MATCH", 0, "r1"))
    return cases


def op_flag(condition):
    return make_flag(rules=[rule("r", [condition], serve("on"))], fall=serve("off"))


def op_case(cases, case_id, description, operator, values, attribute, matched, negate=False):
    flag = op_flag(attr_cond("attr", operator, *values, negate=negate))
    expected = served(flag, "on", "RULE_MATCH", 0, "r") if matched else served(flag, "off", "FALLTHROUGH")
    cases.add(case_id, description, flag, context("u", attr=attribute), expected)


def build_operators():
    cases = Cases("operators")
    add = lambda *args, **kw: op_case(cases, *args, **kw)

    add("equals-string-match", "equal strings", "equals", ["pro"], "pro", True)
    add("equals-string-differs", "different strings", "equals", ["pro"], "team", False)
    add("equals-string-case", "string equality is case sensitive", "equals", ["pro"], "Pro", False)
    add("equals-string-empty", "empty string equals empty string", "equals", [""], "", True)
    add("equals-string-prefix", "a prefix is not equal", "equals", ["pro"], "pro ", False)
    add("equals-number", "equal integers", "equals", [5], 5, True)
    add("equals-number-float-literal", "5.0 and 5 are the same double", "equals", [5.0], 5, True)
    add("equals-number-sum", "0.1 + 0.2 as a literal is not 0.3", "equals", [0.3], 0.30000000000000004, False)
    add("equals-number-exact-double", "the same long literal parses to the same double", "equals", [0.30000000000000004], 0.30000000000000004, True)
    add("equals-number-negative-zero", "negative zero equals zero", "equals", [0], -0.0, True)
    add("equals-number-beyond-2-53", "integers beyond 2^53 collapse to the nearest double", "equals", [9007199254740992], 9007199254740993, True)
    add("equals-number-large-exponent", "large doubles compare exactly", "equals", [1e300], 1e300, True)
    add("equals-boolean-true", "true equals true", "equals", [True], True, True)
    add("equals-boolean-false", "false equals false", "equals", [False], False, True)
    add("equals-boolean-differs", "true does not equal false", "equals", [True], False, False)
    add("equals-string-vs-number", "the string 5 is not the number 5", "equals", [5], "5", False)
    add("equals-number-vs-string", "the number 5 is not the string 5", "equals", ["5"], 5, False)
    add("equals-string-vs-boolean", "the string true is not the boolean true", "equals", [True], "true", False)
    add("equals-number-vs-boolean", "1 is not true", "equals", [True], 1, False)
    add("equals-unicode-match", "polish diacritics", "equals", ["zażółć gęślą jaźń"], "zażółć gęślą jaźń", True)
    add("equals-unicode-astral", "an astral emoji", "equals", ["\U0001f600"], "\U0001f600", True)
    add("equals-no-normalization", "precomposed and decomposed e acute differ", "equals", ["é"], "é", False)
    add("equals-no-case-folding", "sharp s is not SS", "equals", ["STRASSE"], "STRAßE", False)
    add("equals-negate-match", "negate flips a match", "equals", ["pro"], "pro", False, negate=True)
    add("equals-negate-differs", "negate flips a miss", "equals", ["pro"], "team", True, negate=True)
    add("equals-negate-type-mismatch", "a present attribute of another type is applicable, so negate is true", "equals", [5], "5", True, negate=True)
    add("equals-list-contains-value", "a list matches when any element is equal", "equals", ["b"], ["a", "b", "c"], True)
    add("equals-list-lacks-value", "a list without the value", "equals", ["z"], ["a", "b", "c"], False)
    add("equals-list-negate-lacks", "negate on a list without the value", "equals", ["z"], ["a", "b"], True, negate=True)
    add("equals-list-negate-has", "negate on a list with the value", "equals", ["a"], ["a", "b"], False, negate=True)
    add("equals-list-empty", "an empty list is not applicable", "equals", ["a"], [], False)
    add("equals-list-empty-negate", "an empty list stays false with negate", "equals", ["a"], [], False, negate=True)
    add("equals-list-mixed-scalars", "a list of mixed scalars", "equals", [1], ["a", 1, True], True)
    add("equals-list-numbers", "a list of numbers", "equals", [7], [3, 5, 7], True)

    add("in-first", "member at the start", "in", ["a", "b", "c"], "a", True)
    add("in-last", "member at the end", "in", ["a", "b", "c"], "c", True)
    add("in-absent", "not a member", "in", ["a", "b", "c"], "d", False)
    add("in-single-value", "a single value list", "in", ["a"], "a", True)
    add("in-case-sensitive", "membership is case sensitive", "in", ["a", "b"], "A", False)
    add("in-mixed-types-string", "values of several types, string matches", "in", [1, "1", True], "1", True)
    add("in-mixed-types-number", "values of several types, number matches", "in", [1, "1", True], 1, True)
    add("in-mixed-types-boolean", "values of several types, boolean matches", "in", [1, "1", True], True, True)
    add("in-mixed-types-miss", "values of several types, none match", "in", [1, "1", True], 2, False)
    add("in-negate-member", "negate on a member", "in", ["a", "b"], "a", False, negate=True)
    add("in-negate-non-member", "negate on a non-member", "in", ["a", "b"], "z", True, negate=True)
    add("in-list-any-element", "any list element in the values", "in", ["admin", "owner"], ["dev", "admin"], True)
    add("in-list-no-element", "no list element in the values", "in", ["admin", "owner"], ["dev", "qa"], False)
    add("in-countries", "country codes", "in", ["PL", "DE", "CZ", "SK"], "CZ", True)
    add("in-unicode", "unicode members", "in", ["日本", "中国"], "中国", True)

    add("contains-substring", "substring in the middle", "contains", ["lo wo"], "hello world", True)
    add("contains-whole", "the whole string", "contains", ["abc"], "abc", True)
    add("contains-absent", "not a substring", "contains", ["xyz"], "abc", False)
    add("contains-empty-value", "the empty string is contained in anything", "contains", [""], "abc", True)
    add("contains-empty-both", "the empty string contains the empty string", "contains", [""], "", True)
    add("contains-case-sensitive", "substring search is case sensitive", "contains", ["b"], "ABC", False)
    add("contains-longer-value", "a value longer than the attribute", "contains", ["abcd"], "abc", False)
    add("contains-any-of-values", "any of several values", "contains", ["x", "y", "b"], "abc", True)
    add("contains-none-of-values", "none of several values", "contains", ["x", "y", "z"], "abc", False)
    add("contains-non-string-attribute", "a number attribute is not applicable", "contains", ["5"], 5, False)
    add("contains-non-string-attribute-negate", "a number attribute stays false with negate", "contains", ["5"], 5, False, negate=True)
    add("contains-boolean-attribute", "a boolean attribute is not applicable", "contains", ["true"], True, False)
    add("contains-non-string-value", "a number value never satisfies contains", "contains", [5], "a5", False)
    add("contains-non-string-value-negate", "a number value with negate on an applicable attribute", "contains", [5], "a5", True, negate=True)
    add("contains-negate-hit", "negate on a substring hit", "contains", ["b"], "abc", False, negate=True)
    add("contains-negate-miss", "negate on a substring miss", "contains", ["z"], "abc", True, negate=True)
    add("contains-list-element", "any list element contains the value", "contains", ["ef"], ["abc", "def"], True)
    add("contains-list-miss", "no list element contains the value", "contains", ["xy"], ["abc", "def"], False)
    add("contains-list-skips-numbers", "numbers in a list are skipped", "contains", ["1"], [1, "x"], False)
    add("contains-unicode", "unicode substring", "contains", ["語テ"], "日本語テキスト", True)
    add("contains-emoji", "an emoji inside text", "contains", ["\U0001f600"], "smile \U0001f600 please", True)
    add("contains-lone-high-surrogate", "substring search works on UTF-16 code units", "contains", ["\ud83d"], "\U0001f600", True)
    add("contains-lone-low-surrogate", "the low half of a pair is a code unit too", "contains", ["\ude00"], "\U0001f600", True)
    add("contains-no-normalization", "a precomposed letter is not found in the decomposed form", "contains", ["é"], "café", False)

    add("starts-with-prefix", "a real prefix", "startsWith", ["prefix"], "prefix-value", True)
    add("starts-with-whole", "the whole string", "startsWith", ["abc"], "abc", True)
    add("starts-with-middle", "a substring that is not a prefix", "startsWith", ["fix"], "prefix-value", False)
    add("starts-with-empty", "the empty prefix", "startsWith", [""], "abc", True)
    add("starts-with-empty-both", "empty prefix of the empty string", "startsWith", [""], "", True)
    add("starts-with-longer", "a prefix longer than the string", "startsWith", ["abcd"], "abc", False)
    add("starts-with-case", "prefix matching is case sensitive", "startsWith", ["PRE"], "prefix", False)
    add("starts-with-any", "any of several prefixes", "startsWith", ["x", "pre"], "prefix", True)
    add("starts-with-number-attribute", "a number attribute is not applicable", "startsWith", ["1"], 123, False)
    add("starts-with-number-attribute-negate", "a number attribute stays false with negate", "startsWith", ["1"], 123, False, negate=True)
    add("starts-with-negate-hit", "negate on a hit", "startsWith", ["pre"], "prefix", False, negate=True)
    add("starts-with-negate-miss", "negate on a miss", "startsWith", ["zzz"], "prefix", True, negate=True)
    add("starts-with-list", "any list element has the prefix", "startsWith", ["de"], ["abc", "def"], True)
    add("starts-with-unicode", "unicode prefix", "startsWith", ["żół"], "żółć", True)
    add("starts-with-split-pair", "a prefix that ends inside a surrogate pair", "startsWith", ["\ud83d"], "\U0001f600x", True)
    add("starts-with-email-domain", "typical use, an email suffix check done as a prefix of the reversed domain", "startsWith", ["@example."], "@example.com", True)

    add("lt-below", "5 is below 10", "lt", [10], 5, True)
    add("lt-equal", "10 is not below 10", "lt", [10], 10, False)
    add("lt-above", "15 is not below 10", "lt", [10], 15, False)
    add("lt-negative", "-5 is below -1", "lt", [-1], -5, True)
    add("lt-float", "0.1 is below 0.2", "lt", [0.2], 0.1, True)
    add("lt-tiny", "1e-7 is below 1e-6", "lt", [1e-6], 1e-7, True)
    add("lt-string-attribute", "a string attribute is not applicable", "lt", [10], "5", False)
    add("lt-string-attribute-negate", "a string attribute stays false with negate", "lt", [10], "5", False, negate=True)
    add("lt-boolean-attribute", "a boolean attribute is not applicable", "lt", [10], True, False)
    add("lt-string-value", "a string value never satisfies lt", "lt", ["10"], 5, False)
    add("lt-string-value-negate", "a string value with negate on an applicable attribute", "lt", ["10"], 5, True, negate=True)
    add("lt-negate-below", "negate on a hit", "lt", [10], 5, False, negate=True)
    add("lt-negate-above", "negate on a miss", "lt", [10], 15, True, negate=True)
    add("lt-list-any", "any list element below the value", "lt", [10], [50, 5], True)
    add("lt-list-none", "no list element below the value", "lt", [10], [50, 60], False)
    add("lt-list-skips-strings", "strings in a list are skipped", "lt", [10], ["1", 50], False)
    add("lte-below", "5 is at most 10", "lte", [10], 5, True)
    add("lte-equal", "10 is at most 10", "lte", [10], 10, True)
    add("lte-above", "10.000001 is not at most 10", "lte", [10], 10.000001, False)
    add("lte-negate-equal", "negate on equality", "lte", [10], 10, False, negate=True)
    add("gt-above", "15 is above 10", "gt", [10], 15, True)
    add("gt-equal", "10 is not above 10", "gt", [10], 10, False)
    add("gt-below", "5 is not above 10", "gt", [10], 5, False)
    add("gt-huge", "1e21 is above 1e20", "gt", [1e20], 1e21, True)
    add("gt-negative", "-1 is above -5", "gt", [-5], -1, True)
    add("gt-negate", "negate on a hit", "gt", [10], 15, False, negate=True)
    add("gt-string-attribute", "a string attribute is not applicable", "gt", [10], "15", False)
    add("gte-above", "15 is at least 10", "gte", [10], 15, True)
    add("gte-equal", "10 is at least 10", "gte", [10], 10, True)
    add("gte-below", "9.999999 is not at least 10", "gte", [10], 9.999999, False)
    add("gte-zero", "0 is at least 0", "gte", [0], 0, True)
    add("gte-negative-zero", "-0 is at least 0", "gte", [0], -0.0, True)
    add("gte-negate-below", "negate on a miss", "gte", [10], 5, True, negate=True)
    add("gte-list", "a list with an element at the bound", "gte", [10], [1, 10], True)
    add("gte-age-gate", "typical age gate", "gte", [18], 17, False)
    return cases


def build_missing():
    cases = Cases("missing")
    operators = [
        ("equals", ["x"]),
        ("in", ["x", "y"]),
        ("contains", ["x"]),
        ("startsWith", ["x"]),
        ("lt", [1]),
        ("lte", [1]),
        ("gt", [1]),
        ("gte", [1]),
        ("semverEquals", ["1.0.0"]),
        ("semverLt", ["1.0.0"]),
        ("semverLte", ["1.0.0"]),
        ("semverGt", ["1.0.0"]),
        ("semverGte", ["1.0.0"]),
    ]
    for operator, values in operators:
        op_case(cases, operator + "-absent", "absent attribute never matches " + operator, operator, values, MISSING, False)
        op_case(cases, operator + "-absent-negate", "absent attribute with negate never matches " + operator, operator, values, MISSING, False, negate=True)
    op_case(cases, "null-attribute", "null is the same as absent", "equals", ["x"], None, False)
    op_case(cases, "null-attribute-negate", "null with negate stays false", "equals", ["x"], None, False, negate=True)
    op_case(cases, "object-attribute", "an object value is treated as missing", "equals", ["x"], {"a": 1}, False)
    op_case(cases, "object-attribute-negate", "an object value with negate is still false", "equals", ["x"], {"a": 1}, False, negate=True)
    op_case(cases, "list-of-objects", "a list holding an object is treated as missing", "equals", ["x"], ["x", {"a": 1}], False)
    op_case(cases, "list-of-lists", "a list holding a list is treated as missing", "equals", [1], [[1]], False)
    op_case(cases, "list-with-null", "a list holding null is treated as missing", "equals", ["x"], ["x", None], False)

    flag = make_flag(rules=[rule("r", [attr_cond("plan", "equals", "pro", negate=True)], serve("on"))], fall=serve("off"))
    cases.add("negate-does-not-match-missing", "not-pro does not match a context without plan", flag, {"key": "u", "attributes": {}}, served(flag, "off", "FALLTHROUGH"))
    cases.add("negate-matches-other-value", "not-pro matches a context with another plan", flag, context("u", plan="free"), served(flag, "on", "RULE_MATCH", 0, "r"))
    cases.add("negate-wrong-attribute-name", "attribute names are case sensitive", flag, context("u", Plan="free"), served(flag, "off", "FALLTHROUGH"))

    proto_flag = make_flag(rules=[rule("r", [attr_cond("__proto__", "equals", "x")], serve("on"))], fall=serve("off"))
    cases.add("attribute-named-proto", "an attribute named __proto__ is an ordinary attribute", proto_flag, context("u", **{"__proto__": "x"}), served(proto_flag, "on", "RULE_MATCH", 0, "r"))
    cases.add("attribute-named-proto-absent", "and an absent one is missing", proto_flag, {"key": "u", "attributes": {}}, served(proto_flag, "off", "FALLTHROUGH"))
    inherited_flag = make_flag(rules=[rule("r", [attr_cond("constructor", "contains", "Object")], serve("on"))], fall=serve("off"))
    cases.add("attribute-named-constructor-absent", "names of built in object members are missing unless supplied", inherited_flag, {"key": "u", "attributes": {}}, served(inherited_flag, "off", "FALLTHROUGH"))
    cases.add("attribute-named-constructor-present", "and ordinary when supplied", inherited_flag, context("u", constructor="Object"), served(inherited_flag, "on", "RULE_MATCH", 0, "r"))

    chain = make_flag(rules=[rule("a", [attr_cond("a", "equals", 1)], serve("off")), rule("b", [attr_cond("b", "equals", 1)], serve("on"))], fall=serve("off"))
    cases.add("later-rule-after-missing", "a rule with a missing attribute is skipped, the next one can match", chain, context("u", b=1), served(chain, "on", "RULE_MATCH", 1, "b"))
    return cases


SEMVER_CASES = [
    ("equals-same", "semverEquals", "1.2.3", "1.2.3", True),
    ("equals-build-ignored", "semverEquals", "1.2.3", "1.2.3+build.7", True),
    ("equals-both-build", "semverEquals", "1.2.3+a", "1.2.3+b", True),
    ("equals-prerelease-differs", "semverEquals", "1.2.3", "1.2.3-rc.1", False),
    ("equals-prerelease-same", "semverEquals", "1.2.3-rc.1", "1.2.3-rc.1+x", True),
    ("lt-patch", "semverLt", "1.2.4", "1.2.3", True),
    ("lt-minor", "semverLt", "1.3.0", "1.2.9", True),
    ("lt-major", "semverLt", "2.0.0", "1.99.99", True),
    ("lt-not-below", "semverLt", "1.2.3", "1.2.4", False),
    ("lt-equal", "semverLt", "1.2.3", "1.2.3", False),
    ("lt-numeric-not-lexical", "semverLt", "1.10.0", "1.9.0", True),
    ("lt-numeric-not-lexical-reversed", "semverLt", "1.9.0", "1.10.0", False),
    ("lt-prerelease-below-release", "semverLt", "2.0.0", "2.0.0-rc.1", True),
    ("lt-release-not-below-prerelease", "semverLt", "2.0.0-rc.1", "2.0.0", False),
    ("lt-numeric-identifiers", "semverLt", "1.0.0-beta.11", "1.0.0-beta.2", True),
    ("lt-numeric-below-alpha", "semverLt", "1.0.0-alpha", "1.0.0-1", True),
    ("lt-alpha-order", "semverLt", "1.0.0-beta", "1.0.0-alpha", True),
    ("lt-shorter-is-lower", "semverLt", "1.0.0-alpha.1", "1.0.0-alpha", True),
    ("lt-uppercase-below-lowercase", "semverLt", "1.0.0-a", "1.0.0-A", True),
    ("lt-huge-major", "semverLt", "99999999999999999999.0.0", "99999999999999999998.0.0", True),
    ("lt-huge-prerelease-number", "semverLt", "1.0.0-alpha.18446744073709551616", "1.0.0-alpha.18446744073709551615", True),
    ("lte-equal", "semverLte", "1.2.3", "1.2.3", True),
    ("lte-below", "semverLte", "1.2.4", "1.2.3", True),
    ("lte-above", "semverLte", "1.2.2", "1.2.3", False),
    ("lte-build-ignored", "semverLte", "1.2.3+z", "1.2.3+a", True),
    ("gt-patch", "semverGt", "1.2.3", "1.2.4", True),
    ("gt-equal", "semverGt", "1.2.3", "1.2.3", False),
    ("gt-prerelease-above-lower-prerelease", "semverGt", "1.0.0-beta.2", "1.0.0-rc.1", True),
    ("gt-release-above-prerelease", "semverGt", "1.0.0-rc.9", "1.0.0", True),
    ("gt-prerelease-core-higher", "semverGt", "1.0.0", "1.0.1-alpha", True),
    ("gte-equal", "semverGte", "2.0.0", "2.0.0", True),
    ("gte-above", "semverGte", "2.0.0", "2.0.1", True),
    ("gte-below", "semverGte", "2.0.1", "2.0.0", False),
    ("gte-prerelease-of-same-core", "semverGte", "2.0.0", "2.0.0-rc.1", False),
    ("gte-build-metadata-on-bound", "semverGte", "2.0.0+meta", "2.0.0", True),
    ("gte-zero", "semverGte", "0.0.0", "0.0.0", True),
]


def build_semver_conditions():
    cases = Cases("semver")
    for case_id, operator, bound, attribute, matched in SEMVER_CASES:
        parsed_bound = oracle.parse_semver(bound)
        parsed_attribute = oracle.parse_semver(attribute)
        outcome = oracle.compare_semver(parsed_attribute, parsed_bound)
        truth = {
            "semverEquals": outcome == 0,
            "semverLt": outcome < 0,
            "semverLte": outcome <= 0,
            "semverGt": outcome > 0,
            "semverGte": outcome >= 0,
        }[operator]
        assert truth == matched, case_id
        op_case(cases, case_id, "attribute " + attribute + " against " + operator + " " + bound, operator, [bound], attribute, matched)

    op_case(cases, "invalid-attribute-leading-v", "a leading v is not valid", "semverGte", ["1.0.0"], "v2.0.0", False)
    op_case(cases, "invalid-attribute-negate", "an invalid attribute stays false with negate", "semverGte", ["1.0.0"], "v2.0.0", False, negate=True)
    op_case(cases, "invalid-attribute-two-parts", "two parts are not valid", "semverGt", ["1.0.0"], "2.0", False)
    op_case(cases, "invalid-attribute-leading-zero", "a leading zero is not valid", "semverEquals", ["1.2.3"], "1.02.3", False)
    op_case(cases, "invalid-attribute-text", "plain text is not valid", "semverLt", ["1.0.0"], "latest", False)
    op_case(cases, "invalid-attribute-empty", "the empty string is not valid", "semverLt", ["1.0.0"], "", False)
    op_case(cases, "invalid-attribute-space", "a trailing space is not valid", "semverEquals", ["1.2.3"], "1.2.3 ", False)
    op_case(cases, "invalid-value", "an invalid bound never matches", "semverGte", ["not-a-version"], "2.0.0", False)
    op_case(cases, "invalid-value-negate", "an invalid bound with negate on a valid attribute", "semverGte", ["not-a-version"], "2.0.0", True, negate=True)
    op_case(cases, "number-attribute", "a number is not a version", "semverGte", ["1.0.0"], 2, False)
    op_case(cases, "number-attribute-negate", "a number attribute stays false with negate", "semverGte", ["1.0.0"], 2, False, negate=True)
    op_case(cases, "boolean-attribute", "a boolean is not a version", "semverEquals", ["1.0.0"], True, False)
    op_case(cases, "non-string-value", "a number bound never matches", "semverGte", [1], "1.0.0", False)
    op_case(cases, "negate-hit", "negate on a hit", "semverGte", ["1.0.0"], "2.0.0", False, negate=True)
    op_case(cases, "negate-miss", "negate on a miss", "semverGte", ["3.0.0"], "2.0.0", True, negate=True)
    op_case(cases, "list-any-element", "any version in a list", "semverGte", ["2.0.0"], ["1.0.0", "2.5.0"], True)
    op_case(cases, "list-no-element", "no version in a list", "semverGte", ["2.0.0"], ["1.0.0", "1.5.0"], False)
    op_case(cases, "list-skips-invalid", "invalid entries in a list are skipped", "semverGte", ["2.0.0"], ["junk", "2.0.0"], True)
    op_case(cases, "list-only-invalid", "a list with only invalid entries is not applicable", "semverGte", ["2.0.0"], ["junk", "v3"], False)
    op_case(cases, "list-only-invalid-negate", "a list with only invalid entries stays false with negate", "semverGte", ["2.0.0"], ["junk", "v3"], False, negate=True)

    app_versions = make_flag(
        rules=[
            rule("legacy", [attr_cond("appVersion", "semverLt", "2.0.0")], serve("off")),
            rule("rc-testers", [attr_cond("appVersion", "semverGte", "2.1.0-rc.1"), attr_cond("appVersion", "semverLt", "2.1.0")], serve("on")),
            rule("current", [attr_cond("appVersion", "semverGte", "2.1.0")], serve("on")),
        ],
        fall=serve("off"),
    )
    cases.add("scenario-legacy", "an old client is served by the first rule", app_versions, context("u", appVersion="1.9.9"), served(app_versions, "off", "RULE_MATCH", 0, "legacy"))
    cases.add("scenario-rc", "a release candidate matches the second rule", app_versions, context("u", appVersion="2.1.0-rc.3"), served(app_versions, "on", "RULE_MATCH", 1, "rc-testers"))
    cases.add("scenario-current", "a current client matches the third rule", app_versions, context("u", appVersion="2.1.0+build.5"), served(app_versions, "on", "RULE_MATCH", 2, "current"))
    cases.add("scenario-gap", "a 2.0.x client matches nothing", app_versions, context("u", appVersion="2.0.5"), served(app_versions, "off", "FALLTHROUGH"))
    cases.add("scenario-alpha-before-rc", "an alpha is below the first release candidate", app_versions, context("u", appVersion="2.1.0-alpha"), served(app_versions, "off", "FALLTHROUGH"))
    return cases


def segment_flag(*conditions, fall="off"):
    return make_flag(rules=[rule("r", list(conditions), serve("on"))], fall=serve(fall))


def build_segments():
    cases = Cases("segments")

    def member(case_id, description, flag, ctx, segments, is_member):
        expected = served(flag, "on", "RULE_MATCH", 0, "r") if is_member else served(flag, "off", "FALLTHROUGH")
        cases.add(case_id, description, flag, ctx, expected, segments)

    beta = segment("beta", included=["u1", "u2"])
    flag = segment_flag(seg_cond("beta"))
    member("included-key", "an included key is a member", flag, context("u1"), [beta], True)
    member("not-included-key", "a key that is not included is not a member", flag, context("u3"), [beta], False)
    member("included-case-sensitive", "keys are case sensitive", flag, context("U1"), [beta], False)
    member("included-empty-key", "the empty key can be included", flag, context(""), [segment("beta", included=[""])], True)
    member("included-unicode-key", "unicode keys can be included", flag, context("użółć"), [segment("beta", included=["użółć"])], True)
    member("included-large-list", "a long included list", flag, context("user-77"), [segment("beta", included=["user-" + str(index) for index in range(150)])], True)
    member("included-large-list-miss", "a long included list without the key", flag, context("user-150"), [segment("beta", included=["user-" + str(index) for index in range(150)])], False)

    both = segment("beta", included=["u1"], excluded=["u1"])
    member("excluded-beats-included", "excluded wins over included", flag, context("u1"), [both], False)
    with_rules = segment("beta", excluded=["u1"], rules=[[attr_cond("country", "equals", "PL")]])
    member("excluded-beats-rule", "excluded wins over a matching group", flag, context("u1", country="PL"), [with_rules], False)
    member("rule-member-not-excluded", "another key with the same attributes is a member", flag, context("u2", country="PL"), [with_rules], True)

    by_country = segment("poland", rules=[[attr_cond("country", "equals", "PL")]])
    member("group-matches", "a group that matches makes a member", segment_flag(seg_cond("poland")), context("u1", country="PL"), [by_country], True)
    member("group-misses", "a group that does not match", segment_flag(seg_cond("poland")), context("u1", country="DE"), [by_country], False)
    member("group-missing-attribute", "a group with a missing attribute does not match", segment_flag(seg_cond("poland")), context("u1"), [by_country], False)

    anded = segment("adults-pl", rules=[[attr_cond("country", "equals", "PL"), attr_cond("age", "gte", 18)]])
    member("group-and-both", "all conditions of a group", segment_flag(seg_cond("adults-pl")), context("u1", country="PL", age=20), [anded], True)
    member("group-and-first-fails", "first condition of a group fails", segment_flag(seg_cond("adults-pl")), context("u1", country="DE", age=20), [anded], False)
    member("group-and-second-fails", "second condition of a group fails", segment_flag(seg_cond("adults-pl")), context("u1", country="PL", age=10), [anded], False)

    ored = segment("eu-or-beta", rules=[[attr_cond("country", "equals", "PL")], [attr_cond("beta", "equals", True)]])
    member("groups-or-first", "the first group matches", segment_flag(seg_cond("eu-or-beta")), context("u1", country="PL"), [ored], True)
    member("groups-or-second", "the second group matches", segment_flag(seg_cond("eu-or-beta")), context("u1", beta=True), [ored], True)
    member("groups-or-none", "no group matches", segment_flag(seg_cond("eu-or-beta")), context("u1", country="US", beta=False), [ored], False)

    everyone = segment("everyone", rules=[[]])
    member("empty-group-matches-all", "a group without conditions matches every context", segment_flag(seg_cond("everyone")), context("anyone"), [everyone], True)
    nobody = segment("nobody")
    member("no-groups-no-members", "a segment without groups and included keys is empty", segment_flag(seg_cond("nobody")), context("u1"), [nobody], False)

    negated = segment_flag(seg_cond("beta", negate=True))
    member("negate-member", "negate is false for a member", negated, context("u1"), [beta], False)
    member("negate-non-member", "negate is true for a non-member", negated, context("u9"), [beta], True)
    member("negate-excluded", "negate is true for an excluded key", negated, context("u1"), [both], True)

    member("unknown-segment", "an unknown segment never matches", segment_flag(seg_cond("ghost")), context("u1"), [beta], False)
    member("unknown-segment-negate", "an unknown segment with negate never matches", segment_flag(seg_cond("ghost", negate=True)), context("u1"), [beta], False)
    member("no-segments-at-all", "no segments in the snapshot", segment_flag(seg_cond("beta")), context("u1"), [], False)

    two = segment_flag(seg_cond("beta"), seg_cond("poland"))
    member("two-segments-both", "a rule needs both segments", two, context("u1", country="PL"), [beta, by_country], True)
    member("two-segments-one", "only one of two segments", two, context("u1", country="DE"), [beta, by_country], False)
    member("segments-order-in-snapshot", "snapshot order does not matter", two, context("u1", country="PL"), [by_country, beta], True)

    mixed = segment_flag(seg_cond("beta"), attr_cond("plan", "equals", "pro"))
    member("segment-and-attribute", "a segment and an attribute condition together", mixed, context("u1", plan="pro"), [beta], True)
    member("segment-and-attribute-fails", "the attribute condition fails", mixed, context("u1", plan="free"), [beta], False)
    member("segment-negate-and-attribute", "not in the segment but on the plan", segment_flag(seg_cond("beta", negate=True), attr_cond("plan", "equals", "pro")), context("u7", plan="pro"), [beta], True)

    semver_segment = segment("modern", rules=[[attr_cond("appVersion", "semverGte", "3.0.0")]])
    member("segment-with-semver", "a segment group using a semver operator", segment_flag(seg_cond("modern")), context("u1", appVersion="3.1.0"), [semver_segment], True)
    member("segment-with-semver-prerelease", "a pre-release is below the bound", segment_flag(seg_cond("modern")), context("u1", appVersion="3.0.0-beta.1"), [semver_segment], False)

    negated_group = segment("not-free", rules=[[attr_cond("plan", "equals", "free", negate=True)]])
    member("group-with-negate", "negate inside a segment group", segment_flag(seg_cond("not-free")), context("u1", plan="pro"), [negated_group], True)
    member("group-with-negate-missing", "negate inside a group still needs the attribute", segment_flag(seg_cond("not-free")), context("u1"), [negated_group], False)

    second = make_flag(rules=[rule("a", [seg_cond("beta")], serve("off")), rule("b", [seg_cond("poland")], serve("on"))], fall=serve("off"))
    cases.add("second-rule-segment", "the second rule matches through its own segment", second, context("u2", country="PL"), served(second, "on", "RULE_MATCH", 1, "b"), [segment("beta", included=["u1"]), by_country])

    unused = make_flag(fall=serve("on"))
    cases.add("unused-segments", "segments that no rule uses change nothing", unused, context("u1"), served(unused, "on", "FALLTHROUGH"), [beta, by_country])

    rolled = make_flag(rules=[rule("r", [seg_cond("beta")], rollout(("on", 30000), ("off", 70000)))], fall=serve("off"))
    key = find_key(rolled["key"], rolled["salt"], 0, 29999, "u-")
    beta_for_key = segment("beta", included=[key])
    rollout_case(cases, "segment-then-rollout", "a segment rule that serves a rollout", rolled, context(key), [beta_for_key], matched_rule=0)
    return cases


def build_rollouts():
    cases = Cases("rollouts")
    salt = "5e1d"
    flag_key = "checkout"

    def boolean_flag(on_weight, key=flag_key, salt_value=salt, **kw):
        return make_flag(key=key, salt=salt_value, fall=rollout(("on", on_weight), ("off", 100000 - on_weight)), **kw)

    quarter = boolean_flag(25000)
    for wanted, variant in [(0, "on"), (1, "on"), (24999, "on"), (25000, "off"), (25001, "off"), (50000, "off"), (99998, "off"), (99999, "off")]:
        key = find_exact(flag_key, salt, wanted)
        assert oracle.bucket(flag_key, salt, key) == wanted
        assert pick(quarter["fallthrough"]["rollout"], wanted) == variant
        cases.add("quarter-bucket-" + str(wanted), "25 percent rollout, context at bucket " + str(wanted), quarter, context(key), served(quarter, variant, "FALLTHROUGH", bucket=wanted))

    everyone = boolean_flag(100000)
    for wanted in (0, 99999):
        key = find_exact(flag_key, salt, wanted)
        cases.add("all-on-bucket-" + str(wanted), "100 percent rollout covers bucket " + str(wanted), everyone, context(key), served(everyone, "on", "FALLTHROUGH", bucket=wanted))
    nobody = boolean_flag(0)
    for wanted in (0, 99999):
        key = find_exact(flag_key, salt, wanted)
        cases.add("all-off-bucket-" + str(wanted), "0 percent rollout excludes bucket " + str(wanted), nobody, context(key), served(nobody, "off", "FALLTHROUGH", bucket=wanted))

    one_thousandth = boolean_flag(1)
    for wanted, variant in [(0, "on"), (1, "off")]:
        key = find_exact(flag_key, salt, wanted)
        cases.add("one-unit-bucket-" + str(wanted), "a 0.001 percent rollout, bucket " + str(wanted), one_thousandth, context(key), served(one_thousandth, variant, "FALLTHROUGH", bucket=wanted))
    almost_all = boolean_flag(99999)
    for wanted, variant in [(99998, "on"), (99999, "off")]:
        key = find_exact(flag_key, salt, wanted)
        cases.add("almost-all-bucket-" + str(wanted), "a 99.999 percent rollout, bucket " + str(wanted), almost_all, context(key), served(almost_all, variant, "FALLTHROUGH", bucket=wanted))

    three = make_flag(key="palette", type="string", salt="c0ffee", variants=[{"key": "a", "value": "amber"}, {"key": "b", "value": "birch"}, {"key": "c", "value": "cobalt"}], off="a", fall=rollout(("a", 33333), ("b", 33333), ("c", 33334)))
    for wanted, variant in [(0, "a"), (33332, "a"), (33333, "b"), (66665, "b"), (66666, "c"), (99999, "c")]:
        key = find_exact("palette", "c0ffee", wanted)
        assert pick(three["fallthrough"]["rollout"], wanted) == variant
        cases.add("thirds-bucket-" + str(wanted), "three variants, bucket " + str(wanted), three, context(key), served(three, variant, "FALLTHROUGH", bucket=wanted))

    gap = make_flag(key="gap", type="string", salt="0f0f", variants=[{"key": "a", "value": "one"}, {"key": "b", "value": "two"}, {"key": "c", "value": "three"}], off="a", fall=rollout(("a", 50000), ("b", 0), ("c", 50000)))
    for wanted, variant in [(49999, "a"), (50000, "c")]:
        key = find_exact("gap", "0f0f", wanted)
        assert pick(gap["fallthrough"]["rollout"], wanted) == variant
        cases.add("zero-weight-middle-" + str(wanted), "a zero weight in the middle never wins, bucket " + str(wanted), gap, context(key), served(gap, variant, "FALLTHROUGH", bucket=wanted))
    zero_first = make_flag(key="zero-first", type="string", salt="0f0f", variants=[{"key": "a", "value": "one"}, {"key": "b", "value": "two"}, {"key": "c", "value": "three"}], off="a", fall=rollout(("a", 0), ("b", 60000), ("c", 40000)))
    for wanted, variant in [(0, "b"), (59999, "b"), (60000, "c")]:
        key = find_exact("zero-first", "0f0f", wanted)
        assert pick(zero_first["fallthrough"]["rollout"], wanted) == variant
        cases.add("zero-weight-first-" + str(wanted), "a zero weight first never wins, bucket " + str(wanted), zero_first, context(key), served(zero_first, variant, "FALLTHROUGH", bucket=wanted))
    single_entry = make_flag(key="solo", salt="1", fall=rollout(("on", 100000)))
    cases.add("single-entry-rollout", "a rollout with one entry", single_entry, context("anyone"), served(single_entry, "on", "FALLTHROUGH", bucket=oracle.bucket("solo", "1", "anyone")))
    trailing_zero = make_flag(key="trailing", salt="2", fall=rollout(("on", 100000), ("off", 0)))
    cases.add("trailing-zero-weight", "a trailing zero weight", trailing_zero, context("anyone"), served(trailing_zero, "on", "FALLTHROUGH", bucket=oracle.bucket("trailing", "2", "anyone")))

    for index in range(1, 25):
        key = "user-" + str(index)
        rollout_case(cases, "half-user-" + str(index), "50 percent rollout for " + key, boolean_flag(50000), context(key))

    rule_flag = make_flag(key="promo", salt="abc123", rules=[rule("pro-half", [attr_cond("plan", "equals", "pro")], rollout(("on", 50000), ("off", 50000)))], fall=serve("off"))
    for index in range(6):
        key = "member-" + str(index)
        rollout_case(cases, "rule-rollout-" + str(index), "rollout inside a matching rule, context " + key, rule_flag, context(key, plan="pro"), matched_rule=0)
    cases.add("rule-rollout-miss", "the rollout is skipped when the rule does not match", rule_flag, context("member-0", plan="free"), served(rule_flag, "off", "FALLTHROUGH"))

    both = make_flag(key="both", salt="beef", rules=[rule("r", [attr_cond("plan", "equals", "pro")], rollout(("on", 20000), ("off", 80000)))], fall=rollout(("on", 70000), ("off", 30000)))
    key = find_key("both", "beef", 25000, 69999, "z-")
    rollout_case(cases, "rule-and-fallthrough-rollouts-hit", "the fallthrough rollout serves on for a non-matching context", both, context(key, plan="free"))
    rollout_case(cases, "rule-and-fallthrough-rollouts-rule", "the rule rollout serves off for the same bucket", both, context(key, plan="pro"), matched_rule=0)

    key = find_exact("checkout", "5e1d", 12345)
    for other_salt in ("5e1d", "5e1e", "deadbeef", "0"):
        flag = boolean_flag(50000, salt_value=other_salt)
        rollout_case(cases, "salt-" + other_salt, "the same key under salt " + other_salt, flag, context(key))
    for other_flag in ("checkout", "checkout-v2", "search", "a"):
        flag = boolean_flag(50000, key=other_flag)
        rollout_case(cases, "flag-key-" + other_flag, "the same key under flag " + other_flag, flag, context(key))

    rollout_case(cases, "context-key-with-dots", "dots in the context key do not change the parts", boolean_flag(50000), context("a.b.c"))
    rollout_case(cases, "context-key-looks-like-salt", "a context key that looks like a salt", boolean_flag(50000), context("5e1d.checkout"))

    numbers = make_flag(key="limits", type="number", salt="77", variants=[{"key": "small", "value": 10}, {"key": "large", "value": 1000.5}], off="small", fall=rollout(("small", 80000), ("large", 20000)))
    for index in range(4):
        rollout_case(cases, "number-variants-" + str(index), "a rollout over number variants", numbers, context("account-" + str(index)))
    objects = make_flag(key="layout", type="json", salt="88", variants=[{"key": "grid", "value": {"cols": 3}}, {"key": "list", "value": {"cols": 1, "dense": True}}], off="list", fall=rollout(("grid", 50000), ("list", 50000)))
    for index in range(4):
        rollout_case(cases, "json-variants-" + str(index), "a rollout over json variants", objects, context("account-" + str(index)))
    return cases


UNICODE_KEYS = [
    ("empty", ""),
    ("space", " "),
    ("ascii", "user-1"),
    ("polish", "zażółć gęślą jaźń"),
    ("cjk", "日本語のユーザー"),
    ("emoji", "\U0001f600"),
    ("emoji-sequence", "\U0001f468‍\U0001f469‍\U0001f467‍\U0001f466"),
    ("flag-pair", "\U0001f1f5\U0001f1f1"),
    ("precomposed", "café"),
    ("decomposed", "café"),
    ("lone-high", "\ud800"),
    ("lone-low", "\udc00"),
    ("reversed-pair", "\ude00\ud83d"),
    ("lone-middle", "a\ud800b"),
    ("lone-high-then-char", "\ud83dx"),
    ("replacement-char", "�"),
    ("nul", "\u0000"),
    ("nul-inside", "a\u0000b"),
    ("dots", "user.with.dots"),
    ("trailing-space", "user "),
    ("leading-space", " user"),
    ("rtl", "مرحبا"),
    ("zero-width-space", "​"),
    ("bom", "﻿"),
    ("noncharacter", "￿"),
    ("max-code-point", "\U0010ffff"),
    ("two-byte-boundary", "߿"),
    ("three-byte-boundary", "ࠀ"),
    ("surrogate-boundary-before", "퟿"),
    ("surrogate-boundary-after", ""),
    ("four-byte-boundary", "\U00010000"),
    ("sharp-s", "straße"),
    ("dotted-capital-i", "İstanbul"),
    ("tab-and-newline", "a\tb\nc"),
    ("quote-and-backslash", "a\"b\\c"),
    ("long", "k" * 5000),
    ("long-unicode", "ż" * 2500),
]


def build_unicode():
    cases = Cases("unicode")
    flag = make_flag(key="uni", salt="face", fall=rollout(("on", 50000), ("off", 50000)))
    for name, key in UNICODE_KEYS:
        rollout_case(cases, name, "bucketing the context key " + name, flag, context(key))
    return cases


BUCKET_INPUTS = [
    ("plain", "new-checkout", "9f2c41", "user-42"),
    ("short-key", "a", "0", "b"),
    ("empty-context-key", "flag", "a1b2c3", ""),
    ("dots-in-context-key", "flag", "a1b2c3", "x.y.z"),
    ("long-salt", "flag", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", "u"),
    ("dash-and-underscore-flag", "dark_mode-v2", "ff", "u"),
    ("digits-flag", "2026-sale", "ab12", "u-1"),
    ("lone-surrogate", "flag", "a1b2c3", "\ud800"),
    ("replacement-char", "flag", "a1b2c3", "�"),
    ("emoji", "flag", "a1b2c3", "\U0001f600"),
    ("cjk", "flag", "a1b2c3", "日本語"),
    ("decomposed", "flag", "a1b2c3", "café"),
    ("precomposed", "flag", "a1b2c3", "café"),
    ("nul", "flag", "a1b2c3", "\u0000"),
    ("long-key", "flag", "a1b2c3", "x" * 1000),
    ("uuid", "flag", "a1b2c3", "123e4567-e89b-12d3-a456-426614174000"),
    ("email", "flag", "a1b2c3", "someone@example.org"),
    ("numeric", "flag", "a1b2c3", "1234567890"),
]


def build_bucket_document():
    cases = []
    inputs = list(BUCKET_INPUTS)
    for length in range(12):
        inputs.append(("input-length-" + str(length), "f", "0", "k" * length))
    inputs.append(("bucket-zero", "flag", "a1b2c3", find_exact("flag", "a1b2c3", 0)))
    inputs.append(("bucket-max", "flag", "a1b2c3", find_exact("flag", "a1b2c3", 99999)))
    for name, flag_key, salt, context_key in inputs:
        text = oracle.bucket_input(flag_key, salt, context_key)
        value = oracle.hash_text(text)
        cases.append({"id": "bucket-" + name, "flagKey": flag_key, "salt": salt, "contextKey": context_key, "hash": "0x%08X" % value, "bucket": value % oracle.BUCKET_SPACE})
    return cases


def build_murmur_document():
    published = []
    for index, (data, seed, expected) in enumerate(oracle.PUBLISHED):
        published.append({"id": "published-" + str(index), "hex": data.hex(), "seed": seed, "hash": "0x%08X" % expected})
    extra = []
    for index, (data, seed) in enumerate([(bytes(range(length)), 0) for length in (1, 2, 3, 4, 5, 6, 7, 8, 9, 15, 16, 17, 31, 32, 33)]):
        extra.append({"id": "sequence-" + str(index), "hex": data.hex(), "seed": seed, "hash": "0x%08X" % oracle.murmur3_x86_32(data, seed)})
    for seed in (0, 1, 0x7FFFFFFF, 0x80000000, 0xFFFFFFFF):
        data = b"seed edge"
        extra.append({"id": "seed-" + hex(seed), "hex": data.hex(), "seed": seed, "hash": "0x%08X" % oracle.murmur3_x86_32(data, seed)})
    return {
        "vectors": published + extra,
        "verification": {
            "description": "keys [0..i-1] with seed 256-i for i in 0..255, results as little-endian words, hashed again with seed 0",
            "expected": "0x%08X" % oracle.SMHASHER_VERIFICATION_VALUE,
        },
    }


def build_utf8_document():
    cases = []
    for name, text in UNICODE_KEYS:
        cases.append({"id": "utf8-" + name, "text": text, "hex": oracle.utf8_with_replacement(text).hex()})
    return cases


SEMVER_VALID = [
    ("zero", "0.0.0"),
    ("plain", "1.2.3"),
    ("large", "10.20.30"),
    ("prerelease-build", "1.2.3-alpha.1+build.5"),
    ("prerelease-leading-zero-letters", "1.0.0-0A.is.legal"),
    ("prerelease-mixed", "1.0.0-x.7.z.92"),
    ("build-only", "1.0.0+20130313144700"),
    ("beta-exp-sha", "1.0.0-beta+exp.sha.5114f85"),
    ("hyphen-identifier", "1.0.0--"),
    ("hyphen-in-identifier", "1.0.0-alpha-1"),
    ("hyphen-only-segments", "1.0.0-a.-"),
    ("build-with-hyphen", "1.0.0+a-b.c"),
    ("build-leading-zero", "1.0.0+001"),
    ("huge-numbers", "99999999999999999999.88888888888888888888.77777777777777777777"),
    ("huge-prerelease", "1.0.0-18446744073709551616"),
]

SEMVER_INVALID = [
    ("empty", ""),
    ("one-part", "1"),
    ("two-parts", "1.2"),
    ("four-parts", "1.2.3.4"),
    ("leading-v", "v1.2.3"),
    ("leading-capital-v", "V1.2.3"),
    ("leading-zero-major", "01.2.3"),
    ("leading-zero-minor", "1.02.3"),
    ("leading-zero-patch", "1.2.03"),
    ("empty-prerelease", "1.2.3-"),
    ("numeric-prerelease-leading-zero", "1.2.3-01"),
    ("numeric-prerelease-leading-zero-later", "1.2.3-alpha.01"),
    ("empty-prerelease-identifier", "1.2.3-a..b"),
    ("empty-build", "1.2.3+"),
    ("empty-build-identifier", "1.2.3+a..b"),
    ("leading-space", " 1.2.3"),
    ("trailing-space", "1.2.3 "),
    ("trailing-newline", "1.2.3\n"),
    ("bad-prerelease-char", "1.2.3-@"),
    ("negative-major", "-1.2.3"),
    ("negative-patch", "1.2.-3"),
    ("non-ascii-prerelease", "1.2.3-é"),
    ("non-ascii-build", "1.2.3+é"),
    ("fullwidth-digit", "１.2.3"),
    ("arabic-indic-digit", "1.2.٣"),
    ("dots-only", "..."),
    ("two-plus-signs", "1.2.3+a+b"),
    ("text", "latest"),
    ("wildcard", "1.2.x"),
    ("range", "^1.2.3"),
    ("trailing-dot", "1.2.3."),
]

SEMVER_ORDER = [
    "0.9.9",
    "1.0.0-0",
    "1.0.0-1",
    "1.0.0-2",
    "1.0.0-10",
    "1.0.0-A",
    "1.0.0-a",
    "1.0.0-alpha",
    "1.0.0-alpha.1",
    "1.0.0-alpha.beta",
    "1.0.0-beta",
    "1.0.0-beta.2",
    "1.0.0-beta.11",
    "1.0.0-rc.1",
    "1.0.0",
    "1.0.1",
    "1.1.0",
    "1.10.0",
    "2.0.0",
    "2.1.0",
    "2.1.1",
    "10.0.0",
    "99999999999999999998.0.0",
    "99999999999999999999.0.0",
]


def build_semver_document():
    valid = []
    for name, text in SEMVER_VALID:
        major, minor, patch, pre, build = oracle.parse_semver(text)
        valid.append({"id": "valid-" + name, "input": text, "major": str(major), "minor": str(minor), "patch": str(patch), "prerelease": pre, "build": build})
    invalid = []
    for name, text in SEMVER_INVALID:
        assert oracle.parse_semver(text) is None, text
        invalid.append({"id": "invalid-" + name, "input": text})
    compare = []
    for index in range(len(SEMVER_ORDER) - 1):
        left, right = SEMVER_ORDER[index], SEMVER_ORDER[index + 1]
        assert oracle.compare_semver_text(left, right) == -1, (left, right)
        compare.append({"id": "order-" + str(index), "a": left, "b": right, "result": -1})
    for index, text in enumerate(["1.2.3", "1.0.0-rc.1", "1.0.0-alpha.1"]):
        compare.append({"id": "reflexive-" + str(index), "a": text, "b": text, "result": 0})
    extra = [
        ("build-ignored", "1.0.0+a", "1.0.0+b"),
        ("build-ignored-prerelease", "1.0.0-rc.1+x", "1.0.0-rc.1"),
        ("build-vs-none", "1.0.0", "1.0.0+exp.sha.5114f85"),
        ("numeric-identifiers-differ-in-width", "1.0.0-9", "1.0.0-10"),
        ("long-numeric-identifiers", "1.0.0-alpha.99999999999999999999", "1.0.0-alpha.100000000000000000000"),
        ("prefix-is-lower", "1.0.0-a.b", "1.0.0-a.b.c"),
        ("hyphen-identifiers", "1.0.0-a-b", "1.0.0-a-c"),
        ("numeric-below-alphanumeric", "1.0.0-99", "1.0.0-a"),
        ("alphanumeric-with-leading-digit", "1.0.0-1a", "1.0.0-1b"),
        ("digits-then-letters-above-number", "1.0.0-100", "1.0.0-1a"),
    ]
    for name, left, right in extra:
        compare.append({"id": "compare-" + name, "a": left, "b": right, "result": oracle.compare_semver_text(left, right)})
    return {"valid": valid, "invalid": invalid, "compare": compare}


def build_documents():
    for builder, name, description in [
        (build_core, "eval-core.json", "Disabled flags, kill switch, empty rules, rule order, variant types"),
        (build_operators, "eval-operators.json", "Every operator with matches, misses, type mismatches, lists and negate"),
        (build_missing, "eval-missing.json", "Missing, null and malformed attributes"),
        (build_semver_conditions, "eval-semver.json", "Semantic version operators, pre-releases and invalid versions"),
        (build_segments, "eval-segments.json", "Segments: included, excluded, groups, negate, unknown keys"),
        (build_rollouts, "eval-rollouts.json", "Rollouts and bucket boundaries"),
        (build_unicode, "eval-unicode.json", "Unicode and edge case context keys"),
    ]:
        add_document(name, description, {"cases": builder().items})
    add_document("murmur3.json", "Published MurmurHash3 x86 32 vectors and the SMHasher verification value", build_murmur_document())
    add_document("bucket.json", "Hash and bucket of flagKey.salt.contextKey", {"cases": build_bucket_document()})
    add_document("utf8.json", "UTF-8 encoding with U+FFFD for lone surrogates", {"cases": build_utf8_document()})
    add_document("semver.json", "Semantic version parsing and precedence", build_semver_document())


def render(document):
    lines = ["{"]
    keys = list(document.keys())
    for position, key in enumerate(keys):
        value = document[key]
        suffix = "," if position < len(keys) - 1 else ""
        if isinstance(value, list):
            lines.append("  " + json.dumps(key) + ": [")
            for item_position, item in enumerate(value):
                item_suffix = "," if item_position < len(value) - 1 else ""
                lines.append("    " + json.dumps(item, separators=(",", ":"), ensure_ascii=True) + item_suffix)
            lines.append("  ]" + suffix)
        else:
            lines.append("  " + json.dumps(key) + ": " + json.dumps(value, separators=(",", ":"), ensure_ascii=True) + suffix)
    lines.append("}")
    return "\n".join(lines) + "\n"


def main(argv):
    oracle.self_test()
    build_documents()
    check = "--check" in argv
    stale = []
    total = 0
    for name, document in sorted(documents.items()):
        text = render(document)
        path = os.path.join(VECTOR_DIR, name)
        if check:
            current = open(path, encoding="utf-8").read() if os.path.exists(path) else None
            if current != text:
                stale.append(name)
        else:
            os.makedirs(VECTOR_DIR, exist_ok=True)
            with open(path, "w", encoding="utf-8", newline="\n") as handle:
                handle.write(text)
        total += sum(len(value) for key, value in document.items() if isinstance(value, list))
    eval_total = sum(len(document["cases"]) for name, document in documents.items() if name.startswith("eval-"))
    if check:
        if stale:
            sys.stderr.write("stale vector files: " + ", ".join(stale) + "\n")
            return 1
        sys.stdout.write("vectors are up to date\n")
    sys.stdout.write("evaluation cases: " + str(eval_total) + ", all cases: " + str(total) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
