import GroupApiHelpers from './api-helpers/GroupApiHelpers';
import UserApiHelpers from './api-helpers/UserApiHelpers';
import { test as setup } from './fixtures/baseFixtures';
import { fetchCsrfCookie, loginWithApi, newRequestContextAs } from './utils/auth';
import { AUTH_ADMIN } from './utils/constants';
import login from './utils/login';
import { PERSONAS } from './utils/personas';
import { DEFAULT_TENANT_UUID } from './utils/url';

/**
 * The XTM Hub news feed register banner (#8938) shows up only after an asynchronous
 * registration lookup and then pushes the whole layout down: a click in flight lands
 * on a moved target. Store its dismissal, as a user who closed it would have, so no
 * suite runs under that late layout shift. Same key as NewsFeedRegisterBannerProvider
 * (useLocalStorage serialises the boolean as JSON).
 */
const NEWS_FEED_BANNER_DISMISSED = [
  `news-feed-register-banner-dismissed-${DEFAULT_TENANT_UUID}`,
  'news-feed-register-banner-dismissed-default',
];

/**
 * The admin shell sends a browser to Getting Started on its first visit, then stores
 * `false` under this key. The admin goes through that visit at login; a persona logged
 * in through the API has not, so its first `goto` would land on Getting Started. Same
 * key as GETTING_STARTED_LOCAL_STORAGE_KEY.
 */
const GETTING_STARTED_SEEN = {
  name: 'go-to-getting-started',
  value: 'false',
};

const LOCAL_STORAGE_INIT = [
  ...NEWS_FEED_BANNER_DISMISSED.map(name => ({
    name,
    value: 'true',
  })),
  GETTING_STARTED_SEEN,
];

// Serial: the manager and observer are provisioned with the admin session saved by the first step.
setup.describe.configure({ mode: 'serial' });

// These are setup functions.
// eslint-disable-next-line playwright/expect-expect
setup('authenticate admin', async ({ page }) => {
  // The admin goes through the login form: the only place it is exercised.
  await login(page);
  // page.request shares the browser context cookies, so the XSRF-TOKEN lands there.
  await fetchCsrfCookie(page.request);

  await page.evaluate((entries) => {
    entries.forEach(({ name, value }) => window.localStorage.setItem(name, value));
  }, LOCAL_STORAGE_INIT);

  await page.context().storageState({ path: AUTH_ADMIN });
});

for (const persona of ['manager', 'observer'] as const) {
  // eslint-disable-next-line playwright/expect-expect
  setup(`authenticate ${persona}`, async ({ playwright }) => {
    const { email, password, firstname, groupName } = PERSONAS[persona];
    const admin = await newRequestContextAs(playwright, 'admin');
    try {
      const user = await new UserApiHelpers(admin).createOrAttachUser(email, password, firstname);
      await new GroupApiHelpers(admin).addUserToGroup(groupName, user.user_id);
    } finally {
      await admin.dispose();
    }
    await loginWithApi(playwright, persona, LOCAL_STORAGE_INIT);
  });
}
