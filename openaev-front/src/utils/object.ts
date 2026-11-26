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

// Same semantics as Ramda's `isEmpty`, for any input: `true` only for an empty string, array, plain object,
// Map, Set, typed array or `arguments`. `null` and `undefined` are NOT empty (unlike a `!value` check), and
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
