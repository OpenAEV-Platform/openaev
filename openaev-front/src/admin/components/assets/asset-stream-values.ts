import { type Page } from '../../../components/common/queryable/Page';
import { type SearchPaginationInput } from '../../../utils/api-types';

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

/** The streamed fields whose value changed in the store: an unchanged one may be older than the row. */
const changedFields = (previous: Record<string, unknown> | undefined, current: Record<string, unknown> | undefined) => Object.fromEntries(
  STREAMED_ROW_FIELDS
    .filter(field => current && field in current && JSON.stringify(current[field]) !== JSON.stringify(previous?.[field]))
    .map(field => [field, current?.[field]]),
);

/**
 * Patches, in each row, the streamed fields whose value changed in the store between two reads.
 * Only a change counts: a store value older than the searched row must not overwrite it. Returns
 * the same array when no row changed, so a caller can skip the state update.
 */
export const patchRowsFromStore = <Row extends { asset_id: string }>(
  rows: Row[],
  previous: StoredEntities,
  current: StoredEntities,
): Row[] => {
  let patched = false;
  const result = rows.map((row) => {
    const fields = changedFields(previous[row.asset_id], current[row.asset_id]);
    if (Object.keys(fields).length === 0) {
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

/**
 * Wraps a search so its response keeps what the stream changed while it ran: a row read before a
 * streamed rename, and answered after it, would otherwise bring the old name back.
 */
export const keepStreamedChanges = <Row extends { asset_id: string }>(
  search: (input: SearchPaginationInput) => Promise<{ data: Page<Row> }>,
  storedEntities: () => StoredEntities,
) => (input: SearchPaginationInput) => {
  const atRequest = storedEntities();
  return search(input).then(response => ({
    ...response,
    data: {
      ...response.data,
      content: patchRowsFromStore(response.data.content, atRequest, storedEntities()),
    },
  }));
};
