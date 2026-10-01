import { expect, type Locator, type Page } from '@playwright/test';

import MuiListHelpers from '../../utils/MuiListHelpers';
import CredentialsPickerDialog from '../credentials/CredentialsPickerDialog';

/** Credential reference section of the inject form (single-credential contract field). */
class InjectCredentialReferenceComponent {
  readonly page: Page;
  readonly form: Locator;
  readonly updateCredentialButton: Locator;
  readonly submitButton: Locator;
  readonly picker: CredentialsPickerDialog;

  constructor(page: Page) {
    this.page = page;
    this.form = page.locator('form#injectForm');
    this.updateCredentialButton = this.form.getByRole('button', {
      name: 'Update credential',
      exact: true,
    });
    this.submitButton = page.getByTestId('inject-form-submit-button');
    this.picker = new CredentialsPickerDialog(page);
  }

  // -- Get Locator methods

  getFieldLabel(label: string) {
    return this.form.locator('label').filter({ hasText: label });
  }

  /** Credentials associated with the inject (each row exposes a kebab, the header row does not). */
  getSelectedCredentials() {
    return this.form
      .getByRole('listitem')
      .filter({ has: this.page.getByRole('button', { name: 'More actions' }) });
  }

  getSelectedCredential(credentialName: string) {
    return MuiListHelpers.filterItemsInList(this.form, credentialName);
  }

  // -- Action methods

  async openPicker() {
    await this.updateCredentialButton.click();
    await expect(this.picker.dialog).toBeVisible();
  }

  async removeCredential(credentialName: string) {
    await MuiListHelpers.clickSecondaryActionOnListItem(this.page, this.form, credentialName, 'Remove from the inject');
    const confirmDialog = this.page.getByRole('dialog').filter({ hasText: 'Do you want to remove this credential from the inject?' });
    await confirmDialog.getByRole('button', { name: 'Remove' }).click();
    await expect(confirmDialog).toBeHidden();
  }

  /** Saves the inject and checks that the update succeeded. */
  async save() {
    const updateResponse = this.page.waitForResponse(response => response.url().includes('/atomic-testings/')
      && response.request().method() === 'PUT');
    await this.submitButton.click();
    const response = await updateResponse;
    expect(response.ok(), `inject update failed: ${response.status()} ${await response.text()}`).toBeTruthy();
    await expect(this.form).toBeHidden();
  }
}

export default InjectCredentialReferenceComponent;
