import type { APIRequestContext, TestInfo } from '@playwright/test';
import { expect } from '@playwright/test';

import { test } from '../../fixtures';
import UpdateTeamDialog from '../../model/common/UpdateTeamDialog';
import ScenarioPage from '../../model/scenario/ScenarioPage';
import { tenantUrl } from '../../utils/url';

const escapeRegExp = (value: string): string => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const auditLogTimeoutMs = Number(process.env.E2E_AUDIT_LOG_TIMEOUT_MS ?? '45000');
const auditLogAssertionsEnabled = !process.env.CI
  || (Boolean(process.env.OPENAEV_APPLICATION_LICENSE));

const managementLogfileUrl = (): string => {
  if (process.env.AUDIT_LOGFILE_ENDPOINT_URL) {
    return process.env.AUDIT_LOGFILE_ENDPOINT_URL;
  }

  const managementUrl = new URL(process.env.APP_URL ?? 'http://localhost:3001');
  managementUrl.port = process.env.MANAGEMENT_PORT ?? '8080';
  const managementBasePath = process.env.MANAGEMENT_BASE_PATH ?? '/actuator';
  const normalizedBasePath = managementBasePath.startsWith('/') ? managementBasePath : `/${managementBasePath}`;
  managementUrl.pathname = `${normalizedBasePath}/logfile`;
  managementUrl.search = '';
  return managementUrl.toString();
};

const findJsonObjectEnd = (content: string, startIndex: number): number => {
  let depth = 0;
  let inString = false;
  let escaped = false;

  for (let index = startIndex; index < content.length; index += 1) {
    const char = content[index];

    if (inString) {
      if (escaped) {
        escaped = false;
      } else if (char === '\\') {
        escaped = true;
      } else if (char === '"') {
        inString = false;
      }
      continue;
    }

    if (char === '"') {
      inString = true;
      continue;
    }

    if (char === '{') {
      depth += 1;
    } else if (char === '}') {
      depth -= 1;
      if (depth === 0) {
        return index;
      }
    }
  }

  return -1;
};

const extractAuditEntries = (rawContent: string): string[] => {
  const entries: string[] = [];
  let searchIndex = 0;

  while (searchIndex < rawContent.length) {
    const markerIndex = rawContent.indexOf('[AUDIT]', searchIndex);
    if (markerIndex < 0) {
      break;
    }

    const jsonStart = rawContent.indexOf('{', markerIndex);
    if (jsonStart < 0) {
      break;
    }

    const jsonEnd = findJsonObjectEnd(rawContent, jsonStart);
    if (jsonEnd < 0) {
      searchIndex = markerIndex + '[AUDIT]'.length;
      continue;
    }

    const rawEntry = rawContent.slice(jsonStart, jsonEnd + 1);
    entries.push(rawEntry);

    try {
      entries.push(JSON.stringify(JSON.parse(rawEntry)));
    } catch {
      // Keep raw entry only when parsing fails.
    }

    searchIndex = jsonEnd + 1;
  }

  return entries;
};

const attachAuditDebugOnFailure = async (
  request: APIRequestContext,
  testInfo: TestInfo,
  context: string,
  matchers: RegExp[],
): Promise<void> => {
  const logfileEndpoint = managementLogfileUrl();
  const response = await request.get(logfileEndpoint);
  const rawContent = await response.text();
  const logfileTail = rawContent.split('\n').slice(-350).join('\n');
  const auditEntries = extractAuditEntries(rawContent);
  const matcherStatus = matchers
    .map((matcher, index) => `[${index}] ${matcher.source} => ${auditEntries.some(entry => matcher.test(entry))}`)
    .join('\n');

  const debugReport = [
    `[AUDIT DEBUG] context=${context}`,
    `[AUDIT DEBUG] endpoint=${logfileEndpoint}`,
    `[AUDIT DEBUG] status=${response.status()}`,
    '[AUDIT DEBUG] matcher_status:',
    matcherStatus,
    '[AUDIT DEBUG] logfile_tail:',
    logfileTail,
  ].join('\n');

  // Keep diagnostics visible in CI logs even when attachments are hard to retrieve.
  process.stdout.write(`${debugReport}\n`);

  await testInfo.attach(`audit-debug-${context}.txt`, {
    body: Buffer.from(debugReport, 'utf-8'),
    contentType: 'text/plain',
  });
};

