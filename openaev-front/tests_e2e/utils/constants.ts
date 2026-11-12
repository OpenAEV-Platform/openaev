import { authFile } from './auth';

export const AUTH_ADMIN = authFile('admin');
export const AUTH_MANAGER = authFile('manager');
export const AUTH_OBSERVER = authFile('observer');

/** Default timeout (ms) used across E2E tests and page objects. */
export const TIMEOUT = 20_000;

/**
 * Page-load budget (ms) on WebKit. On Linux, WebKit sometimes holds a single request
 * for ~20s before sending it while the server stays fast, and the page waits on that
 * request without painting. Seen on the CI runners and reproduced in the Playwright
 * Linux image (#8507).
 */
export const WEBKIT_PAGE_LOAD_TIMEOUT = 60_000;
