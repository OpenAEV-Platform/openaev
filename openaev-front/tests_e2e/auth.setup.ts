import { test as setup } from './fixtures/baseFixtures';
import { AUTH_FILE } from './utils/constants';
import login from './utils/login';
import { DEFAULT_TENANT_UUID } from './utils/url';

/**
 * The XTM Hub news feed register banner (#8938) shows up only after an asynchronous
 * registration lookup and then pushes the whole layout down: a click in flight lands
 * on a moved target. Store its dismissal, as a user who closed it would have, so no
 * suite runs under that late layout shift. Same key as NewsFeedRegisterBannerProvider
 * (useLocalStorage serialises the boolean as JSON).
 */
const NEWS_FEED_BANNER_DISMISSED_KEYS = [
  `news-feed-register-banner-dismissed-${DEFAULT_TENANT_UUID}`,
  'news-feed-register-banner-dismissed-default',
];

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

  await page.evaluate((keys) => {
    keys.forEach(key => window.localStorage.setItem(key, 'true'));
  }, NEWS_FEED_BANNER_DISMISSED_KEYS);

  await page.context().storageState({ path: AUTH_FILE });
});
