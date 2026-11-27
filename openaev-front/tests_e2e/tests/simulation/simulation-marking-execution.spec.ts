import { type APIRequestContext, expect } from '@playwright/test';

import { type Exercise, type InjectOutput } from '../../../src/utils/api-types';
import EndpointsApiHelpers from '../../api-helpers/EndpointsApiHelpers';
import FakeAgentApiHelpers, { type FakeAgent } from '../../api-helpers/FakeAgentApiHelpers';
import InjectApiHelpers from '../../api-helpers/InjectApiHelpers';
import PayloadApiHelpers from '../../api-helpers/PayloadApiHelpers';
import SimulationApiHelpers from '../../api-helpers/SimulationApiHelpers';
import { test } from '../../fixtures';
import { newRequestContextAs } from '../../utils/auth';
import { type MarkedAssetKey, MARKINGS, visibleAssetsOf } from '../../utils/markedAssets';

// Launched by the manager (TLP:AMBER), whose role can launch: the marking alone decides the targets, no grant.
// The red agent is left out of the execution with a trace that is not counted, so the status only depends on the
// results of the green and amber agents.
const LAUNCHER = 'manager';
const launcherAssets = visibleAssetsOf(LAUNCHER);

// The personas of CLEARANCE_CASES: admin TLP:RED, manager TLP:AMBER, observer TLP:GREEN.
const READERS = ['admin', 'manager', 'observer'] as const;
type Reader = typeof READERS[number];

type AgentResult = 'EXECUTED' | 'ERROR';

// One inject per outcome, all in the same simulation: a single scheduler tick runs them.
// `results` are the COMPLETE traces the agents the launcher is cleared for send. `expectedStatus` is the status each
// persona reads: recomputed from the agents its clearance covers, NOT_VISIBLE when it covers none of them.
const OUTCOMES: {
  name: string;
  targets: MarkedAssetKey[];
  results: Partial<Record<MarkedAssetKey, AgentResult>>;
  expectedStatus: Record<Reader, string>;
}[] = [
  {
    name: 'all agents succeed',
    targets: ['green', 'amber', 'red'],
    results: {
      green: 'EXECUTED',
      amber: 'EXECUTED',
    },
    expectedStatus: {
      admin: 'EXECUTED',
      manager: 'EXECUTED',
      observer: 'EXECUTED',
    },
  },
  {
    name: 'the amber agent fails',
    targets: ['green', 'amber', 'red'],
    results: {
      green: 'EXECUTED',
      amber: 'ERROR',
    },
    expectedStatus: {
      admin: 'PARTIAL',
      manager: 'PARTIAL',
      observer: 'EXECUTED',
    },
  },
  {
    name: 'all agents fail',
    targets: ['green', 'amber', 'red'],
    results: {
      green: 'ERROR',
      amber: 'ERROR',
    },
    expectedStatus: {
      admin: 'ERROR',
      manager: 'ERROR',
      observer: 'ERROR',
    },
  },
  {
    name: 'no agent in the observer clearance',
    targets: ['amber', 'red'],
    results: { amber: 'EXECUTED' },
    expectedStatus: {
      admin: 'EXECUTED',
      manager: 'EXECUTED',
      observer: 'NOT_VISIBLE',
    },
  },
];

