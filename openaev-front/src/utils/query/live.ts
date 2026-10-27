import { type DefaultError, useMutation, type UseMutationOptions, type UseMutationResult } from '@tanstack/react-query';

import { type EntityChange, type EntityRef } from '../stream/entityChange';
import { type StreamType } from '../stream/streamTypes';
import { type ApplyOptions } from './liveRouter';
import { useLiveRouter } from './liveRouterContext';

/**
 * Declares how a query reacts to entity changes. Set it as `meta: { live: {...} }` in `queryOptions`.
 * A query without `meta.live` is never invalidated by the stream.
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
  /** Minimum delay between two invalidations of this query (trailing). Defaults to 1000 ms. */
  throttleMs?: number;
}

declare module '@tanstack/react-query' {
  interface Register { queryMeta: { live?: LiveMeta } }
}

interface TypedLiveMeta<TData> {
  refs?: (data: TData) => {
    type: StreamType;
    id: string;
  }[];
  collections?: StreamType[];
  throttleMs?: number;
}

/**
 * Builds the `meta` of a live query: `refs` receives the query's data typed, and every type name
 * must come from `STREAM_TYPES`, so a typo does not compile instead of silently matching nothing.
 */
export const liveMeta = <TData>(live: TypedLiveMeta<TData>): { live: LiveMeta } => ({
  live: {
    ...live,
    // The cache stores data untyped: the query's own data is the only value passed back here.
    refs: live.refs as LiveMeta['refs'],
  },
});

/** The only way to refresh the cache: every change goes through the same router as the stream. */
export const useApplyChanges = (): ((changes: EntityChange[], options?: ApplyOptions) => Promise<void>) => useLiveRouter().applyChanges;

/**
 * `useMutation` whose success feeds `changes(data, variables)` into the router. Never touch the
 * query cache from `onSuccess`: describe what changed instead. The mutation stays pending until the
 * queries showing the change are refetched, so a drawer closing on success reveals fresh data. A
 * query already fetching at that moment is refetched once its fetch settles, without holding the
 * mutation. The `mutationFn` keeps the default error toast of its action.
 */
export const useLiveMutation = <TData = unknown, TVariables = void, TError = DefaultError>(
  options: UseMutationOptions<TData, TError, TVariables> & { changes: (data: TData, variables: TVariables) => EntityChange[] },
): UseMutationResult<TData, TError, TVariables> => {
  const { changes, onSuccess, ...mutationOptions } = options;
  const applyChanges = useApplyChanges();
  return useMutation<TData, TError, TVariables>({
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
