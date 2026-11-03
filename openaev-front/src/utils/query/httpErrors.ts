/**
 * The API client (`network.ts`) rejects with a plain `{ status, ...body }` object when the server
 * answered, and with `false` on a network failure: it never rejects with an AxiosError. The status
 * of the answer, if any.
 */
export const httpStatus = (error: unknown): number | undefined => {
  if (typeof error !== 'object' || error === null) return undefined;
  const { status } = error as { status?: unknown };
  return typeof status === 'number' ? status : undefined;
};

/** Whether a query error is a 404 (e.g. the entity was deleted): an expected state, not a failure. */
export const isNotFoundError = (error: unknown): boolean => httpStatus(error) === 404;

/**
 * Whether the server refused the request itself (4xx: deleted, forbidden...): retrying, or keeping
 * the previous data on screen, would show what the user may no longer see.
 */
export const isClientError = (error: unknown): boolean => {
  const status = httpStatus(error);
  return status !== undefined && status >= 400 && status < 500;
};
