import { expect } from '@playwright/test';

import { test } from '../../fixtures';
import UpdateTeamDialog from '../../model/common/UpdateTeamDialog';
import InjectFormComponent from '../../model/injects/InjectFormComponent';
import ScenarioPage from '../../model/scenario/ScenarioPage';
import MuiListHelpers from '../../utils/MuiListHelpers';

test.describe('Scenario - Teams management', () => {
  let scenarioPage: ScenarioPage;
  let updateTeamDialog: UpdateTeamDialog;
  let injectFormComponent: InjectFormComponent;

  test.beforeEach(async ({ page, emptyScenario }) => {
    updateTeamDialog = new UpdateTeamDialog(page);
    scenarioPage = new ScenarioPage(page);
    await page.goto(`/admin/scenarios/${emptyScenario.scenario_id}`);
    await scenarioPage.goToDefinitionTab();
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
      const newTeamName = 'New team';
      // Create and add team
      await expect(scenarioPage.teamAddBtn).toBeVisible();
      await scenarioPage.teamAddBtn.click();
      await updateTeamDialog.createNewTeam(newTeamName, 'Team created from scenario', true);
      await expect(updateTeamDialog.getChipLocator(newTeamName)).toBeVisible();
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
  });

  test.describe('Player Management', () => {
    test('should be able to activate and deactivate player', async ({ page, createTeamWithMultiplePlayers, createPlayer }) => {
      const players = await Promise.all([
        createPlayer(`aude-test1@test.io`),
        createPlayer(`mia-test1@test.io`),
        createPlayer(`make-test1@test.io`),
      ]);
      const teamWithMultiplePlayers = await createTeamWithMultiplePlayers(
        `Team with players ${Date.now()}-${Math.random()}`,
        players.map(p => p.user_id),
      );

      // Add team to scenario
      await expect(scenarioPage.teamAddBtn).toBeVisible();
      await scenarioPage.addExistingTeam(teamWithMultiplePlayers.team_name);
      await expect(scenarioPage.getTeam(teamWithMultiplePlayers.team_name)).toBeVisible();
      // Verify players are present
      await scenarioPage.getTeam(teamWithMultiplePlayers.team_name).click();
      await Promise.all([
        players.map(player =>
          expect(MuiListHelpers.filterItemsInList(page, player.user_email)).toBeVisible(),
        ),
      ]);

      // Deactivate a player
      await MuiListHelpers.filterItemsInList(page, players[2].user_email).getByText('Enabled').click();
      await expect(MuiListHelpers.filterItemsInList(page, players[2].user_email).getByText('Disabled')).toBeVisible();
    });
  });

  test.describe('Teams in Injects', () => {
    test('should only show scenario teams in inject form', async ({ page, createTeam }) => {
      const [team1, team2, team3] = await Promise.all([
        createTeam(`Team 1-${Date.now()}-${Math.random()}`),
        createTeam(`Team 2-${Date.now()}-${Math.random()}`),
        createTeam(`Team 3-${Date.now()}-${Math.random()}`),
      ]);

      // Add team1 and team2 to scenario context
      await scenarioPage.addExistingTeam(team1.team_name);
      await scenarioPage.addExistingTeam(team2.team_name);

      // Add individual mail inject and check teams available
      await scenarioPage.goToInjectsTab();
      await (expect(scenarioPage.injectListSection).toBeVisible());
      await scenarioPage.addIndividualMailInject();

      // Open inject form and check available teams
      await MuiListHelpers.searchAndSelectItemInList(page, 'Send individual mails');
      injectFormComponent = new InjectFormComponent(page);
      await injectFormComponent.updateTargetTeamButton.click();
      await updateTeamDialog.searchField.clear();
      await expect(updateTeamDialog.listContainer.getByRole('button', { name: team1.team_name })).toBeVisible();
      await expect(updateTeamDialog.listContainer.getByRole('button', { name: team2.team_name })).toBeVisible();
      await expect(updateTeamDialog.listContainer.getByRole('button', { name: team3.team_name })).toBeHidden();
      // Search for team3 and check no result
      await updateTeamDialog.searchField.fill(team3.team_name);
      await expect(updateTeamDialog.listContainer.getByRole('button', { name: team3.team_name })).toBeHidden();

      // add team2 as target team
      await MuiListHelpers.searchAndSelectItemInList(updateTeamDialog.listContainer, team2.team_name);
      await updateTeamDialog.save();
      await injectFormComponent.save();

      //  Verify team2 is added
      await page.reload();
      await (expect(scenarioPage.injectListSection).toBeVisible());
      await MuiListHelpers.searchAndSelectItemInList(page, 'Send individual mails');
      await expect(page.getByText(team2.team_name)).toBeVisible();

      // Remove team2 from target teams
      await MuiListHelpers.clickSecondaryActionOnListItem(page, page, team2.team_name, 'Remove from the inject');
      await page.getByRole('button', { name: 'Remove' }).click();
      await injectFormComponent.save();

      await page.reload();
      await (expect(scenarioPage.injectListSection).toBeVisible());
      await MuiListHelpers.searchAndSelectItemInList(page, 'Send individual mails');
      await expect(page.getByText(team2.team_name)).toBeHidden();
    });

    test('should show correct player count in teams', async ({ page, createPlayer, createTeamWithMultiplePlayers }) => {
      const [player1, player2, player3] = await Promise.all([
        createPlayer(`tony-test2-${Date.now()}@test.io`),
        createPlayer(`lena-test2-${Date.now()}@test.io`),
        createPlayer(`anna-test2-${Date.now()}@test.io`),
      ]);
      const nameTeam = `Team1-${Date.now()}-${Math.random()}`;
      const team = await createTeamWithMultiplePlayers(nameTeam, [player1.user_id, player2.user_id, player3.user_id]);
      // Add team to scenario
      await expect(scenarioPage.teamAddBtn).toBeVisible();
      await scenarioPage.addExistingTeam(team.team_name);

      await scenarioPage.goToInjectsTab();
      await (expect(scenarioPage.injectListSection).toBeVisible());
      await scenarioPage.addIndividualMailInject();

      // Open inject form and check available teams
      await MuiListHelpers.searchAndSelectItemInList(page, 'Send individual mails');
      injectFormComponent = new InjectFormComponent(page);
      await injectFormComponent.updateTargetTeamButton.click();
      await MuiListHelpers.searchAndSelectItemInList(updateTeamDialog.listContainer, team.team_name);
      await updateTeamDialog.save();
      await expect(page.getByTestId('user-count')).toHaveText('3');
      await expect(page.getByTestId('enable-user-count')).toHaveText('3');
      await injectFormComponent.save();

      // Disable one player in scenario context
      await scenarioPage.goToDefinitionTab();
      await expect(scenarioPage.getTeam(team.team_name)).toBeVisible();
      await scenarioPage.getTeam(team.team_name).click();
      await MuiListHelpers.filterItemsInList(page, player1.user_email).getByText('Enabled').click();
      await page.reload();

      await scenarioPage.goToInjectsTab();
      await (expect(scenarioPage.injectListSection).toBeVisible());
      await MuiListHelpers.searchAndSelectItemInList(page, 'Send individual mails');
      await expect(page.getByTestId('user-count')).toHaveText('3');
      await expect(page.getByTestId('enable-user-count')).toHaveText('2');
    });
    test('should be able to add all the scenario teams', async ({ page, createPlayer, createTeamWithMultiplePlayers }) => {
      const [player1, player2, player3, player4, player5] = await Promise.all([
        createPlayer(`tony-test3-${Date.now()}@test.io`),
        createPlayer(`lena-test3-${Date.now()}@test.io`),
        createPlayer(`alex-test3-${Date.now()}@test.io`),
        createPlayer(`jade-test3-${Date.now()}@test.io`),
        createPlayer(`anna-test3-${Date.now()}@test.io`),
      ]);
      const team1 = await createTeamWithMultiplePlayers(`Team1 ${Date.now()}-${Math.random()}`, [player1.user_id, player2.user_id, player3.user_id]);
      const team2 = await createTeamWithMultiplePlayers(`Team2 ${Date.now()}-${Math.random()}`, [player4.user_id, player5.user_id]);
      // Add team to scenario
      await expect(scenarioPage.teamAddBtn).toBeVisible();
      await scenarioPage.addExistingTeam(team1.team_name);
      await scenarioPage.addExistingTeam(team2.team_name);
      await scenarioPage.getTeam(team1.team_name).click();
      await MuiListHelpers.filterItemsInList(page, player2.user_email).getByText('Enabled').click();
      await page.reload();

      await scenarioPage.goToInjectsTab();
      await (expect(scenarioPage.injectListSection).toBeVisible());
      await scenarioPage.addIndividualMailInject();
      await MuiListHelpers.searchAndSelectItemInList(page, 'Send individual mails');
      injectFormComponent = new InjectFormComponent(page);
      await injectFormComponent.switchAllTeamsCheckbox();
      await expect(page.getByTestId('user-count')).toHaveText('5');
      await expect(page.getByTestId('enable-user-count')).toHaveText('4');
    });
  });
});
