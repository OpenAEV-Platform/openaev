/**
 * Playwright configuration for nightly CI runs.
 *
 * Extends the base configuration with all desktop browser projects.
 * Used by the nightly-ci workflow via:
 *   yarn playwright test --config playwright.nightly.config.ts --project=<browser>
 */
// eslint-disable-next-line import/no-extraneous-dependencies
import { defineConfig, devices } from '@playwright/test';

import baseConfig from './playwright.config';

export default defineConfig({
  ...baseConfig,
  projects: [
    {
      name: 'setup',
      testMatch: /.*\.setup\.ts/,
    },
    {
      name: 'chromium',
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
      name: 'chrome',
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
      name: 'edge',
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
