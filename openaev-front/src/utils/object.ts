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

const isPlainObject = (value: unknown): value is Record<string, unknown> =>
  Object.prototype.toString.call(value) === '[object Object]';

// `null` and `undefined` are not empty: use isEmptyField for that
export const isEmpty = (value: unknown): boolean => {
  if (typeof value === 'string' || Array.isArray(value)) {
    return value.length === 0;
  }
  if (isPlainObject(value)) {
    return Object.keys(value).length === 0;
  }
  return false;
};

// Deep equality for the store JSON, so that `useHelper` keeps the previous result when nothing changed.
// Any other object (Date, Map...) is only equal to itself: a wrong "different" only costs a re-render
export const deepEqual = (a: unknown, b: unknown): boolean => {
  if (Object.is(a, b)) {
    return true;
  }
  if (Array.isArray(a) && Array.isArray(b)) {
    return a.length === b.length && a.every((item, index) => deepEqual(item, b[index]));
  }
  if (isPlainObject(a) && isPlainObject(b)) {
    const keys = Object.keys(a);
    return keys.length === Object.keys(b).length
      && keys.every(key => Object.prototype.hasOwnProperty.call(b, key) && deepEqual(a[key], b[key]));
  }
  return false;
};
