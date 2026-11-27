import { type APIRequestContext, expect } from '@playwright/test';

import { type InjectOutput, type Scenario } from '../../../src/utils/api-types';
import InjectorContractApiHelpers from '../../api-helpers/InjectorContractApiHelpers';
import ScenarioApiHelpers from '../../api-helpers/ScenarioApiHelpers';
import { test } from '../../fixtures';
import ScenarioPage from '../../model/scenario/ScenarioPage';
import { disableAnimations } from '../../utils/animations';
import { newRequestContextAs } from '../../utils/auth';
import { CLEARANCE_CASES, createMarkedAssets, deleteMarkedAssets, type MarkedAssets } from '../../utils/markedAssets';

test.describe('Scenario - With asset marked', () => {
  // Seeded once as admin for every persona: a read-only persona cannot create them, and the data does not
  // depend on who reads it. Playwright reruns these hooks in a new worker after a failure.
  let adminRequest: APIRequestContext;
  let assets: MarkedAssets;
  let scenarioPage: ScenarioPage;
  let scenario: Scenario;
  let inject: InjectOutput;
  let contractId: string;

  test.beforeAll(async ({ playwright, getMarkingDefinitionId }) => {
    adminRequest = await newRequestContextAs(playwright, 'admin');
    const scenarioApiHelpers = new ScenarioApiHelpers(adminRequest);
    const contractApiHelpers = new InjectorContractApiHelpers(adminRequest);
    const suffix = `${Date.now()}-${Math.random()}`;

    assets = await createMarkedAssets(adminRequest, getMarkingDefinitionId, suffix);
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
      await deleteMarkedAssets(adminRequest, assets);
    }
    await adminRequest.dispose();
  });

  for (const { persona, visibleAssets, invisibleAssets } of CLEARANCE_CASES) {
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
