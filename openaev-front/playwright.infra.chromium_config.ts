/**
 * Playwright configuration for infra E2E tests.
 *
 * Runs tests under tests_e2e/tests/infra/ that require CI infrastructure.
 * Used by the quality-gates workflow (x64 and arm64 runners) via:
 *   yarn playwright test --config playwright.infra.chromium_config.ts
 */
// eslint-disable-next-line import/no-extraneous-dependencies
import { defineConfig, devices } from '@playwright/test';

import baseConfig from './playwright.config';

export default defineConfig({
  ...baseConfig,
  // Infra specs install a real agent and launch atomic tests that take several
  // minutes each. The base config retries twice on CI, which - combined with the
  // beforeAll agent reinstall - can push this job past its 30 min budget. One
  // retry still absorbs a single flake while keeping the worst case bounded.
  retries: process.env.CI ? 1 : 0,
  projects: [
    {
      name: 'setup',
      testMatch: /.*\.setup\.ts/,
      use: { ...devices['Desktop Chromium'] },
    },
    {
      name: 'Chromium',
      testMatch: /infra\/.*\.spec\.ts/,
      use: {
        ...devices['Desktop Chromium'],
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
