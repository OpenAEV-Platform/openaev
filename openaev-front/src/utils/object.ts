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
