import { describe, expect, it } from 'vitest';

import { hasHttpStatus, isNotFoundError } from '../../../utils/query/httpErrors';

// The shapes network.ts rejects with: the answer's status and body, or false on a network failure.
const answered = (status: number) => ({
  status,
  message: 'failed',
  forbiddenAction: false,
});

describe('httpErrors', () => {
  it('given_a404Answer_should_beNotFound', () => {
    expect(isNotFoundError(answered(404))).toBe(true);
    expect(isNotFoundError(answered(403))).toBe(false);
  });

  it('given_a500Answer_should_haveAStatus', () => {
    expect(hasHttpStatus(answered(500))).toBe(true);
  });

  it.each([false, undefined, null, new Error('network'), { status: '404' }])('given_noHttpAnswer_%s_should_haveNoStatus', (error) => {
    expect(hasHttpStatus(error)).toBe(false);
    expect(isNotFoundError(error)).toBe(false);
  });
});
