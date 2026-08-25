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
    // MUI nests the message under the FormControl. A library field has no
    // FormControl: its helper text is a `<p id="…-helper">` inside the combobox
    // root, which is the nearest ancestor also holding the label and the field.
    // Both forms coexist while the migration is partial, so match either — only
    // one of the two can resolve for a given field.
    const mui = fieldLocator
      .locator('xpath=ancestor::*[contains(@class, "MuiFormControl-root")]')
      .first()
      .locator('.MuiFormHelperText-root.Mui-error');
    // Combobox wraps its parts in a flex column, so its helper text is a child
    // of that root.
    const combobox = fieldLocator
      .locator('xpath=ancestor::div[contains(@class, "flex-col")][1]/p[substring(@id, string-length(@id) - 6) = "-helper"]');
    // Select renders NO wrapper of its own (LIBRARY-FEEDBACK 43), so its helper
    // text is a plain sibling of the trigger, wherever the trigger sits.
    const select = fieldLocator
      .locator('xpath=following-sibling::p[substring(@id, string-length(@id) - 6) = "-helper"]');
    return mui.or(combobox).or(select);
  }

  static getListContainer(listItemLocator: Locator): Locator {
    return listItemLocator.locator('xpath=ancestor::*[contains(@class, "MuiList-root")]').first();
  }
}

export default MuiFormHelpers;
