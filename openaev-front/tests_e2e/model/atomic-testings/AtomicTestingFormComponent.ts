import { expect, type Locator, type Page } from '@playwright/test';

class AtomicTestingFormComponent {
  readonly page: Page;

  readonly payloadSearch: Locator;
  readonly modifyAssetsButton: Locator;
  readonly updateAssetsButton: Locator;
  readonly submitButton: Locator;
  readonly launchButton: Locator;
  readonly relaunchButton: Locator;
  readonly confirmButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.payloadSearch = page.getByPlaceholder('Search these results...').first();
    this.modifyAssetsButton = page.getByRole('button', { name: 'Modify assets' });
    this.updateAssetsButton = page.getByRole('button', { name: 'Update' });
    this.submitButton = page.getByTestId('inject-form-submit-button');
    this.launchButton = page.getByRole('button', { name: /Launch now/i });
    this.relaunchButton = page.getByRole('button', { name: 'Relaunch now' });
    this.confirmButton = page.getByRole('button', { name: /Confirm/i });
  }

  async searchAndSelectPayload(payloadName: string) {
    await this.payloadSearch.fill(payloadName);
    await this.page.getByText(payloadName).first().click();
  }

  async selectAsset(assetName: string) {
    await this.modifyAssetsButton.click();
    await this.page.getByText(assetName, { exact: false }).first().click();
    await this.updateAssetsButton.click();
  }

  async submit() {
    await this.submitButton.click();
    await this.page.waitForURL('**/atomic_testings/**');
  }

  async launch() {
    await expect(this.launchButton).toBeEnabled({ timeout: 60_000 });
    await this.launchButton.click();
    await this.confirmButton.click();
    await expect(this.relaunchButton).toBeVisible({ timeout: 30_000 });
  }
}

export default AtomicTestingFormComponent;
