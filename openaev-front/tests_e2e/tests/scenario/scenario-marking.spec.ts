import { type APIRequestContext, expect } from '@playwright/test';

import { type Endpoint, type InjectOutput, type Scenario } from '../../../src/utils/api-types';
import EndpointsApiHelpers from '../../api-helpers/EndpointsApiHelpers';
import InjectorContractApiHelpers from '../../api-helpers/InjectorContractApiHelpers';
import ScenarioApiHelpers from '../../api-helpers/ScenarioApiHelpers';
import { test } from '../../fixtures';
import ScenarioPage from '../../model/scenario/ScenarioPage';
import { disableAnimations } from '../../utils/animations';
import { newRequestContextAs } from '../../utils/auth';
import { type Persona } from '../../utils/personas';

type AssetKey = 'green' | 'amber' | 'red';

// Default group clearances (PresetTenantData.DEFAULT_GROUP_MARKINGS): Admin TLP:RED, Manager TLP:AMBER,
// Observer TLP:GREEN. A clearance also covers every lower marking of the same type.
const CASES: {
  persona: Persona;
  visibleAssets: AssetKey[];
  invisibleAssets: AssetKey[];
}[] = [
  {
    persona: 'admin',
    visibleAssets: ['green', 'amber', 'red'],
    invisibleAssets: [],
  },
  {
    persona: 'manager',
    visibleAssets: ['green', 'amber'],
    invisibleAssets: ['red'],
  },
  {
    persona: 'observer',
    visibleAssets: ['green'],
    invisibleAssets: ['amber', 'red'],
  },
];

test.describe('Scenario - With asset marked', () => {
  // Seeded once as admin for every persona: a read-only persona cannot create them, and the data does not
  // depend on who reads it. Playwright reruns these hooks in a new worker after a failure.
  let adminRequest: APIRequestContext;
  let assets: Record<AssetKey, Endpoint>;
  let scenarioPage: ScenarioPage;
  let scenario: Scenario;
  let inject: InjectOutput;
  let contractId: string;

  test.beforeAll(async ({ playwright, getMarkingDefinitionId }) => {
    adminRequest = await newRequestContextAs(playwright, 'admin');
    const endpointApiHelpers = new EndpointsApiHelpers(adminRequest);
    const scenarioApiHelpers = new ScenarioApiHelpers(adminRequest);
    const contractApiHelpers = new InjectorContractApiHelpers(adminRequest);
    const suffix = `${Date.now()}-${Math.random()}`;

    const [green, amber, red] = await Promise.all([
      endpointApiHelpers.createEndpoint(`Asset Green ${suffix}`, [getMarkingDefinitionId('TLP:GREEN')]),
      endpointApiHelpers.createEndpoint(`Asset Amber ${suffix}`, [getMarkingDefinitionId('TLP:AMBER')]),
      endpointApiHelpers.createEndpoint(`Asset Red ${suffix}`, [getMarkingDefinitionId('TLP:RED')]),
    ]);
    assets = {
      green,
      amber,
      red,
    };
    scenario = await scenarioApiHelpers.createScenario(`Scenario marking ${suffix}`);
    // Custom contract on the built-in manual injector, declaring an asset field so the inject form lists the targets.
    const contract = await contractApiHelpers.createContract(`E2E contract ${suffix}`, undefined, true);
    contractId = contract.contractId;
    inject = await scenarioApiHelpers.createInject(scenario.scenario_id, {
      inject_title: `Inject ${suffix}`,
      inject_injector_contract: contract.contractId,
      inject_injector: contract.injectorId,
      inject_content: {},
      inject_assets: Object.values(assets).map(asset => asset.asset_id),
      // Required on a scenario inject (its offset from the scenario start), unlike an atomic testing.
      inject_depends_duration: 0,
    });
  });

  test.beforeEach(async ({ page }) => {
    scenarioPage = new ScenarioPage(page);
    await disableAnimations(page);
    await scenarioPage.gotoInjects(scenario.scenario_id);
    await scenarioPage.openInject(inject.inject_title);
  });

  test.afterAll(async () => {
    if (!adminRequest) {
      return;
    }
    // Deleting the scenario deletes its inject, which releases the contract and the assets.
    if (scenario) {
      await new ScenarioApiHelpers(adminRequest).deleteScenario(scenario.scenario_id);
    }
    if (contractId) {
      await new InjectorContractApiHelpers(adminRequest).deleteContract(contractId);
    }
    if (assets) {
      const endpointApiHelpers = new EndpointsApiHelpers(adminRequest);
      await Promise.all(Object.values(assets).map(asset => endpointApiHelpers.deleteEndpoint(asset.asset_id)));
    }
    await adminRequest.dispose();
  });

  for (const { persona, visibleAssets, invisibleAssets } of CASES) {
    test.describe(() => {
      test.use({ persona });

      test(`${persona} should list the inject targets corresponding with the correct marking onto the scenario`, async () => {
        // Assert
        await Promise.all(visibleAssets.map(key =>
          expect(scenarioPage.getInjectTargetAsset(assets[key].asset_name)).toBeVisible()));
        // Runs after the visible checks, so the list has rendered and an absence is meaningful.
        await Promise.all(invisibleAssets.map(key =>
          expect(scenarioPage.getInjectTargetAsset(assets[key].asset_name)).toHaveCount(0, { timeout: 1_000 })));
      });
    });
  }
});
