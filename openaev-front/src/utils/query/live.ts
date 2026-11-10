import { useMutation, type UseMutationOptions, type UseMutationResult } from '@tanstack/react-query';

import { type EntityChange, type EntityRef, type EntityType } from './entityChange';
import { type ApplyOptions } from './liveRouter';
import { useLiveRouter } from './liveRouterContext';

/**
 * Declares how a query reacts to entity changes. Build it with `liveMeta`. A query without
 * `meta.live` is never invalidated by a change, the user's own mutations included.
 */
export interface LiveMeta {
  /** Entity refs the query's data currently shows: a change on one of them invalidates the query. */
  refs?: (data: unknown) => EntityRef[];
  /**
   * Types the query lists or counts: a created or deleted entity of one of these types (an id the
   * query may not show) can enter or leave the result and change its total. An update of an entity
   * the query does not show is ignored: an off-page entity updated into a filter appears at the
   * next refetch.
   */
  collections?: string[];
  /**
   * Types any change of which (create, update or delete) can change the result, whether the query
   * shows the entity or not: a count by status, a list filtered or sorted on a field that changes.
   */
  watch?: string[];
  /** Minimum delay between two invalidations of this query (trailing). Defaults to 1000 ms. */
  throttleMs?: number;
}

declare module '@tanstack/react-query' {
  interface Register {
    queryMeta: { live?: LiveMeta };
    // The API client rejects with `{ status, ...body }` or `false`, never an Error: narrow with
    // the helpers of `httpErrors.ts`.
    defaultError: unknown;
  }
}

interface TypedLiveMeta<TData> {
  refs?: (data: TData) => EntityRef<EntityType>[];
  collections?: EntityType[];
  watch?: EntityType[];
  throttleMs?: number;
}

/**
 * Builds the `meta` of a live query: `refs` receives the query's data typed, and every type name
 * must come from `ENTITY_TYPES`, so a typo does not compile instead of silently matching nothing.
 * `TData` must be what the `queryFn` resolves to (before any `select`): this is not checked.
 */
export const liveMeta = <TData>(live: TypedLiveMeta<TData>): { live: LiveMeta } => ({
  live: {
    ...live,
    // The cache stores data untyped: the query's own data is the only value passed back here.
    refs: live.refs as LiveMeta['refs'],
  },
});

/** A change described by feature code: types come from `ENTITY_TYPES`. */
export type LiveChange = EntityChange<EntityType>;

/**
 * Describes changes made outside a mutation (e.g. an import). Pass `{ immediate: true }` to refetch
 * at once and wait for it; otherwise the changes are batched and throttled like the stream's.
 */
export const useApplyChanges = (): ((changes: LiveChange[], options?: ApplyOptions) => Promise<void>) => useLiveRouter().applyChanges;

/**
 * `useMutation` whose success feeds `changes(data, variables)` into the router, TanStack's
 * "invalidate from onSuccess" pattern: never write the cache, describe what changed. The mutation
 * stays pending until the queries showing the change are refetched, so a drawer closing on success
 * reveals fresh data. The stream echo of the same change refetches them once more, throttled.
 */
export const useLiveMutation = <TData = unknown, TError = unknown, TVariables = void, TOnMutateResult = unknown>(
  options: UseMutationOptions<TData, TError, TVariables, TOnMutateResult> & { changes: (data: TData, variables: TVariables) => LiveChange[] },
): UseMutationResult<TData, TError, TVariables, TOnMutateResult> => {
  const { changes, onSuccess, ...mutationOptions } = options;
  const applyChanges = useApplyChanges();
  return useMutation<TData, TError, TVariables, TOnMutateResult>({
    ...mutationOptions,
    onSuccess: async (data, variables, onMutateResult, context) => {
      // The server accepted the mutation: neither a faulty change description nor a failed refetch
      // may turn it into an error.
      try {
        await applyChanges(changes(data, variables), { immediate: true });
      } catch (error) {
        // eslint-disable-next-line no-console
        console.error('[live] Could not refresh after a mutation', error);
      }
      return onSuccess?.(data, variables, onMutateResult, context);
    },
  });
};
