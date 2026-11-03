// The store holds what the stream sends: the JPA entity, with every empty field as null and nested
// objects (agents) shaped differently from the REST outputs the screens are typed on.

type StoredEntities = Record<string, Record<string, unknown> | undefined>;

// Fields of a list row that the stream carries in the same shape as the search output. The others
// (agents, …) stay as searched.
const STREAMED_ROW_FIELDS = ['asset_name', 'asset_category', 'asset_criticality', 'endpoint_platform', 'asset_tags', 'asset_markings'];

/** Drops the null fields, so a form falls back to its defaults instead of binding null to an input. */
export const withoutNullValues = <T extends object>(values: T): Partial<T> => Object.fromEntries(
  Object.entries(values).filter(([, value]) => value !== null),
) as Partial<T>;

const streamedFields = (entity: Record<string, unknown> | undefined) => Object.fromEntries(
  STREAMED_ROW_FIELDS.filter(field => entity && field in entity).map(field => [field, entity?.[field]]),
);

/**
 * Patches the rows whose entity changed in the store between two renders. Only a change counts: a
 * store copy older than the searched row must not overwrite it. Returns the same array when no row
 * changed, so a caller can skip the state update.
 */
export const patchRowsFromStore = <Row extends { asset_id: string }>(
  rows: Row[],
  previous: StoredEntities,
  current: StoredEntities,
): Row[] => {
  let patched = false;
  const result = rows.map((row) => {
    const fields = streamedFields(current[row.asset_id]);
    if (!current[row.asset_id] || JSON.stringify(fields) === JSON.stringify(streamedFields(previous[row.asset_id]))) {
      return row;
    }
    patched = true;
    return {
      ...row,
      ...fields,
    };
  });
  return patched ? result : rows;
};
