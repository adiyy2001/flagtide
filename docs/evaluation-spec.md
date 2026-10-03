# Flag evaluation specification

Spec version: 1

This document is normative. The Java engine (`apps/server/domain`) and the TypeScript engine (`libs/core`) implement it independently, and both must produce identical results for every case in `spec/vectors`. Where the text and a vector disagree, the vectors are wrong or the text is ambiguous: fix the text first, then the vectors, then the engines. Rationale for the main choices is in [ADR 0009](adr/0009-evaluation-bucketing-conformance.md).

The key words MUST, MUST NOT and MAY are used as in RFC 2119.

## 1. Data model

All data is JSON. Numbers are IEEE 754 binary64 values, parsed from the JSON text with the nearest double. NaN and infinity cannot occur in JSON and are never produced.

### 1.1 Context

```json
{ "key": "user-42", "attributes": { "plan": "pro", "age": 31, "beta": true, "roles": ["admin", "dev"] } }
```

- `key`: a string, any content including the empty string. It identifies the subject for bucketing and for segment membership.
- `attributes`: an object. A value is a string, a number, a boolean, or an array of those scalars. A `null` value and an absent attribute are the same thing: missing. A value of any other shape (an object, or an array holding something that is not a scalar) is treated as missing.
- The key is not an attribute. A condition cannot refer to it.

### 1.2 Flag config

```json
{
  "key": "new-checkout",
  "type": "boolean",
  "enabled": true,
  "killSwitch": false,
  "salt": "9f2c41",
  "variants": [{ "key": "on", "value": true }, { "key": "off", "value": false }],
  "offVariant": "off",
  "rules": [
    {
      "id": "beta-users",
      "conditions": [{ "attribute": "plan", "operator": "in", "values": ["pro", "team"] }],
      "serve": { "rollout": [{ "variant": "on", "weight": 25000 }, { "variant": "off", "weight": 75000 }] }
    }
  ],
  "fallthrough": { "variant": "off" }
}
```

| Field | Rule |
| --- | --- |
| `key` | Slug: `^[a-z0-9][a-z0-9_-]{0,63}$`. It never contains a dot. |
| `type` | `boolean`, `string`, `number` or `json`. The evaluator does not look at it. It tells producers which variant values are legal. |
| `enabled`, `killSwitch` | Booleans. |
| `salt` | Lowercase hex string, 1 to 64 characters. It never contains a dot. |
| `variants` | Non-empty list. Variant keys are slugs and unique. A value is any JSON value that fits `type`. |
| `offVariant` | The key of one variant. |
| `rules` | Ordered list, possibly empty. Rule ids are unique non-empty strings. |
| `fallthrough` | A serve. |

A `prerequisites` list is reserved for a later spec version and MUST be ignored by version 1.

Producers validate configs before publishing them. The behavior of an engine on an invalid config is unspecified, except that an engine MUST NOT return a variant that does not exist. The reference engines throw. Vectors never contain an invalid config.

### 1.3 Serve

A serve is either `{ "variant": "<key>" }` or `{ "rollout": [ { "variant": "<key>", "weight": <integer> }, ... ] }`.

A rollout is a non-empty list. Weights are integers from 0 to 100000, one unit is a thousandth of a percent, and the weights sum to exactly 100000. A variant appears at most once. Zero weights are allowed.

### 1.4 Condition

An attribute condition:

```json
{ "attribute": "version", "operator": "semverGte", "values": ["2.0.0-rc.1"], "negate": false }
```

A segment condition:

```json
{ "segment": "beta-testers", "negate": false }
```

`negate` is optional and defaults to false. `values` is a non-empty list of scalars. The operators `equals`, `lt`, `lte`, `gt`, `gte`, `semverEquals`, `semverLt`, `semverLte`, `semverGt` and `semverGte` take exactly one value. `in`, `contains` and `startsWith` take one or more.

### 1.5 Segment

```json
{ "key": "beta-testers", "included": ["u1"], "excluded": ["u9"], "rules": [[{ "attribute": "country", "operator": "equals", "values": ["PL"] }]] }
```

`included` and `excluded` are lists of context keys. `rules` is a list of groups, each group a list of attribute conditions. A segment cannot refer to another segment. Segment keys are slugs and unique within a snapshot.

## 2. Evaluation

Input: a flag config, the snapshot's segments, a context. Output: a result.

```json
{ "variantKey": "on", "value": true, "reason": "RULE_MATCH", "ruleIndex": 0, "ruleId": "beta-users", "bucket": 12345 }
```

