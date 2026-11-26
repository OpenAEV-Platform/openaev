import { describe, expect, it } from 'vitest';

import { isEmpty, omit, pick } from '../../utils/object';

describe('pick', () => {
  it('given present keys should copy only those keys', () => {
    // Arrange
    const obj = {
      a: 1,
      b: 2,
      c: 3,
    };

    // Act
    const result = pick(obj, ['a', 'c']);

    // Assert
    expect(result).toEqual({
      a: 1,
      c: 3,
    });
  });

  it('given a key absent from the object should not create it', () => {
    // Arrange
    const obj: {
      a: number;
      b?: number;
    } = { a: 1 };

    // Act
    const result = pick(obj, ['a', 'b']);

    // Assert
    expect(Object.keys(result)).toEqual(['a']);
  });

  it('given a key whose value is undefined should keep it', () => {
    // Arrange
    const obj = {
      a: undefined,
      b: 2,
    };

    // Act
    const result = pick(obj, ['a']);

    // Assert
    expect(Object.keys(result)).toEqual(['a']);
  });

  it('given an inherited key should copy it', () => {
    // Arrange
    const obj = Object.create({ inherited: 'yes' }) as { inherited: string };

    // Act
    const result = pick(obj, ['inherited']);

    // Assert
    expect(result).toEqual({ inherited: 'yes' });
  });
});

describe('omit', () => {
  it('given keys should return a copy without them and leave the source untouched', () => {
    // Arrange
    const obj = {
      a: 1,
      b: 2,
      c: 3,
    };

    // Act
    const result = omit(obj, ['b']);

    // Assert
    expect(result).toEqual({
      a: 1,
      c: 3,
    });
    expect(obj).toEqual({
      a: 1,
      b: 2,
      c: 3,
    });
  });

  it('given a key absent from the object should return an equal copy', () => {
    // Arrange
    const obj: {
      a: number;
      b?: number;
    } = { a: 1 };

    // Act
    const result = omit(obj, ['b']);

    // Assert
    expect(result).toEqual({ a: 1 });
    expect(result).not.toBe(obj);
  });
});

describe('isEmpty', () => {
  it.each([
    ['an empty string', ''],
    ['an empty array', []],
    ['an empty plain object', {}],
    ['an object without prototype', Object.create(null)],
  ])('given %s should be empty', (_label, value) => {
    // Act
    const result = isEmpty(value);

    // Assert
    expect(result).toBe(true);
  });

  it.each([
    ['null', null],
    ['undefined', undefined],
    ['a non-empty string', ' '],
    ['a non-empty array', [undefined]],
    ['an object with a key', { a: undefined }],
    ['zero', 0],
    ['NaN', Number.NaN],
    ['false', false],
    ['a Date', new Date(0)],
    ['a function', () => undefined],
  ])('given %s should not be empty', (_label, value) => {
    // Act
    const result = isEmpty(value);

    // Assert
    expect(result).toBe(false);
  });
});
