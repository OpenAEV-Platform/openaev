const canonical = (value: unknown): unknown => {
  if (value === null || value === undefined || value === '') {
    return undefined;
  }
  if (Array.isArray(value)) {
    return value.map(canonical);
  }
  if (typeof value === 'object') {
    return Object.fromEntries(Object.entries(value)
      .map(([key, item]) => [key, canonical(item)])
      .filter(([, item]) => item !== undefined)
      .sort(([a], [b]) => String(a).localeCompare(String(b))));
  }
  return value;
};

// Empty values and key order are no change.
const sameValue = (left: unknown, right: unknown) => JSON.stringify(canonical(left) ?? null) === JSON.stringify(canonical(right) ?? null);

export default sameValue;
