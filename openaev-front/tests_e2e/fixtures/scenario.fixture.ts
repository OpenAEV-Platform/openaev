import { test as base } from '@playwright/test';

import { type Scenario } from '../../src/utils/api-types';
import ScenarioApiHelpers from '../api-helpers/ScenarioApiHelpers';

type ScenarioFixtures = { emptyScenario: Scenario };

const scenarioFixture = base.extend<ScenarioFixtures>({

  emptyScenario: async ({ request }, use) => {
    const scenarioApiHelpers = new ScenarioApiHelpers(request);
    const scenario = await scenarioApiHelpers.createScenario();

    await use(scenario);

    await scenarioApiHelpers.deleteScenario(scenario.scenario_id);
  },
});

export default scenarioFixture;
