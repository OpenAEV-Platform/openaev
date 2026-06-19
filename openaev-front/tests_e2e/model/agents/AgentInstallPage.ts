import { expect, type Locator, type Page } from '@playwright/test';

import { TIMEOUT } from '../../utils/constants';

/**
 * Page object for the Agent install dialog flow.
 * Handles: opening the install dialog, selecting a platform, and extracting the install command.
 */
class AgentInstallPage {
  constructor(private page: Page) {}

  get installButton(): Locator {
    return this.page.getByRole('button', { name: /Install/i }).first();
  }

  async waitForLoad(): Promise<void> {
    await this.page.waitForURL('**/agents**');
  }

  /**
   * Opens the install dialog, selects the given platform, and returns the install command.
   */
  async getInstallCommand(platform: string): Promise<string> {
    await this.installButton.click();
    await this.page.getByText(`Install ${platform} agent`).click();

    const preBlock = this.page.locator('pre').first();
    await expect(preBlock).not.toBeEmpty({ timeout: TIMEOUT });
    return (await preBlock.textContent())!;
  }
}

export default AgentInstallPage;