const createTeamAddedMatcher = (scenarioId: string): RegExp => {
  const escapedScenarioId = escapeRegExp(scenarioId);
  return new RegExp(
    `"event_scope"\\s*:\\s*"update"[\\s\\S]*?"url"\\s*:\\s*"[^"]*/scenarios/${escapedScenarioId}/teams/replace"[\\s\\S]*?"method"\\s*:\\s*"PUT"`,
    'i',
  );
};

const expectAuditLogContainsAll = async (
  request: APIRequestContext,
  testInfo: TestInfo,
  context: string,
  matchers: RegExp[],
): Promise<void> => {
  const logfileEndpoint = managementLogfileUrl();

  try {
    await expect(async () => {
      const response = await request.get(logfileEndpoint);
      expect(response.ok()).toBeTruthy();

      const auditLog = await response.text();
      const entries = extractAuditEntries(auditLog);
      expect(matchers.every(matcher => entries.some(entry => matcher.test(entry)))).toBeTruthy();
    }).toPass({
      intervals: [1_000, 2_000, 5_000],
      timeout: auditLogTimeoutMs,
    });
  } catch (error) {
    await attachAuditDebugOnFailure(request, testInfo, context, matchers);
    throw error;
  }
};

const expectTeamAddedAuditLog = async (
  request: APIRequestContext,
  testInfo: TestInfo,
  scenarioId: string,
): Promise<void> => {
  const logfileEndpoint = managementLogfileUrl();
  const teamAddedMatcher = createTeamAddedMatcher(scenarioId);

  try {
    await expect(async () => {
      const response = await request.get(logfileEndpoint);
      expect(response.ok()).toBeTruthy();

      const auditLog = await response.text();
      const entries = extractAuditEntries(auditLog);
      expect(entries.some(entry => teamAddedMatcher.test(entry))).toBeTruthy();
    }).toPass({
      intervals: [1_000, 2_000, 5_000],
      timeout: auditLogTimeoutMs,
    });
  } catch (error) {
    await attachAuditDebugOnFailure(request, testInfo, 'team-add', [teamAddedMatcher]);
    throw error;
  }
};

const expectScenarioLifecycleAuditLog = async (
  request: APIRequestContext,
  testInfo: TestInfo,
  scenarioId: string,
): Promise<void> => {
  const escapedScenarioId = escapeRegExp(scenarioId);

  const matchers = [
    new RegExp(
      `"event_scope"\\s*:\\s*"create"[\\s\\S]*?"url"\\s*:\\s*"[^"]*/api/scenarios"[\\s\\S]*?"method"\\s*:\\s*"POST"[\\s\\S]*?("resource_id"\\s*:\\s*"${escapedScenarioId}"|${escapedScenarioId})`,
      'i',
    ),
    new RegExp(
      `"event_scope"\\s*:\\s*"update"[\\s\\S]*?"url"\\s*:\\s*"[^"]*/scenarios/${escapedScenarioId}/injects"[\\s\\S]*?"method"\\s*:\\s*"POST"`,
      'i',
    ),
    new RegExp(
      `"event_scope"\\s*:\\s*"update"[\\s\\S]*?"url"\\s*:\\s*"[^"]*/scenarios/${escapedScenarioId}/teams/replace"[\\s\\S]*?"method"\\s*:\\s*"PUT"`,
      'i',
    ),
    new RegExp(
      `"event_scope"\\s*:\\s*"status_change"[\\s\\S]*?"url"\\s*:\\s*"[^"]*/scenarios/${escapedScenarioId}/exercise/running"[\\s\\S]*?"method"\\s*:\\s*"POST"`,
      'i',
    ),
  ];

  await expectAuditLogContainsAll(request, testInfo, 'scenario-lifecycle', matchers);
};

const findAnyInjectorContractId = async (request: APIRequestContext): Promise<string> => {
  const response = await request.get('/api/injector_contracts');
  expect(response.ok()).toBeTruthy();

  const injectorContracts = await response.json() as Array<{ injector_contract_id: string }>;
  expect(injectorContracts.length).toBeGreaterThan(0);
  return injectorContracts[0].injector_contract_id;
};

const addInjectToScenario = async (request: APIRequestContext, scenarioId: string): Promise<void> => {
  const injectorContractId = await findAnyInjectorContractId(request);
  const response = await request.post(`/api/scenarios/${scenarioId}/injects`, {
    data: {
      inject_title: `Lifecycle inject ${Date.now()}`,
      inject_injector_contract: injectorContractId,
      inject_depends_duration: 0,
    },
  });
  expect(response.ok()).toBeTruthy();
};

