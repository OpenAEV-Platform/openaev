import { mergeTests } from '@playwright/test';

import scenarioFixture from './scenario.fixture';
import teamFixture from './team.fixture';

export const test = mergeTests(
  teamFixture,
  scenarioFixture,
);

export { expect } from '@playwright/test';
