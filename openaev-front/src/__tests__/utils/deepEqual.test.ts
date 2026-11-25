import { describe, expect, it } from 'vitest';

import { deepEqual } from '../../utils/object';
import { buildDeepEqualCases } from './deepEqualCases';

describe('deepEqual', () => {
  // The expectations are the results of Ramda's `equals`, see deepEqualCases.ts
  it.each(buildDeepEqualCases())('given $name should return $expected, whatever the argument order', ({ a, b, expected, expectedSwapped }) => {
    // Act
    const result = deepEqual(a, b);
    const swappedResult = deepEqual(b, a);

    // Assert
    expect(result).toBe(expected);
    expect(swappedResult).toBe(expectedSwapped ?? expected);
  });
});