`ruleIndex`, `ruleId` and `bucket` are `null` when they do not apply. Evaluation is a pure function: no clock, no randomness, no I/O.

Steps, in this order, stopping at the first that applies:

1. If `killSwitch` is true: serve the off variant, reason `KILL_SWITCH`.
2. If `enabled` is false: serve the off variant, reason `OFF`.
3. For each rule in list order: if the rule matches (section 2.1), serve its `serve` with reason `RULE_MATCH`, `ruleIndex` the zero-based position and `ruleId` its id. The first matching rule wins.
4. Serve `fallthrough` with reason `FALLTHROUGH`.

Serving a variant key sets `variantKey` and `value`, with `bucket` null. Serving a rollout computes the bucket (section 3), sets `bucket` to it, and picks the variant (section 3.3). The off variant is always served directly. A kill switch or a disabled flag never computes a bucket.

### 2.1 Rule matching

A rule matches when every one of its conditions matches. A rule without conditions matches every context. Conditions are evaluated in list order, and evaluation has no side effects, so the order is only a cost matter.

### 2.2 Attribute conditions

Let `a` be the attribute named by the condition.

1. If `a` is missing, the condition does not match. `negate` has no effect.
2. Turn `a` into a list of elements: a scalar becomes a one-element list, an array is used as is. Keep only the elements that are applicable to the operator (table below).
3. If no element is applicable, the condition does not match. `negate` has no effect.
4. Otherwise the condition is true when some applicable element satisfies the operator against some value of `values`, and the result is that truth value XOR `negate`.

| Operator | Applicable elements | An element `e` satisfies it against value `v` when |
| --- | --- | --- |
| `equals`, `in` | strings, numbers, booleans | `e` and `v` have the same JSON type and are equal. Strings: same UTF-16 code units. Numbers: `e == v` as doubles, so `-0` equals `0`. |
| `contains` | strings | `v` is a string and `e` contains `v` as a substring. The empty string is contained in everything. |
| `startsWith` | strings | `v` is a string and `e` starts with `v`. |
| `lt`, `lte`, `gt`, `gte` | numbers | `v` is a number and `e < v`, `e <= v`, `e > v`, `e >= v` as doubles. |
| `semverEquals`, `semverLt`, `semverLte`, `semverGt`, `semverGte` | strings that are valid semantic versions (section 4) | `v` is a valid semantic version and the precedence of `e` compared with `v` is equal, lower, lower or equal, higher, higher or equal. |

`equals` and `in` differ only in how many values they accept. A value of another type than the element never matches it. For example the string `"5"` does not equal the number 5, and with `negate` the condition is true because the attribute is present and applicable.

String comparison and substring search work on UTF-16 code units, are case sensitive, and apply no Unicode normalization.

### 2.3 Segment conditions

A segment condition names a segment key. If the snapshot has no such segment, the condition does not match and `negate` has no effect. Otherwise the context is a member of the segment when:

1. its key is in `excluded`: not a member, whatever else is true;
2. else its key is in `included`: member;
3. else some group in `rules` has all its conditions matching (section 2.2) the context: member. A group without conditions matches every context. A segment without groups has no members besides `included`.

The condition is the membership XOR `negate`.

## 3. Bucketing

### 3.1 Hash

`MurmurHash3_x86_32`, seed 0, as specified by Austin Appleby's reference implementation (public domain). In pseudocode with all arithmetic modulo 2^32, `rotl` a 32-bit rotate left and block reads little-endian:

```
c1 = 0xcc9e2d51
c2 = 0x1b873593
h = seed
for each full 4-byte block k:
    k = k * c1
    k = rotl(k, 15)
    k = k * c2
    h = h xor k
    h = rotl(h, 13)
    h = h * 5 + 0xe6546b64
tail = the remaining 0 to 3 bytes, read little-endian into k (first byte least significant)
if tail is not empty:
    k = k * c1
    k = rotl(k, 15)
    k = k * c2
    h = h xor k
h = h xor length_in_bytes
h = h xor (h >> 16)
h = h * 0x85ebca6b
h = h xor (h >> 13)
h = h * 0xc2b2ae35
h = h xor (h >> 16)
```

`>>` is a logical shift. The result is an unsigned 32-bit integer. Implementations in languages with signed 32-bit integers MUST convert to unsigned before the modulo in section 3.2.

