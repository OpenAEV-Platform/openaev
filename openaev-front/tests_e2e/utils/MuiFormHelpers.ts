import { type Locator, type Page } from '@playwright/test';

class MuiFormHelpers {
  constructor() {}

  static async getSelectFieldOption(page: Page, field: Locator) {
    await field.click();
    const options = await page
      .locator('[role="option"]')
      .allTextContents();
    await page.keyboard.press('Escape');
    return options;
  }

  static async selectSingleOption(page: Page, field: Locator, optionText: string) {
    return field.click().then(() =>
      page.getByRole('option', { name: optionText }).click(),
    );
  }

  static getFieldError(fieldLocator: Locator): Locator {
    return fieldLocator
      .locator('xpath=ancestor::*[contains(@class, "MuiFormControl-root")]')
      .first()
      .locator('.MuiFormHelperText-root.Mui-error');
  }

  static getListContainer(listItemLocator: Locator): Locator {
    return listItemLocator.locator('xpath=ancestor::*[contains(@class, "MuiList-root")]').first();
  }
}

export default MuiFormHelpers;
