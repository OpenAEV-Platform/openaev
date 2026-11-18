import { expect, type Locator, type Page, type Response } from '@playwright/test';

// The contract constraint is pre-applied as a filter on this entity property.
const CREDENTIAL_TYPE_FILTER_KEY = 'secret_reference_credential_type';
// The row owns the selection: its design-system checkbox is presentational (an aria-hidden span
// without the checkbox role), so its state is read from the `data-state` attribute instead.
const ROW_CHECKBOX_SELECTOR = 'span[aria-hidden="true"][data-state]';

class CredentialsPickerDialog {
  readonly page: Page;
  readonly dialog: Locator;
  readonly searchField: Locator;
  readonly constraintChip: Locator;
  readonly submitButton: Locator;
  readonly cancelButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.dialog = page.getByRole('dialog', { name: /Update credentials? in this inject/ });
    this.searchField = this.dialog.getByPlaceholder('Search these results...');
    // Filter chips are the only removable chips of the picker (status chips are not). MUI only
    // sets the icon test ids (e.g. "CancelIcon") in development builds, so the E2E environment,
    // which serves a production build, is matched on the deletable state class instead.
    this.constraintChip = this.dialog.locator('.MuiChip-root.MuiChip-deletable');
    this.submitButton = this.dialog.getByRole('button', {
      name: 'Update',
      exact: true,
    });
    this.cancelButton = this.dialog.getByRole('button', {
      name: 'Cancel',
      exact: true,
    });
  }

  // -- Get Locator methods

  /** Selectable rows (each row carries a trailing checkbox, the header row does not). */
  getRows() {
    return this.dialog.getByRole('button').filter({ has: this.page.locator(ROW_CHECKBOX_SELECTOR) });
  }

  getRow(credentialName: string) {
    return this.getRows().filter({ hasText: credentialName });
  }

  getRowCheckbox(credentialName: string) {
    return this.getRow(credentialName).locator(ROW_CHECKBOX_SELECTOR);
  }

  // -- Assertion methods

  async expectSelected(credentialName: string, selected = true) {
    await expect(this.getRowCheckbox(credentialName)).toHaveAttribute('data-state', selected ? 'checked' : 'unchecked');
  }

  // -- Action methods

  private waitForSearch(predicate: (body: string) => boolean): Promise<Response> {
    return this.page.waitForResponse(response => response.url().includes('/credentials/search')
      && response.request().method() === 'POST'
      && predicate(response.request().postData() ?? ''));
  }

  /** Narrows the list to the credentials of the current test, and waits for the matching results. */
  async search(text: string) {
    const searchResponse = this.waitForSearch(body => body.includes(text));
    await this.searchField.fill(text);
    await searchResponse;
  }

  async removeConstraint() {
    const searchResponse = this.waitForSearch(body => !body.includes(CREDENTIAL_TYPE_FILTER_KEY));
    await this.constraintChip.locator('.MuiChip-deleteIcon').click();
    await searchResponse;
  }

  async select(credentialName: string) {
    const row = this.getRow(credentialName);
    await row.click();
    await this.expectSelected(credentialName);
  }

  async submit() {
    await this.submitButton.click();
    await expect(this.dialog).toBeHidden();
  }

  async cancel() {
    await this.cancelButton.click();
    await expect(this.dialog).toBeHidden();
  }
}

export default CredentialsPickerDialog;
