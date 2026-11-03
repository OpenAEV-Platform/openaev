/** Drops the null fields, so a form falls back to its defaults instead of binding null to an input. */
export const withoutNullValues = <T extends object>(values: T): Partial<T> => Object.fromEntries(
  Object.entries(values).filter(([, value]) => value !== null),
) as Partial<T>;
