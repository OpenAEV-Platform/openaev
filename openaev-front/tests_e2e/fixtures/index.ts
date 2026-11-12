import { mergeTests } from '@playwright/test';

import authFixture from './auth.fixture';
import credentialFixture from './credential.fixture';
import scenarioFixture from './scenario.fixture';
import teamFixture from './team.fixture';

export const test = mergeTests(
  authFixture,
  teamFixture,
  scenarioFixture,
  credentialFixture,
);
