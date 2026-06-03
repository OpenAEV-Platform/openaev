import { expect } from '@playwright/test';

import ScenarioApiHelpers from '../../api-helpers/ScenarioApiHelpers';
import TeamApiHelpers from '../../api-helpers/TeamApiHelpers';
import { test } from '../../fixtures';

/**
 * End-to-end integration test covering the full Scenario lifecycle with audit logging.
 *
 * Epic:      #5241
 * Design:    Audit Logging — Platform-Level Log Forwarding (No UI)
 * PoC PR:    #5221
 *
 * Mirrors ScenarioLifecycleAuditLogAspectTest.java but exercises the real HTTP stack
 * via the authenticated Playwright request context instead of MockMvc.
 *
 * Definition of Done:
 *   ✅ Inject is created as a child of the scenario (POST /scenarios/{id}/injects → 2xx)
 *   ✅ Team is associated to the scenario (PUT /scenarios/{id}/teams/replace → 2xx)
 *   ✅ Launch returns a successful HTTP response (POST /scenarios/{id}/exercise/running → 2xx)
 *   🔲 Inject create events have parent_id = scenario ID  (pending /api/audit-logs/search)
 *   🔲 Team association events have parent_id = scenario ID (pending /api/audit-logs/search)
 *   🔲 Launch produces event_scope="status_change" (pending /api/audit-logs/search)
 *   🔲 All events have correct event_type=mutation (pending /api/audit-logs/search)
 *   🔲 Child-resource detection classifies injects/teams as child creates (pending /api/audit-logs/search)
 */
test.describe('Scenario Lifecycle — Audit Logging (API-driven)', () => {
  test.describe('Full scenario lifecycle', () => {
    test('should create inject as child of scenario, associate team, and launch successfully',
      async ({ request, emptyScenario }) => {
        const scenarioApi = new ScenarioApiHelpers(request);
        const teamApi = new TeamApiHelpers(request);
        const scenarioId = emptyScenario.scenario_id;

        // Arrange: create a standalone team to associate with the scenario.
        const teamName = `audit-team-e2e-${Date.now()}`;
        const team = await teamApi.createTeam(teamName);

        try {
          // Act (1): Create inject inside the scenario — child-resource creation.
          // POST /api/scenarios/{id}/injects
          const injectResponse = await scenarioApi.createInjectInScenario(
            scenarioId,
            `audit-inject-${Date.now()}`,
          );

          // Assert: inject was created successfully as a child of the scenario.
          expect(injectResponse.status()).toBeLessThan(300);
          const injectBody = await injectResponse.json();
          expect(injectBody.inject_scenario).toBe(scenarioId);

          // Act (2): Associate team to scenario.
          // PUT /api/scenarios/{id}/teams/replace
          const teamAssocResponse = await scenarioApi.replaceTeams(scenarioId, [team.team_id]);

          // Assert: team association succeeded.
          expect(teamAssocResponse.status()).toBeLessThan(300);
          const teamsBody = await teamAssocResponse.json();
          const associatedTeamIds = teamsBody.map((t: { team_id: string }) => t.team_id);
          expect(associatedTeamIds).toContain(team.team_id);

          // Act (3): Launch the scenario.
          // POST /api/scenarios/{id}/exercise/running
          const launchResponse = await scenarioApi.launchScenario(scenarioId);

          // Assert: launch produced a successful response.
          // The backend generates a status_change audit event at this point.
          expect(launchResponse.status()).toBeLessThan(300);

          // TODO: once POST /api/audit-logs/search is implemented, add AuditLogApiHelpers
          // assertions here to verify:
          //   - injectBody.inject_id event has parent_id = scenarioId
          //   - team association event has parent_id = scenarioId
          //   - launch event has event_scope = "status_change"
          //   - all lifecycle events have event_type = "mutation"
        } finally {
          // Cleanup team created outside of fixture scope.
          await teamApi.deleteTeam(team.team_id);
        }
      });
  });
});
