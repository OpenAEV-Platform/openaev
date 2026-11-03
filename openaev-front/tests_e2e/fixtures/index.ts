import { mergeTests } from '@playwright/test';

import credentialFixture from './credential.fixture';
import csrfFixture from './csrf.fixture';
import scenarioFixture from './scenario.fixture';
import teamFixture from './team.fixture';

export const test = mergeTests(
  csrfFixture,
  teamFixture,
  scenarioFixture,
  credentialFixture,
);

export { expect } from '@playwright/test';
