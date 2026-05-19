import { test as setup } from './fixtures/baseFixtures';
import { AUTH_FILE } from './utils/constants';
import login from './utils/login';

// This a setup function.
// eslint-disable-next-line playwright/expect-expect
setup('authenticate', async ({ page }) => {
  await login(page);

  // The login endpoint is CSRF-ignored so no XSRF-TOKEN cookie exists yet.
  // Fire any POST to trigger CsrfFilter — the response (even a 403) sets
  // the XSRF-TOKEN cookie in the browser, which storageState() will persist.
  await page.evaluate(async () => {
    await fetch('/api/scenarios/search', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({}),
    }).catch(() => {});
  });

  await page.context().storageState({ path: AUTH_FILE });
});
