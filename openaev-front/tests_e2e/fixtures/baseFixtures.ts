import { type Page, test as testBase } from '@playwright/test';
import MCR from 'monocart-coverage-reports';

import coverageOptions from '../conf/mcr.config';

// fixtures
const test = testBase.extend<{ autoTestFixture: string }>({
  autoTestFixture: [async ({ context }, use) => {
    const activateCoverage = process.env.E2E_COVERAGE;
    const isChromium = test.info().project.name === 'chromium';

    const handlePageEvent = async (page: Page) => {
      await Promise.all([
        page.coverage.startJSCoverage({ resetOnNavigation: false }),
        page.coverage.startCSSCoverage({ resetOnNavigation: false }),
      ]);
    };

    // coverage API is chromium only
    if (activateCoverage && isChromium) {
      context.on('page', handlePageEvent);
    }

    await use('autoTestFixture');

    if (activateCoverage && isChromium) {
      context.off('page', handlePageEvent);
      const coverageList = await Promise.all(context.pages().map(async (page) => {
        const jsCoverage = await page.coverage.stopJSCoverage();
        const cssCoverage = await page.coverage.stopCSSCoverage();
        return [...jsCoverage, ...cssCoverage];
      }));
      const mcr = MCR(coverageOptions);
      await mcr.add(coverageList.flat());
    }
  }, {
    scope: 'test',
    auto: true,
  }],
});

// eslint-disable-next-line import/prefer-default-export
export { test };
