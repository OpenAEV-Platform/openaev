import { QueryCache, QueryClient } from '@tanstack/react-query';

import { type Error as ApiError, notifyErrorHandler } from '../error/errorHandlerUtil';
import { httpStatus, isNotFoundError } from './httpErrors';

const MAX_RETRIES = 3;
const RETRIED_CLIENT_ERRORS = [408, 429];

/**
 * TanStack's retry (3 retries, exponential backoff), except for a 4xx: the same request would get
 * the same answer. A timeout (408) and a rate limit (429) are not a refusal of the request itself,
 * so they are retried.
 */
export const shouldRetry = (failureCount: number, error: unknown): boolean => {
  if (failureCount >= MAX_RETRIES) return false;
  const status = httpStatus(error);
  return status === undefined || status < 400 || status >= 500 || RETRIED_CLIENT_ERRORS.includes(status);
};

/**
 * Query functions call the API without their own error toast. One toast is shown here per failure
 * streak of a query, whether it has data or not (a failed background refetch leaves stale data on
 * screen), and not again until it succeeds. A 404 is a state the page renders (deleted entity), a
 * 401 is handled by the logout flow.
 */
const createQueryCache = () => {
  const notified = new WeakSet<object>();
  return new QueryCache({
    onError: (error, query) => {
      if (isNotFoundError(error) || httpStatus(error) === 401 || notified.has(query)) return;
      notified.add(query);
      notifyErrorHandler(error as ApiError);
    },
    onSuccess: (_data, query) => {
      notified.delete(query);
    },
  });
};

/** TanStack's defaults (stale at once, refetch on mount, focus and reconnect), plus the error policy above. */
export const createAppQueryClient = (): QueryClient => new QueryClient({
  queryCache: createQueryCache(),
  defaultOptions: { queries: { retry: shouldRetry } },
});
