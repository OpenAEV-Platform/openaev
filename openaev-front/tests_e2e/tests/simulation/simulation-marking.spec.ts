import { type APIRequestContext, expect } from '@playwright/test';

import { type Exercise, type Grant, type InjectOutput } from '../../../src/utils/api-types';
import GroupApiHelpers from '../../api-helpers/GroupApiHelpers';
import InjectApiHelpers from '../../api-helpers/InjectApiHelpers';
import InjectorContractApiHelpers from '../../api-helpers/InjectorContractApiHelpers';
import SimulationApiHelpers from '../../api-helpers/SimulationApiHelpers';
import { test } from '../../fixtures';
import SimulationPage from '../../model/simulation/SimulationPage';
import { disableAnimations } from '../../utils/animations';
import { newRequestContextAs } from '../../utils/auth';
import {
  CLEARANCE_CASES,
  createMarkedAssets,
  deleteMarkedAssets,
  type MarkedAssetKey,
  type MarkedAssets,
  visibleAssetsOf,
} from '../../utils/markedAssets';
import { PERSONAS } from '../../utils/personas';

// The simulation is launched by the observer, so the assets its clearance does not cover are left out of the
// execution: each persona sees an "Asset not targeted" trace on those of them it can see.
const LAUNCHER = 'observer';
const launcherAssets = visibleAssetsOf(LAUNCHER);

