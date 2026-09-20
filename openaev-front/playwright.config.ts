// imports to not let tools report them as unused
import 'monocart-coverage-reports';
import 'monocart-reporter';

import { defineConfig, devices } from '@playwright/test';

import coverageOptions from './tests_e2e/conf/mcr.config';

const isArm = process.env.CI === 'true' && process.arch === 'arm64';
const armUnsupportedTests = /.*external-injector.*|.*external-executor.*|.*external-collector.*|.*infra-chaining\.spec\.ts$/;
const globalTestIgnore = isArm ? [armUnsupportedTests] : [];
const nonInfraTestIgnore = isArm ? [/infra\/.*/, armUnsupportedTests] : [/infra\/.*/];

// Infra suites keep the shared action/assertion timeouts; only their overall
// budget is larger, because they poll for a real agent to execute (up to 240s).
const INFRA_TEST_TIMEOUT = 420_000;

/**
 * See https://playwright.dev/docs/test-configuration.
 */
export default defineConfig({
  testDir: './tests_e2e',
  testIgnore: globalTestIgnore,
  /* Run tests in files in parallel */
  fullyParallel: false,
  /* Fail the build on CI if you accidentally left test.only in the source code. */
  forbidOnly: !!process.env.CI,
  /* Every suite already polls internally via expect/toPass, so process-level
     retries mostly multiply the feedback loop on a genuine failure. */
  retries: 0,
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
    headless: process.env.CI ? true : process.env.HEADLESS === 'true',
    /* Collect trace on failure. See https://playwright.dev/docs/trace-viewer
       (not 'on-first-retry': with retries disabled that would never trigger) */
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    ignoreHTTPSErrors: true,

    /**
     * Defaults are tuned for UI-only suites so a bad locator fails fast.
     * Slower suites raise these per project (see infraUse) or per assertion.
     */
    navigationTimeout: 15_000,
    actionTimeout: 15_000,
  },
  /* Timeouts configuration 15s for assertions (e.g., expect().toBeVisible())  */
  expect: { timeout: 15_000 },
  /* Test timeout: a backstop for hangs, not a fail-fast lever (the action and
     assertion timeouts above are). Must stay above the worst-case sum of a
     test's own explicit waits - tenant creation alone can spend ~200s. */
  timeout: 300_000,
  // Configure projects for major browsers.
  // Select via CLI: yarn playwright test --project=setup --project=<browser>
  projects: [
    {
      name: 'setup',
      testMatch: /.*\.setup\.ts/,
      // Bundled Chromium: it only writes storageState, and it is the one browser
      // present on every runner and dev machine (no Chrome channel on arm64).
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'chrome',
      testIgnore: nonInfraTestIgnore,
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
    {
      name: 'chromium',
      testIgnore: nonInfraTestIgnore,
      use: {
        ...devices['Desktop Chrome'],
        storageState: 'tests_e2e/.auth/user.json',
        viewport: {
          width: 1920,
          height: 1080,
        },
      },
      dependencies: ['setup'],
    },
    {
      name: 'firefox',
      testIgnore: nonInfraTestIgnore,
      use: {
        ...devices['Desktop Firefox'],
        storageState: 'tests_e2e/.auth/user.json',
        viewport: {
          width: 1920,
          height: 1080,
        },
      },
      dependencies: ['setup'],
    },
    {
      name: 'webkit',
      testIgnore: nonInfraTestIgnore,
      use: {
        ...devices['Desktop Safari'],
        storageState: 'tests_e2e/.auth/user.json',
        viewport: {
          width: 1920,
          height: 1080,
        },
      },
      dependencies: ['setup'],
    },
    {
      name: 'edge',
      testIgnore: nonInfraTestIgnore,
      use: {
        ...devices['Desktop Edge'],
        channel: 'msedge',
        storageState: 'tests_e2e/.auth/user.json',
        viewport: {
          width: 1920,
          height: 1080,
        },
      },
      dependencies: ['setup'],
    },
    {
      name: 'infra-chrome',
      testMatch: /infra\/.*\.spec\.ts/,
      timeout: INFRA_TEST_TIMEOUT,
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
    {
      name: 'infra-chromium',
      testMatch: /infra\/.*\.spec\.ts/,
      timeout: INFRA_TEST_TIMEOUT,
      use: {
        ...devices['Desktop Chrome'],
        storageState: 'tests_e2e/.auth/user.json',
        viewport: {
          width: 1920,
          height: 1080,
        },
      },
      dependencies: ['setup'],
    },
    {
      name: 'infra-firefox',
      testMatch: /infra\/.*\.spec\.ts/,
      timeout: INFRA_TEST_TIMEOUT,
      use: {
        ...devices['Desktop Firefox'],
        storageState: 'tests_e2e/.auth/user.json',
        viewport: {
          width: 1920,
          height: 1080,
        },
      },
      dependencies: ['setup'],
    },
    {
      name: 'infra-webkit',
      testMatch: /infra\/.*\.spec\.ts/,
      timeout: INFRA_TEST_TIMEOUT,
      use: {
        ...devices['Desktop Safari'],
        storageState: 'tests_e2e/.auth/user.json',
        viewport: {
          width: 1920,
          height: 1080,
        },
      },
      dependencies: ['setup'],
    },
    {
      name: 'infra-edge',
      testMatch: /infra\/.*\.spec\.ts/,
      timeout: INFRA_TEST_TIMEOUT,
      use: {
        ...devices['Desktop Edge'],
        channel: 'msedge',
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
