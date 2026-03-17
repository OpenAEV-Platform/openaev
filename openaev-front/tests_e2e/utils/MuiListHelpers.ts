import { type Locator, type Page } from '@playwright/test';

class MuiListHelpers {
  constructor() {}

  static filterItemsInList(locatorOrPage: Locator | Page, searchText: string) {
    return locatorOrPage.getByRole('listitem').filter({ hasText: searchText });
  }

  static async clickSecondaryActionOnListItem(
    page: Page,
    listLocator: Locator | Page,
    itemText: string,
    actionLabel: string,
  ) {
    await this.filterItemsInList(listLocator, itemText)
      .locator('button')
      .click();
    return await page.getByRole('menuitem', { name: actionLabel }).click();
  }

  static async searchAndSelectItemInList(locatorOrPage: Locator | Page, searchText: string) {
    await locatorOrPage.getByRole('textbox', { name: 'Search these results...' }).fill(searchText);
    await locatorOrPage.getByRole('button', { name: searchText }).click();
  }
}

export default MuiListHelpers;