test.describe('Simulation - Execution status with asset marked', () => {
  // Seeded once as admin for every persona: a read-only persona cannot create them, and the data does not
  // depend on who reads it. Playwright reruns these hooks in a new worker after a failure.
  let adminRequest: APIRequestContext;
  let agents: Record<MarkedAssetKey, FakeAgent>;
  let payloadId: string;
  let simulation: Exercise;
  let injects: InjectOutput[];

  test.beforeAll(async ({ playwright, getMarkingDefinitionId }) => {
    adminRequest = await newRequestContextAs(playwright, 'admin');
    const fakeAgentApiHelpers = new FakeAgentApiHelpers(adminRequest);
    const endpointApiHelpers = new EndpointsApiHelpers(adminRequest);
    const simulationApiHelpers = new SimulationApiHelpers(adminRequest);
    const injectApiHelpers = new InjectApiHelpers(adminRequest);
    const suffix = `${Date.now()}-${Math.random()}`;

    const [green, amber, red] = await Promise.all([
      fakeAgentApiHelpers.register(`fake-green-${suffix}`),
      fakeAgentApiHelpers.register(`fake-amber-${suffix}`),
      fakeAgentApiHelpers.register(`fake-red-${suffix}`),
    ]);
    agents = {
      green,
      amber,
      red,
    };
    await Promise.all((Object.keys(MARKINGS) as MarkedAssetKey[]).map(key =>
      endpointApiHelpers.replaceMarkings([agents[key].assetId], [getMarkingDefinitionId(MARKINGS[key])])));

    const payload = await new PayloadApiHelpers(adminRequest).createCommandPayload(`E2E payload ${suffix}`);
    payloadId = payload.payloadId;
    simulation = await simulationApiHelpers.createSimulation(`Simulation marking execution ${suffix}`);
    injects = await Promise.all(OUTCOMES.map(outcome => simulationApiHelpers.createInject(simulation.exercise_id, {
      inject_title: `Inject ${outcome.name} ${suffix}`,
      inject_injector_contract: payload.contractId,
      inject_content: {
        expectations: [{
          expectation_type: 'DETECTION',
          expectation_name: 'Detection',
          expectation_score: 100,
          expectation_expectation_group: false,
          expectation_expiration_time: 3600,
        }],
      },
      inject_assets: outcome.targets.map(key => agents[key].assetId),
      // Required on a simulation inject (its offset from the simulation start), unlike an atomic testing.
      inject_depends_duration: 0,
    })));

    const launcherRequest = await newRequestContextAs(playwright, LAUNCHER);
    try {
      await new SimulationApiHelpers(launcherRequest).startSimulation(simulation.exercise_id);
    } finally {
      await launcherRequest.dispose();
    }

    const statusesOf = () => Promise.all(injects.map(async inject =>
      (await injectApiHelpers.getStatus(inject.inject_id)).status_name));

    // InjectsExecutionJob fires once a minute, on the minute: the injects are dispatched within the next tick.
    // An agent's COMPLETE is refused (409) until its inject is PENDING.
    await expect.poll(statusesOf, {
      message: 'the injects should be dispatched to the agents',
      intervals: [2_000],
      timeout: 90_000,
    }).toEqual(OUTCOMES.map(() => 'PENDING'));

    await Promise.all(OUTCOMES.map(async (outcome, index) => {
      for (const [key, result] of Object.entries(outcome.results) as [MarkedAssetKey, AgentResult][]) {
        // eslint-disable-next-line no-await-in-loop -- the agents of one inject report one after the other
        await fakeAgentApiHelpers.complete(agents[key].agentId, injects[index].inject_id, result);
      }
    }));

    // The callbacks go through a queue: the status is computed once it is consumed.
    await expect.poll(statusesOf, {
      message: 'the injects should end with the agents\' results',
      intervals: [2_000],
      timeout: 30_000,
    }).toEqual(OUTCOMES.map(outcome => outcome.expectedStatus.admin));
  });

  test.afterAll(async () => {
    if (!adminRequest) {
      return;
    }
    // Deleting the simulation deletes its injects, which releases the payload and the agents' endpoints.
    if (simulation) {
      await new SimulationApiHelpers(adminRequest).deleteSimulation(simulation.exercise_id);
    }
    if (payloadId) {
      await new PayloadApiHelpers(adminRequest).deletePayload(payloadId);
    }
    if (agents) {
      const endpointApiHelpers = new EndpointsApiHelpers(adminRequest);
      await Promise.all(Object.values(agents).map(agent => endpointApiHelpers.deleteEndpoint(agent.assetId)));
    }
    await adminRequest.dispose();
  });

  test('should create the expectations only on the targets cleared for the launcher', async () => {
    // Act
    const expectations = await new SimulationApiHelpers(adminRequest).getExpectations(simulation.exercise_id);

    // Assert
    // An expectation is what scores the inject: the red asset, left out of the execution, never weighs on it.
    OUTCOMES.forEach((outcome, index) => {
      const expectedAssetIds = outcome.targets
        .filter(key => launcherAssets.includes(key))
        .map(key => agents[key].assetId);
      const assetIds = new Set(expectations
        .filter(expectation => expectation.inject_expectation_inject === injects[index].inject_id)
        .map(expectation => expectation.inject_expectation_asset)
        .filter(assetId => assetId !== undefined));
      expect([...assetIds].toSorted(), outcome.name).toEqual(expectedAssetIds.toSorted());
    });
  });

  for (const reader of READERS) {
    test(`${reader} should read the status computed from the agents its clearance covers`, async ({ playwright }) => {
      // Arrange
      const request = await newRequestContextAs(playwright, reader);
      const injectApiHelpers = new InjectApiHelpers(request);

      try {
        // Act
        const statuses = await Promise.all(injects.map(async inject =>
          (await injectApiHelpers.getStatus(inject.inject_id)).status_name));

        // Assert
        OUTCOMES.forEach((outcome, index) => expect(statuses[index], outcome.name).toBe(outcome.expectedStatus[reader]));
      } finally {
        await request.dispose();
      }
    });
  }
});
