export { BUCKET_SPACE, bucketOf } from './lib/bucket';
export { InvalidFlagConfigError, evaluate, indexSegments } from './lib/evaluate';
export { murmur3x86_32, murmur3x86_32OfText } from './lib/murmur3';
export { compareSemanticVersions, parseSemanticVersion } from './lib/semver';
export type { SemanticVersion } from './lib/semver';
export { encodeUtf8, utf8Length } from './lib/utf8';
export type * from './lib/types';