Reference values (all in `spec/vectors/murmur3.json`): the empty input with seed 0 gives `0x00000000`, with seed 1 gives `0x514E28B7`, `"test"` gives `0xBA6BD213`, `"Hello, world!"` gives `0xC0363E43`, `"The quick brown fox jumps over the lazy dog"` gives `0x2E4FF723`. The SMHasher verification value is `0xB0F57EE3`: hash the keys `[0, 1, ..., i-1]` for `i` from 0 to 255 with seed `256 - i`, write each result as four little-endian bytes into a 1024-byte buffer, hash that buffer with seed 0, and the result must equal `0xB0F57EE3`.

### 3.2 Bucket

```
input  = flagKey + "." + salt + "." + contextKey
bucket = unsigned(MurmurHash3_x86_32(utf8(input), seed 0)) mod 100000
```

The bucket is an integer from 0 to 99999. The flag key and the salt contain no dot, so the first two dots always separate the three parts, and the context key may contain anything, dots included.

### 3.3 Picking a variant

Walk the rollout entries in list order with a running total of weights. Serve the first entry whose running total, after adding its own weight, is greater than the bucket. An entry with weight 0 therefore never wins. If no entry qualifies, the config is invalid (the weights do not sum to 100000).

Because the walk is cumulative and in list order, raising the weight of the first entry from `p1` to `p2` only ever moves buckets into it. A context served the first variant at `p1` is served it at `p2`, provided the other entries keep their order.

### 3.4 UTF-8 encoding

The input string is a sequence of UTF-16 code units. It is encoded to UTF-8 as follows: a surrogate pair becomes one four-byte sequence, and a lone surrogate (a high surrogate not followed by a low one, or a low surrogate not preceded by a high one) becomes U+FFFD, bytes `EF BF BD`, one per lone code unit. Everything else uses the shortest standard encoding. No normalization (NFC, NFD) and no case folding is applied, so a precomposed character and its decomposed form hash differently.

The platform encoders MUST NOT be used: Java's `String.getBytes(UTF_8)` writes `?` for a lone surrogate.

## 4. Semantic versions

Semantic Versioning 2.0.0, strictly:

```
version    = core [ "-" prerelease ] [ "+" build ]
core       = number "." number "." number
number     = "0" | nonzero-digit { digit }
prerelease = identifier { "." identifier }
identifier = number | alphanumeric containing at least one letter or hyphen
build      = ( digit | letter | "-" ) { digit | letter | "-" } { "." same }
```

Letters are ASCII. A leading `v`, a missing component, a numeric identifier with a leading zero, an empty identifier and any whitespace make a string invalid. A string that is not a valid version is never an applicable attribute and never a usable value.

Build metadata is ignored in comparisons. Precedence compares, in order:

1. major, minor and patch as numbers. They can be arbitrarily large, so implementations compare the digit strings by length and then lexicographically instead of converting to a fixed-size integer;
2. a version without a pre-release is higher than the same core with one;
3. pre-release identifiers left to right: two numeric identifiers compare as numbers (again as digit strings), a numeric identifier is lower than an alphanumeric one, two alphanumeric identifiers compare by ASCII order;
4. when all shared identifiers are equal, the version with fewer identifiers is lower.

`semverEquals` is true for precedence equality, so `1.0.0+a` equals `1.0.0+b`.

## 5. Conformance

`spec/vectors` holds the shared test data. Every file has `specVersion`, a `description` and a list of cases with a unique `id`.

| File | Content | Source of the expected values |
| --- | --- | --- |
| `murmur3.json` | Published reference vectors, the SMHasher verification procedure | Reference implementations and test suites, reproduced by `spec/tools/oracle.py` |
| `bucket.json` | Hash and bucket for realistic inputs, unicode keys, boundary buckets | `spec/tools/oracle.py` |
| `semver.json` | Valid and invalid strings, precedence pairs including all examples of semver.org section 11 | semver.org and `spec/tools/oracle.py` |
| `eval-*.json` | Whole evaluations: flag, segments, context, expected result | Written by hand from this text in `spec/tools/generate_vectors.py`. Only hashes, buckets and semver outcomes come from the oracle |

A runner loads every file, runs every case, compares the full result including `reason`, `ruleIndex`, `ruleId` and `bucket`, prints `conformance <language> cases=<n> passed=<n> failed=<n>` and exits non-zero on any failure or when the vector directory holds no cases. `pnpm conformance` runs the Java and the TypeScript runner and fails unless both report the same case count.

The JSON Schema of the configs is `spec/schema/flag-config.schema.json`.

## 6. Changing the spec

A change that alters the result for any existing vector bumps `specVersion`. Adding vectors does not.
