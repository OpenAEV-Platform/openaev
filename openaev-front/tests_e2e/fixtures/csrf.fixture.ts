import * as fs from 'node:fs';

import { test as base } from '@playwright/test';

import { AUTH_FILE } from '../utils/constants';

/**
 * Reads the XSRF-TOKEN cookie value from the stored auth state.
 * auth.setup.ts ensures this cookie is present after login.
 */
const readCsrfToken = (): string | null => {
  try {
    const state = JSON.parse(fs.readFileSync(AUTH_FILE, 'utf-8'));
    const cookie = (state.cookies ?? []).find(
      (c: {
        name: string;
        value: string;
      }) => c.name === 'XSRF-TOKEN',
    );
    return cookie ? decodeURIComponent(cookie.value) : null;
  } catch {
    return null;
  }
};

/**
 * Overrides the built-in `request` fixture so that every request
 * automatically includes the X-XSRF-TOKEN header.
 *
 * Playwright sends the XSRF-TOKEN *cookie* automatically (via storageState),
 * but the double-submit pattern also requires the matching *header*.
 */
const csrfFixture = base.extend({
  request: async ({ playwright }, use) => {
    const token = readCsrfToken();
    const context = await playwright.request.newContext({
      storageState: AUTH_FILE,
      extraHTTPHeaders: token ? { 'X-XSRF-TOKEN': token } : {},
    });

    await use(context);
    await context.dispose();
  },
});

export default csrfFixture;
