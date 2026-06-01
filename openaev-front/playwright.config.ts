// imports to not let tools report them as unused
import 'monocart-coverage-reports';
import 'monocart-reporter';

import { defineConfig, devices } from '@playwright/test';

import coverageOptions from './tests_e2e/conf/mcr.config';

/**
 * See https://playwright.dev/docs/test-configuration.
 */
export default defineConfig({
  testDir: './tests_e2e',
  /* Run tests in files in parallel */
  fullyParallel: false,
  /* Fail the build on CI if you accidentally left test.only in the source code. */
  forbidOnly: !!process.env.CI,
  /* Retry on CI only */
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  /* Reporter to use. See https://playwright.dev/docs/test-reporters */
  reporter: [
    ['list'],
    ['monocart-reporter', {
      name: `OpenAEV Report`,
      outputFile: './test-results/report.html',
      // global coverage report options
      coverage: coverageOptions,
      /*
      onEnd: async (reportData) => {
        // teams integration with webhook
        await teamsWebhook(reportData);
      } */
    }],
  ],
  /* Shared settings for all the projects below. See https://playwright.dev/docs/api/class-testoptions. */
  use: {
    locale: 'en-US',
    /* Base URL to use in actions like `await page.goto('/')`. */
    baseURL: process.env.APP_URL ?? 'http://localhost:3001',

    /* Collect trace when retrying the failed test. See https://playwright.dev/docs/trace-viewer */
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    ignoreHTTPSErrors: true,

    /**
     * Timeouts for specific actions:
     * - Navigation: 30s (page.goto, page.reload)
     * - Action timeout: 60s (click, fill, etc.)
     */
    navigationTimeout: 30000,
    actionTimeout: 60000,
  },
  /* Timeouts configuration 60s for assertions (e.g., expect().toBeVisible())  */
  expect: { timeout: 60000 },
  /* Test timeout: 300s (5 min) for long-running scenario tests */
  timeout: 300000,
  /* Configure projects for major browsers */
  projects: [
    {
      name: 'setup',
      testMatch: /.*\.setup\.ts/,
    },
    {
      name: 'Google Chrome',
      use: {
        ...devices['Desktop Chrome'],
        channel: 'chrome',
        storageState: 'tests_e2e/.auth/user.json',
        viewport: {
          width: 1920,
          height: 1080,
        },
      },
      dependencies: ['setup'],
    },
  ],
});
