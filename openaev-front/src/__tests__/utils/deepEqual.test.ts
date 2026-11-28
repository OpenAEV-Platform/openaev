import { describe, expect, it } from 'vitest';

import { deepEqual } from '../../utils/object';

class Point { x = 1; }
const sameDate = new Date(0);

describe('deepEqual', () => {
  it.each([
    {
      name: 'same numbers',
      a: 1,
      b: 1,
      expected: true,
    },
    {
      name: 'a number and a string',
      a: 1,
      b: '1',
      expected: false,
    },
    {
      name: 'NaN and NaN',
      a: NaN,
      b: NaN,
      expected: true,
    },
    {
      name: 'null and undefined',
      a: null,
      b: undefined,
      expected: false,
    },
    {
      name: 'same nested arrays',
      a: [1, [2, 3]],
      b: [1, [2, 3]],
      expected: true,
    },
    {
      name: 'same items in another order',
      a: [1, 2],
      b: [2, 1],
      expected: false,
    },
    {
      name: 'arrays of different lengths',
      a: [1],
      b: [1, 2],
      expected: false,
    },
    {
      name: 'keys in another order',
      a: {
        x: 1,
        y: 2,
      },
      b: {
        y: 2,
        x: 1,
      },
      expected: true,
    },
    {
      name: 'deep objects differing at the bottom',
      a: { x: { y: [1] } },
      b: { x: { y: [2] } },
      expected: false,
    },
    {
      name: 'an undefined value and a missing key',
      a: { x: undefined },
      b: {},
      expected: false,
    },
    {
      name: 'an array and an object with the same keys',
      a: [1],
      b: { 0: 1 },
      expected: false,
    },
    {
      name: 'an object and null',
      a: {},
      b: null,
      expected: false,
    },
    {
      name: 'an object without prototype and a plain one',
      a: Object.assign(Object.create(null), { x: 1 }),
      b: { x: 1 },
      expected: true,
    },
    {
      name: 'the same date',
      a: sameDate,
      b: sameDate,
      expected: true,
    },
    {
      name: 'two equal dates (not JSON data)',
      a: new Date(0),
      b: new Date(0),
      expected: false,
    },
    {
      name: 'two class instances with the same keys',
      a: new Point(),
      b: new Point(),
      expected: true,
    },
  ])('given $name should return $expected, whatever the argument order', ({ a, b, expected }) => {
    // Act
    const result = deepEqual(a, b);
    const swappedResult = deepEqual(b, a);

    // Assert
    expect(result).toBe(expected);
    expect(swappedResult).toBe(expected);
  });
});
