// Same semantics as Ramda's `pick`: a key is copied when `key in obj` (inherited ones included),
// an absent key stays absent in the result (no `undefined` placeholder)
export const pick = <T extends object, K extends keyof T>(obj: T, keys: readonly K[]): Pick<T, K> => {
  const result = {} as Pick<T, K>;
  keys.forEach((key) => {
    if (key in obj) {
      result[key] = obj[key];
    }
  });
  return result;
};

// A shallow copy without the given keys, `obj` is untouched. Unlike Ramda's `omit` / `dissoc` it copies own
// enumerable properties only (not inherited ones), which is what plain JSON payloads and Redux state need
export const omit = <T extends object, K extends keyof T>(obj: T, keys: readonly K[]): Omit<T, K> => {
  const result: Partial<T> = { ...obj };
  keys.forEach((key) => {
    delete result[key];
  });
  return result as Omit<T, K>;
};

// Same semantics as Ramda's `isEmpty` for the JSON data of the application: `true` only for an empty string,
// array, plain object, Map, Set, typed array or `arguments`. Known gaps with Ramda, none reachable with our
// data: a sparse array like `[,]` (Ramda `true`, here `false`) and an empty BigInt64Array (Ramda `false`, here
// `true`). `null` and `undefined` are NOT empty (unlike a `!value` check), and
// neither are a number, a boolean, a function or a Date. Own enumerable string keys are what makes an object
// non-empty (symbol keys and inherited ones are ignored). Ramda's `empty` hooks (a `fantasy-land/empty` or an
// `empty` method on the value or its constructor) are not supported: none of our data carries them
export const isEmpty = (value: unknown): boolean => {
  if (value === null || value === undefined) {
    return false;
  }
  if (typeof value === 'string' || Array.isArray(value)) {
    return value.length === 0;
  }
  if (value instanceof Map || value instanceof Set) {
    return value.size === 0;
  }
  if (ArrayBuffer.isView(value) && !(value instanceof DataView)) {
    return (value as unknown as ArrayLike<unknown>).length === 0;
  }
  const tag = Object.prototype.toString.call(value);
  if (tag === '[object Arguments]') {
    return (value as ArrayLike<unknown>).length === 0;
  }
  if (tag === '[object Object]') {
    return Object.keys(value).length === 0;
  }
  return false;
};

type Loose = Record<PropertyKey, unknown>;

// Ramda's `type`: the `Object.prototype.toString` tag, with `Null` and `Undefined` spelled out
const tagOf = (value: unknown): string => {
  if (value === null) {
    return 'Null';
  }
  if (value === undefined) {
    return 'Undefined';
  }
  return Object.prototype.toString.call(value).slice(8, -1);
};

// Ramda's `keys`: own enumerable string keys, none for a primitive
const ownKeys = (value: unknown): string[] => (Object(value) !== value ? [] : Object.keys(value as object));

// Ramda's `_functionName`: `class X {}` has no `function ` prefix, so its name is not found
const functionName = (fn: unknown): string => String(fn).match(/^function (\w*)/)?.[1] ?? '';

// Ramda's `_uniqContentEquals`: every element of `a` has an equal one in `b` (the sizes are already equal).
// It is not a multiset comparison, so [1, 1, 2] and [1, 2, 2] are "equal" as Set / Map contents (like Ramda)
const sameContent = (a: unknown[], b: unknown[], equals: (itemA: unknown, itemB: unknown) => boolean): boolean => (
  a.every(itemA => b.some(itemB => equals(itemA, itemB)))
);