test.describe('Simulation - With asset marked', () => {
  // Seeded once as admin for every persona: a read-only persona cannot create them, and the data does not
  // depend on who reads it. Playwright reruns these hooks in a new worker after a failure.
  let adminRequest: APIRequestContext;
  let assets: MarkedAssets;
  let simulation: Exercise;
  let inject: InjectOutput;
  let contractId: string;
  let launcherGrant: Grant;

  test.beforeAll(async ({ playwright, getMarkingDefinitionId }) => {
    adminRequest = await newRequestContextAs(playwright, 'admin');
    const simulationApiHelpers = new SimulationApiHelpers(adminRequest);
    const contractApiHelpers = new InjectorContractApiHelpers(adminRequest);
    const suffix = `${Date.now()}-${Math.random()}`;

    assets = await createMarkedAssets(adminRequest, getMarkingDefinitionId, suffix);
    simulation = await simulationApiHelpers.createSimulation(`Simulation marking ${suffix}`);
    // Custom contract on the built-in manual injector, declaring an asset field so the inject lists the targets.
    const contract = await contractApiHelpers.createContract(`E2E contract ${suffix}`, undefined, true);
    contractId = contract.contractId;
    inject = await simulationApiHelpers.createInject(simulation.exercise_id, {
      inject_title: `Inject ${suffix}`,
      inject_injector_contract: contract.contractId,
      inject_injector: contract.injectorId,
      // A detection expectation is created on each asset the inject runs on, and on no other: the manual injector
      // leaves no other per-asset trace of a run. Agentless and without a collector, it fails at once.
      inject_content: {
        expectations: [{
          expectation_type: 'DETECTION',
          expectation_name: 'Detection',
          expectation_score: 100,
          expectation_expectation_group: false,
          expectation_expiration_time: 3600,
        }],
      },
      inject_assets: Object.values(assets).map(asset => asset.asset_id),
      // Required on a simulation inject (its offset from the simulation start), unlike an atomic testing.
      inject_depends_duration: 0,
    });
    // Started by the observer (TLP:GREEN clearance): the Observer role has no LAUNCH_ASSESSMENT, so its group
    // gets a LAUNCHER grant on this simulation only.
    launcherGrant = await new GroupApiHelpers(adminRequest).addGrant(PERSONAS[LAUNCHER].groupName, {
      grant_name: 'LAUNCHER',
      grant_resource: simulation.exercise_id,
      grant_resource_type: 'SIMULATION',
    });
    const launcherRequest = await newRequestContextAs(playwright, LAUNCHER);
    try {
      await new SimulationApiHelpers(launcherRequest).startSimulation(simulation.exercise_id);
    } finally {
      await launcherRequest.dispose();
    }

    // InjectsExecutionJob fires once a minute, on the minute: the inject runs within the next tick.
    const injectApiHelpers = new InjectApiHelpers(adminRequest);
    await expect.poll(
      async () => (await injectApiHelpers.getInject(inject.inject_id)).inject_status?.status_name,
      {
        message: 'the simulation inject should be executed',
        intervals: [5_000],
        timeout: 90_000,
      },
    ).toBe('EXECUTED');
  });

  test.afterAll(async () => {
    if (!adminRequest) {
      return;
    }
    if (launcherGrant) {
      await new GroupApiHelpers(adminRequest).deleteGrant(launcherGrant);
    }
    // Deleting the simulation deletes its inject, which releases the contract and the assets.
    if (simulation) {
      await new SimulationApiHelpers(adminRequest).deleteSimulation(simulation.exercise_id);
    }
    if (contractId) {
      await new InjectorContractApiHelpers(adminRequest).deleteContract(contractId);
    }
    if (assets) {
      await deleteMarkedAssets(adminRequest, assets);
    }
    await adminRequest.dispose();
  });

  test('should run the inject only on the assets cleared for the launcher', async () => {
    // Act
    const expectations = await new SimulationApiHelpers(adminRequest).getExpectations(simulation.exercise_id);

    // Assert
    const targetedAssetIds = expectations
      .filter(expectation => expectation.inject_expectation_inject === inject.inject_id)
      .map(expectation => expectation.inject_expectation_asset);
    expect(targetedAssetIds.toSorted()).toEqual(launcherAssets.map(key => assets[key].asset_id).toSorted());
  });

  for (const { persona, visibleAssets, invisibleAssets } of CLEARANCE_CASES) {
    const notTargetedAssets = visibleAssets.filter(key => !launcherAssets.includes(key));

    test.describe(() => {
      let simulationPage: SimulationPage;

      test.use({ persona });

      test.beforeEach(async ({ page }) => {
        simulationPage = new SimulationPage(page);
        await disableAnimations(page);
        await simulationPage.gotoInject(simulation.exercise_id, inject.inject_id);
      });

      test(`${persona} should list only the inject targets its clearance covers`, async () => {
        // Assert
        await Promise.all(visibleAssets.map(key =>
          expect(simulationPage.getAssetByName(assets[key].asset_name)).toBeVisible()));
        // Runs after the visible checks, so the list has rendered and an absence is meaningful.
        await Promise.all(invisibleAssets.map(key =>
          expect(simulationPage.getAssetByName(assets[key].asset_name)).toHaveCount(0, { timeout: 1_000 })));
      });

      test(`${persona} should see which targets the launcher clearance left out`, async () => {
        // Arrange
        const greenAsset = simulationPage.getAssetByName(assets.green.asset_name);
        const notTargetedTrace = simulationPage.endpointTracesContainer.filter({ hasText: 'Asset not targeted' });

        // Assert
        // Cleared for the launcher: the green asset carries no "Asset not targeted" trace (nor any trace of its own
        // on the manual injector). That it ran is checked on its expectation, in the test above.
        await greenAsset.click();
        await expect(simulationPage.noTracesMessage).toBeVisible();
        for (const key of notTargetedAssets) {
          // Selecting an asset replaces the traces shown: each check starts from the green asset's empty panel,
          // so the message comes from this asset, not from the previous one.
          // eslint-disable-next-line no-await-in-loop -- sequential UI steps, see above
          await simulationPage.getAssetByName(assets[key].asset_name).click();
          // eslint-disable-next-line no-await-in-loop -- sequential UI steps, see above
          await expect(notTargetedTrace).toBeVisible();
          // eslint-disable-next-line no-await-in-loop -- sequential UI steps, see above
          await greenAsset.click();
          // eslint-disable-next-line no-await-in-loop -- sequential UI steps, see above
          await expect(simulationPage.noTracesMessage).toBeVisible();
        }
      });

      test(`${persona} should only get the execution traces of the assets it is cleared for`, async ({ playwright }) => {
        // Known gap: GET /injects/execution-traces checks the inject, not the asset clearance, so a persona gets the
        // traces of an asset it cannot see. Only those personas fail; remove once the endpoint checks it.
        test.fail(invisibleAssets.length > 0, 'The execution traces endpoint does not check the asset marking clearance');

        // Arrange
        const request = await newRequestContextAs(playwright, persona);
        const injectApiHelpers = new InjectApiHelpers(request);
        const getTraces = (key: MarkedAssetKey) => injectApiHelpers.getAssetExecutionTraces(inject.inject_id, assets[key].asset_id);

        try {
          // Act
          const [green, cleared, uncleared] = await Promise.all([
            getTraces('green'),
            Promise.all(notTargetedAssets.map(getTraces)),
            Promise.all(invisibleAssets.map(getTraces)),
          ]);

          // Assert
          // Every persona is cleared for the green asset: a 200 on it proves the request works, so an empty answer
          // for an asset the persona cannot see is a refusal, not a broken request.
          expect(green.status).toBe(200);
          // Each not-targeted asset the persona can see has an "Asset not targeted" trace.
          cleared.forEach(({ traces }) =>
            expect(traces.map(trace => trace.execution_status)).toContain('ASSET_CLEARANCE_RESTRICTED'));
          uncleared.forEach(({ traces }) => expect(traces).toEqual([]));
        } finally {
          await request.dispose();
        }
      });
    });
  }
});