const assertAuditPrerequisites = async (request: APIRequestContext): Promise<void> => {
  const eeProbe = await request.post('/api/tenants/search', { data: {} });
  process.stdout.write(`[E2E][EE] POST /api/tenants/search -> ${eeProbe.status()}\n`);
  expect(
    eeProbe.ok(),
    'Enterprise Edition appears inactive: /api/tenants/search is not reachable with current auth/license.',
  ).toBeTruthy();

  const logfileEndpoint = managementLogfileUrl();
  const logfileResponse = await request.get(logfileEndpoint);
  process.stdout.write(`[E2E][AUDIT] GET ${logfileEndpoint} -> ${logfileResponse.status()}\n`);
  expect(logfileResponse.ok(), 'Actuator logfile endpoint is not reachable in this environment.').toBeTruthy();

  const logfileText = await logfileResponse.text();
  const hasAuditMarker = logfileText.includes('[AUDIT]') || logfileText.includes('"event_type"');
  expect(
    hasAuditMarker,
    `Logfile endpoint is reachable but contains no audit entries yet. Sample: ${logfileText.slice(0, 300)}`,
  ).toBeTruthy();
};

test.describe('Scenario - Teams management', () => {
  let scenarioPage: ScenarioPage;
  let updateTeamDialog: UpdateTeamDialog;

  test.beforeEach(async ({ page, emptyScenario }) => {
    updateTeamDialog = new UpdateTeamDialog(page);
    scenarioPage = new ScenarioPage(page);
    await page.addInitScript(() => {
      const style = document.createElement('style');
      style.innerHTML = `
        *, *::before, *::after {
          transition: none !important;
          animation: none !important;
        }
      `;
      document.head.appendChild(style);
    });
    // Teams management now lives in the hero "Configuration" drawer (teams tab).
    await page.goto(tenantUrl(`/admin/scenarios/${emptyScenario.scenario_id}`));
    await page.waitForLoadState('domcontentloaded');
    await scenarioPage.openConfiguration();
  });

  test.describe('Team CRUD Operations in scenario', () => {
    test('should add and remove existing team', async ({ page, createTeam }) => {
      const team = await createTeam(`Default team ${Date.now()}-${Math.random()}`);
      // Add team
      await scenarioPage.addExistingTeam(team.team_name);
      await expect(scenarioPage.getAllTeamItems()).toHaveCount(1);
      await expect(scenarioPage.getTeam(team.team_name)).toBeVisible();

      // Remove teams from scenario context
      await scenarioPage.clickSecondaryActionOnTeamList(team.team_name, 'Remove from the context');
      await page.getByRole('button', { name: 'Remove' }).click();
      await expect(scenarioPage.getAllTeamItems()).toHaveCount(0);
      await expect(scenarioPage.getTeam(team.team_name)).toHaveCount(0);
    });

    test('should create and add new contextual team', async ({ page }) => {
      const newTeamName = `New team ${Date.now()}-${Math.random()}`;
      // Create and add team
      await expect(scenarioPage.teamAddBtn).toBeVisible();
      await scenarioPage.teamAddBtn.click();
      await updateTeamDialog.createNewTeam(newTeamName, 'Team created from scenario', true);
      await updateTeamDialog.save();
      // Verify team has been added
      await expect(scenarioPage.getAllTeamItems()).toHaveCount(1);
      await expect(scenarioPage.getTeam(newTeamName)).toBeVisible();

      // Remove teams from scenario context
      await scenarioPage.clickSecondaryActionOnTeamList(newTeamName, 'Delete');
      await page.getByRole('button', { name: 'Delete' }).click();
      // Verify team has been removed
      await expect(scenarioPage.getAllTeamItems()).toHaveCount(0);
      await expect(scenarioPage.getTeam(newTeamName)).toHaveCount(0);
    });

    test.describe('Audit logging assertions', () => {
      if (!auditLogAssertionsEnabled) {
        return;
      }

      test.beforeEach(async ({ request }) => {
        await assertAuditPrerequisites(request);
      });

      test('should audit team add from scenario configuration', async ({
        request,
        emptyScenario,
        page,
        createTeam,
      }, testInfo) => {
        const scenarioId = emptyScenario.scenario_id;
        const existingTeam = await createTeam(`Audited team ${Date.now()}-${Math.random()}`);

        await scenarioPage.addExistingTeam(existingTeam.team_name);

        await expectTeamAddedAuditLog(request, testInfo, scenarioId);

        await scenarioPage.clickSecondaryActionOnTeamList(existingTeam.team_name, 'Remove from the context');
        await page.getByRole('button', { name: 'Remove' }).click();
      });

      test('should log full scenario lifecycle with child actions', async ({
        request,
        emptyScenario,
        createTeam,
      }, testInfo) => {
        const scenarioId = emptyScenario.scenario_id;
        const existingTeam = await createTeam(`Lifecycle team ${Date.now()}-${Math.random()}`);

        // Add child resources (inject + team) to the scenario.
        await scenarioPage.addExistingTeam(existingTeam.team_name);
        await expect(scenarioPage.getTeam(existingTeam.team_name)).toBeVisible();
        await addInjectToScenario(request, scenarioId);

        // Run the scenario by creating a running exercise from it.
        const launchResponse = await request.post(`/api/scenarios/${scenarioId}/exercise/running`);
        expect(launchResponse.ok()).toBeTruthy();

        // Validate the full lifecycle audit trail.
        await expectScenarioLifecycleAuditLog(request, testInfo, scenarioId);
      });
    });
  });

  test.describe('Player Management', () => {
    test('should be able to activate and deactivate player', async ({ createTeamWithMultiplePlayers, createPlayer }) => {
      const players = await Promise.all([
        createPlayer(`aude-test1-${Date.now()}-${Math.random()}@test.io`),
        createPlayer(`mia-test1-${Date.now()}-${Math.random()}@test.io`),
        createPlayer(`make-test1-${Date.now()}-${Math.random()}@test.io`),
      ]);
      const teamWithMultiplePlayers = await createTeamWithMultiplePlayers(
        `Team with players ${Date.now()}-${Math.random()}`,
        players.map(p => p.user_id),
      );

      // Add team to scenario
      await expect(scenarioPage.teamAddBtn).toBeVisible();
      await scenarioPage.addExistingTeam(teamWithMultiplePlayers.team_name);
      await expect(scenarioPage.getTeam(teamWithMultiplePlayers.team_name)).toBeVisible();
    });
  });

  test.describe('Teams in Injects', () => {
    // TODO: work on this flaky test
    // test('should only show scenario teams in inject form', async ({ page, createTeam }) => {
    //   const [team1, team2, team3] = await Promise.all([
    //     createTeam(`Team 1-${Date.now()}-${Math.random()}`),
    //     createTeam(`Team 2-${Date.now()}-${Math.random()}`),
    //     createTeam(`Team 3-${Date.now()}-${Math.random()}`),
    //   ]);
    //
    //   await scenarioPage.addExistingTeam(team1.team_name);
    //   await scenarioPage.addExistingTeam(team2.team_name);
    //
    //   await scenarioPage.goToInjectsTab();
    //   await expect(scenarioPage.injectListSection).toBeVisible();
    //   await scenarioPage.addIndividualMailInject();
    //
    //   await MuiListHelpers.searchAndSelectItemInList(page, 'Send individual mails');
    //   injectFormComponent = new InjectFormComponent(page);
    //   await injectFormComponent.updateTargetTeamButton.click();
    //   await updateTeamDialog.searchField.clear();
    //
    //   await expect(updateTeamDialog.listContainer.getByRole('button', { name: team1.team_name })).toBeVisible();
    //   await expect(updateTeamDialog.listContainer.getByRole('button', { name: team2.team_name })).toBeVisible();
    //   await expect(updateTeamDialog.listContainer.getByRole('button', { name: team3.team_name })).toBeHidden();
    //
    //   await updateTeamDialog.searchField.fill(team3.team_name);
    //   await expect(updateTeamDialog.listContainer.getByRole('button', { name: team3.team_name })).toBeHidden();
    //
    //   await MuiListHelpers.searchAndSelectItemInList(updateTeamDialog.listContainer, team2.team_name);
    //   await updateTeamDialog.save();
    //   await injectFormComponent.save();
    //
    //   await page.reload();
    //   await expect(scenarioPage.injectListSection).toBeVisible();
    //   await MuiListHelpers.searchAndSelectItemInList(page, 'Send individual mails');
    //   await expect(page.getByText(team2.team_name)).toBeVisible();
    //
    //   await MuiListHelpers.clickSecondaryActionOnListItem(page, page, team2.team_name, 'Remove from the inject');
    //   await page.getByRole('button', { name: 'Remove' }).click();
    //   await injectFormComponent.save();
    //
    //   await page.reload();
    //   await expect(scenarioPage.injectListSection).toBeVisible();
    //   await MuiListHelpers.searchAndSelectItemInList(page, 'Send individual mails');
    //   await expect(page.getByText(team2.team_name)).toBeHidden();
    // });

    // TODO: work on this flaky test
    // test('should show correct player count in teams', async ({ page, createPlayer, createTeamWithMultiplePlayers }) => {
    //   const [player1, player2, player3] = await Promise.all([
    //     createPlayer(`tony-test2-${Date.now()}@test.io`),
    //     createPlayer(`lena-test2-${Date.now()}@test.io`),
    //     createPlayer(`anna-test2-${Date.now()}@test.io`),
    //   ]);
    //   const nameTeam = `Team1-${Date.now()}-${Math.random()}`;
    //   const team = await createTeamWithMultiplePlayers(nameTeam, [player1.user_id, player2.user_id, player3.user_id]);
    //
    //   await expect(scenarioPage.teamAddBtn).toBeVisible();
    //   await scenarioPage.addExistingTeam(team.team_name);
    //
    //   await scenarioPage.goToInjectsTab();
    //   await expect(scenarioPage.injectListSection).toBeVisible();
    //   await scenarioPage.addIndividualMailInject();
    //
    //   await MuiListHelpers.searchAndSelectItemInList(page, 'Send individual mails');
    //   injectFormComponent = new InjectFormComponent(page);
    //   await injectFormComponent.updateTargetTeamButton.click();
    //   await MuiListHelpers.searchAndSelectItemInList(updateTeamDialog.listContainer, team.team_name);
    //   await updateTeamDialog.save();
    //
    //   await expect(page.getByTestId('user-count')).toHaveText('3');
    //   await injectFormComponent.save();
    //
    //   await scenarioPage.goToDefinitionTab();
    //   await expect(scenarioPage.getTeam(team.team_name)).toBeVisible();
    //   await scenarioPage.getTeam(team.team_name).click();
    //   await page.reload();
    //
    //   await scenarioPage.goToInjectsTab();
    //   await expect(scenarioPage.injectListSection).toBeVisible();
    //   await MuiListHelpers.searchAndSelectItemInList(page, 'Send individual mails');
    //   await expect(page.getByTestId('user-count')).toHaveText('3');
    // });
    // TODO: work on this flaky test
    // test('should be able to add all the scenario teams', async ({ page, createPlayer, createTeamWithMultiplePlayers }) => {
    //   const [player1, player2, player3, player4, player5] = await Promise.all([
    //     createPlayer(`tony-test3-${Date.now()}@test.io`),
    //     createPlayer(`lena-test3-${Date.now()}@test.io`),
    //     createPlayer(`alex-test3-${Date.now()}@test.io`),
    //     createPlayer(`jade-test3-${Date.now()}@test.io`),
    //     createPlayer(`anna-test3-${Date.now()}@test.io`),
    //   ]);
    //   const team1 = await createTeamWithMultiplePlayers(`Team1 ${Date.now()}-${Math.random()}`, [player1.user_id, player2.user_id, player3.user_id]);
    //   const team2 = await createTeamWithMultiplePlayers(`Team2 ${Date.now()}-${Math.random()}`, [player4.user_id, player5.user_id]);
    //
    //   await expect(scenarioPage.teamAddBtn).toBeVisible();
    //   await scenarioPage.addExistingTeam(team1.team_name);
    //   await scenarioPage.addExistingTeam(team2.team_name);
    //   await scenarioPage.getTeam(team1.team_name).click();
    //   await page.reload();
    //
    //   await scenarioPage.goToInjectsTab();
    //   await expect(scenarioPage.injectListSection).toBeVisible();
    //   await scenarioPage.addIndividualMailInject();
    //   await MuiListHelpers.searchAndSelectItemInList(page, 'Send individual mails');
    //   injectFormComponent = new InjectFormComponent(page);
    //   await injectFormComponent.switchAllTeamsCheckbox();
    //
    //   await expect(page.getByTestId('user-count')).toHaveText('5');
    // });
  });
});
