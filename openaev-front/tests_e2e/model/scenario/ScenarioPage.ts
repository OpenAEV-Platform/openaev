import { type Locator, type Page } from '@playwright/test';

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
    await MuiListHelpers.searchAndSelectItemInList(this.updateTeamDialog.listContainer, existingTeamName);
    await this.updateTeamDialog.getChipLocator(existingTeamName).waitFor({
      state: 'visible',
      timeout: 3000,
    });
    await this.updateTeamDialog.save();
  }

  async addIndividualMailInject() {
    await this.injectAddBtn.click();
    await MuiListHelpers.searchAndSelectItemInList(this.page, 'Send individual mails');
    await this.page.getByRole('button', { name: 'Create' }).click();
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
}

export default ScenarioPage;
