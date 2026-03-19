import { test as setup } from './fixtures/baseFixtures';
import login from './utils/login';

const authFile = 'tests_e2e/.auth/user.json';

// This a setup function.
// eslint-disable-next-line playwright/expect-expect
setup('authenticate', async ({ page }) => {
  await login(page);

  await page.context().storageState({ path: authFile });
});
