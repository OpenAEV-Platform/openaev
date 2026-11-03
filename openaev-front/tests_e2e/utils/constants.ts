// Path to the persisted Playwright storage state (cookies + localStorage).
export const AUTH_FILE = 'tests_e2e/.auth/user.json';

/** Default timeout (ms) used across E2E tests and page objects. */
export const TIMEOUT = 20_000;

/**
 * Page-load budget (ms) on WebKit. On Linux, WebKit sometimes holds a single request
 * for ~20s before sending it while the server stays fast, and the page waits on that
 * request without painting. Seen on the CI runners and reproduced in the Playwright
 * Linux image (#8507).
 */
export const WEBKIT_PAGE_LOAD_TIMEOUT = 60_000;
