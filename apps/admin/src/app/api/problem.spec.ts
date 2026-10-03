import { describe, expect, it } from 'vitest';
import { ApiError, MissingKeyError, describeFailure } from './problem';

describe('ApiError', () => {
  it('uses the detail, then the title, then the status', () => {
    expect(new ApiError(400, { title: 'Bad', detail: 'Rule 1 is wrong' }).message).toBe('Rule 1 is wrong');
    expect(new ApiError(400, { title: 'Bad' }).message).toBe('Bad');
    expect(new ApiError(502, null).message).toContain('502');
  });

  it('classifies conflicts and authorisation failures', () => {
    expect(new ApiError(409, null).isConflict).toBe(true);
    expect(new ApiError(401, null).isUnauthorized).toBe(true);
    expect(new ApiError(403, null).isUnauthorized).toBe(true);
    expect(new ApiError(500, null).isUnauthorized).toBe(false);
  });

  it('keeps field issues and lists them in the description', () => {
    const failure = new ApiError(422, { errors: [{ field: 'rules[0]', message: 'unknown segment' }, {}] });
    expect(failure.fieldIssues).toHaveLength(2);
    expect(describeFailure(failure)).toBe('rules[0]: unknown segment; : ');
  });
});

describe('describeFailure', () => {
  it('describes other errors and unknown values', () => {
    expect(describeFailure(new MissingKeyError('prod'))).toContain('prod');
    expect(describeFailure(new Error('boom'))).toBe('boom');
    expect(describeFailure('x')).toBe('Something went wrong');
  });
});