const equalsWithStacks = (a: unknown, b: unknown, stackA: unknown[], stackB: unknown[]): boolean => {
  if (Object.is(a, b)) {
    return true;
  }
  const tag = tagOf(a);
  if (tag !== tagOf(b)) {
    return false;
  }
  const objectA = a as Loose;
  const objectB = b as Loose;
  // custom equality of the value (Fantasy Land, then an `equals` method): both sides must agree
  const landEqualsA = objectA['fantasy-land/equals'];
  const landEqualsB = objectB['fantasy-land/equals'];
  if (typeof landEqualsA === 'function' || typeof landEqualsB === 'function') {
    return typeof landEqualsA === 'function' && Boolean(landEqualsA.call(a, b))
      && typeof landEqualsB === 'function' && Boolean(landEqualsB.call(b, a));
  }
  const equalsA = objectA.equals;
  const equalsB = objectB.equals;
  if (typeof equalsA === 'function' || typeof equalsB === 'function') {
    return typeof equalsA === 'function' && Boolean(equalsA.call(a, b))
      && typeof equalsB === 'function' && Boolean(equalsB.call(b, a));
  }
  switch (tag) {
    case 'Arguments':
    case 'Array':
    case 'Object':
      if (typeof objectA.constructor === 'function' && functionName(objectA.constructor) === 'Promise') {
        return a === b;
      }
      break;
    case 'Boolean':
    case 'Number':
    case 'String':
      // a primitive and its wrapper object are different
      if (!(typeof a === typeof b && Object.is((a as object).valueOf(), (b as object).valueOf()))) {
        return false;
      }
      break;
    case 'Date':
      if (!Object.is((a as Date).valueOf(), (b as Date).valueOf())) {
        return false;
      }
      break;
    case 'Error':
      return objectA.name === objectB.name && objectA.message === objectB.message;
    case 'RegExp': {
      const regexpA = a as RegExp;
      const regexpB = b as RegExp;
      if (!(regexpA.source === regexpB.source
        && regexpA.global === regexpB.global
        && regexpA.ignoreCase === regexpB.ignoreCase
        && regexpA.multiline === regexpB.multiline
        && regexpA.sticky === regexpB.sticky
        && regexpA.unicode === regexpB.unicode)) {
        return false;
      }
      break;
    }
    default:
  }
  // a value already being compared higher up (a cycle) is equal when it is paired with the same value
  for (let index = stackA.length - 1; index >= 0; index -= 1) {
    if (stackA[index] === a) {
      return stackB[index] === b;
    }
  }
  // the content of a Map / Set is compared with the container pushed on its own copy of the stacks
  const equalsInContainer = (itemA: unknown, itemB: unknown) => equalsWithStacks(itemA, itemB, [...stackA, a], [...stackB, b]);
  switch (tag) {
    case 'Map':
      if ((a as Map<unknown, unknown>).size !== (b as Map<unknown, unknown>).size) {
        return false;
      }
      return sameContent([...(a as Map<unknown, unknown>).entries()], [...(b as Map<unknown, unknown>).entries()], equalsInContainer);
    case 'Set':
      if ((a as Set<unknown>).size !== (b as Set<unknown>).size) {
        return false;
      }
      return sameContent([...(a as Set<unknown>).values()], [...(b as Set<unknown>).values()], equalsInContainer);
    case 'Arguments':
    case 'Array':
    case 'Object':
    case 'Boolean':
    case 'Number':
    case 'String':
    case 'Date':
    case 'Error':
    case 'RegExp':
    case 'Int8Array':
    case 'Uint8Array':
    case 'Uint8ClampedArray':
    case 'Int16Array':
    case 'Uint16Array':
    case 'Int32Array':
    case 'Uint32Array':
    case 'Float32Array':
    case 'Float64Array':
    case 'ArrayBuffer':
      break;
    default:
      // any other type (function, symbol, bigint, promise, WeakMap...) is only equal to itself
      return false;
  }
  const keysA = ownKeys(a);
  if (keysA.length !== ownKeys(b).length) {
    return false;
  }
  const extendedStackA = [...stackA, a];
  const extendedStackB = [...stackB, b];
  for (let index = keysA.length - 1; index >= 0; index -= 1) {
    const key = keysA[index];
    if (!(Object.prototype.hasOwnProperty.call(b, key) && equalsWithStacks(objectB[key], objectA[key], extendedStackA, extendedStackB))) {
      return false;
    }
  }
  return true;
};

// Port of Ramda's `equals` (0.32), which `useHelper` relied on: a deep structural equality where
// - primitives compare with `Object.is` (NaN equals NaN, 0 differs from -0), a primitive never equals its wrapper;
// - values of different `Object.prototype.toString` tags are different (an array is never a plain object);
// - plain objects, arrays, arguments, typed arrays, boxed primitives compare their own enumerable string keys,
//   whatever their order and prototype (two instances of different classes with the same keys are equal);
// - Date compares by time, RegExp by source and flags (not `dotAll` / `hasIndices`), Error by name and message;
// - Map and Set compare their content without order, an entry matching a deeply equal one;
// - a cycle is equal to the other cycle when both come back to the same pair of values;
// - any other type (function, symbol, bigint, promise, WeakMap, DataView...) is only equal to itself;
// - an object with an `equals` / `fantasy-land/equals` method decides by itself, as long as both sides agree.
// Known gaps with Ramda, none reachable with the JSON data of the store: the custom `equals` result is coerced to a
// boolean (Ramda returns it as is), and a getter named `equals` is read once more than Ramda reads it
export const deepEqual = (a: unknown, b: unknown): boolean => equalsWithStacks(a, b, [], []);
