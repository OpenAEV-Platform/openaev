type Comparator<T> = (a: T, b: T) => number;

const compare = (a: unknown, b: unknown): number => {
  if ((a as number) < (b as number)) return -1;
  if ((a as number) > (b as number)) return 1;
  return 0;
};

// Same ordering as Ramda's ascend and descend: values are compared with `<` and `>` (not `localeCompare`),
// and a missing value compares equal to every other value.
export const ascendBy = <T>(key: keyof T): Comparator<T> => (a, b) => compare(a?.[key], b?.[key]);

export const descendBy = <T>(key: keyof T): Comparator<T> => (a, b) => compare(b?.[key], a?.[key]);
