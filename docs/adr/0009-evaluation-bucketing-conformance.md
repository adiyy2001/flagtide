# 0009 Evaluation algorithm, bucketing and the conformance suite

Status: accepted, 2026-10-03

## Context

Java on the server and TypeScript in the browser must give the same answer for the same flag and context. The project fixes the hash (MurmurHash3 x86 32 bit, seed 0, input `flagKey.salt.contextKey`, unsigned, modulo 100000). Several details are left open, and each one can make the two languages disagree.

## Decision

The normative text is `docs/evaluation-spec.md`, written together with the first implementation. These are the choices it records.

- Bucket: `murmur3_x86_32(utf8(flagKey + "." + salt + "." + contextKey), seed 0)` as an unsigned 32 bit value, modulo 100000. Flag keys are slugs without dots and the salt is lowercase hex, so the separator is unambiguous. The context key is last and can contain anything.
- UTF-8: both languages use their own encoder. A lone surrogate becomes U+FFFD (`EF BF BD`). Java's `getBytes` would write `?` and `TextEncoder` would write U+FFFD, so neither built-in is used. No Unicode normalization and no case folding.
- Rollout weights are integers in thousandths of a percent and sum to exactly 100000. Variants are walked in list order with cumulative thresholds: bucket `b` is served the first variant whose cumulative weight is greater than `b`. A boolean rollout lists `true` first, so raising its weight never removes a context that was already in.
- Evaluation order: kill switch (off variant, reason `KILL_SWITCH`), flag disabled (off variant, `OFF`), rules in order (first match wins, `RULE_MATCH` with the rule index and id), fallthrough (`FALLTHROUGH`).
- A rule matches when all its conditions match (AND). A rule without conditions matches everything. A condition compares one context attribute with a non-empty list of values. Operators: `equals`, `in`, `contains`, `startsWith`, `lt`, `lte`, `gt`, `gte`, and `semverEquals`, `semverLt`, `semverLte`, `semverGt`, `semverGte`, each with an optional `negate`.
- A missing attribute, or an attribute of the wrong type, makes the condition false even when `negate` is set. Equality is strict about type. A list attribute matches when any element matches. String operations work on UTF-16 code units, case sensitive.
- Numbers are IEEE 754 doubles in both languages. NaN and infinity cannot occur in JSON.
- Semver follows semver.org 2.0.0 strictly (no leading `v`, no missing parts). Build metadata is ignored. Pre-release identifiers compare as the spec says: numeric below alphanumeric, numeric identifiers as numbers without overflow (compared as digit strings), a shorter list is lower when all shared identifiers are equal. An attribute that is not valid semver does not match.
- Segments hold included keys, excluded keys (which win) and rule groups (OR of AND groups). Segments cannot reference segments.
- Conformance: `spec/vectors/*.json`, at least 200 evaluation cases plus hash, bucket and semver files. Expected hashes, buckets and semver results come from an independent Python oracle (`spec/tools/oracle.py`, standard library only), expected evaluation outcomes are specified by hand from the spec text. Both runners are run in CI, report the same case count, and fail on any mismatch. A meta test proves the runners fail on a corrupted vector.

## Alternatives

- Floating point percentages: rounding differences across languages and a sum that is "close to" 100.
- Library hashes (Guava, a murmur package on npm): I wanted my own implementation, and a library would hide the Unicode and signedness decisions.
- Generating expected values from the Java engine: then a shared bug in both engines would pass unnoticed.

## Consequences

- The spec is versioned (`specVersion: 1`) and has room for prerequisites later.
- `bucketBy` another attribute is out of scope for now and would be a spec change.
