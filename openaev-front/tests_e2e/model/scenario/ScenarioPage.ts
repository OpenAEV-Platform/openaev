import { expect, type Locator, type Page } from '@playwright/test';

import MuiListHelpers from '../../utils/MuiListHelpers';
import UpdateTeamDialog from '../common/UpdateTeamDialog';

class ScenarioPage {
  readonly page: Page;

  // Definition tab's locator
  readonly definitionTab: Locator;
  readonly teamAddBtn: Locator;
  readonly teamListSection: Locator;
  readonly updateTeamDialog: UpdateTeamDialog;
  // Injects tab's locator
  readonly injectsTab: Locator;
  readonly injectAddBtn: Locator;
  readonly injectListSection: Locator;
  readonly searchInject: Locator;

  constructor(page: Page) {
    this.page = page;
    // Definition tab's locators
    this.definitionTab = page.getByRole('tab', { name: 'Definition' });
    this.teamAddBtn = page.getByRole('heading', { name: 'Teams Add' }).getByLabel('Add');
    this.teamListSection = page.getByTestId('teams-list-section');
    this.updateTeamDialog = new UpdateTeamDialog(page);
    // Injects tab's locators
    this.injectsTab = page.getByRole('tab', { name: 'Injects' });
    this.injectListSection = page.getByTestId('injects-list-section');
    this.injectAddBtn = page.getByRole('button', { name: 'Add' });

    this.searchInject = page.getByPlaceholder('Search these results...');
  }

  // -- Get Locator methods

  getAllTeamItems() {
    return this.teamListSection.locator('li:nth-child(n+2)'); // Skip the first item which is the header
  }

  getTeam(teamName: string) {
    return MuiListHelpers.filterItemsInList(this.teamListSection, teamName);
  }

  // -- Action methods
  async addExistingTeam(existingTeamName: string) {
    await this.teamAddBtn.click();
    await expect(this.updateTeamDialog.searchField).toBeVisible();
    await this.updateTeamDialog.searchField.clear();
    await MuiListHelpers.searchAndSelectItemInList(this.updateTeamDialog.listContainer, existingTeamName);
    await this.updateTeamDialog.save();
  }

  async addIndividualMailInject() {
    await this.injectAddBtn.click();
    await MuiListHelpers.searchAndSelectItemInList(this.page, 'Send individual mails');
    await this.page.getByTestId('inject-form-submit-button').click();
  }

  async goToDefinitionTab() {
    await this.definitionTab.click();
  }

  async goToInjectsTab() {
    await this.injectsTab.click();
  }

  async clickSecondaryActionOnTeamList(teamName: string, actionLabel: string) {
    await MuiListHelpers.clickSecondaryActionOnListItem(
      this.page,
      this.teamListSection,
      teamName,
      actionLabel,
    );
  }

  async searchAndSelectInjectInList(searchText: string) {
    await this.searchInject.first().fill(searchText);
    const firstInject = this.injectListSection.getByRole('button', { name: searchText }).first();
    await firstInject.click();
    if (await firstInject.isVisible()) {
      await firstInject.click();
    }
  }
}

export default ScenarioPage;
