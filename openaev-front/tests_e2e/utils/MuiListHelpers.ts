import { expect, type Locator, type Page } from '@playwright/test';

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
    await locatorOrPage.getByPlaceholder('Search these results...').first().fill(searchText);
    const itemRow = locatorOrPage
      .getByRole('button')
      .filter({ hasText: searchText })
      .first();
    await expect(itemRow).toBeVisible();
    await itemRow.dispatchEvent('click');
  }
}

export default MuiListHelpers;
