import { describe, expect, it } from 'vitest';

import { httpStatus, isNotFoundError } from '../../../utils/query/httpErrors';

// The shapes network.ts rejects with: the answer's status and body, or false on a network failure.
const answered = (status: number) => ({
  status,
  message: 'failed',
  forbiddenAction: false,
});

describe('httpErrors', () => {
  it.each([
    [answered(404), true],
    [answered(403), false],
  ])('given_%o_should_beNotFound_%s', (error, expected) => {
    // Act
    const notFound = isNotFoundError(error);

    // Assert
    expect(notFound).toBe(expected);
  });

  it.each([false, undefined, null, new Error('network'), { status: '404' }])('given_noHttpAnswer_%s_should_haveNoStatusAndNotBeNotFound', (error) => {
    // Act
    const status = httpStatus(error);

    // Assert
    expect(status).toBeUndefined();
    expect(isNotFoundError(error)).toBe(false);
  });

  it('given_anAnswer_should_haveItsStatus', () => {
    // Act
    const status = httpStatus(answered(503));

    // Assert
    expect(status).toBe(503);
  });
});
