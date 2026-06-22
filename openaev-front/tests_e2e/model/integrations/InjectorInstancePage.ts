import { expect, type Locator, type Page } from '@playwright/test';

import { TIMEOUT } from '../../utils/constants';

/**
 * Page object for the Injector detail page (/admin/integrations/injectors/:id).
 * Wraps start/stop interactions and status polling.
 */
class InjectorInstancePage {
  /** Maximum wait time (ms) for the injector to reach "Started" status.
   *  Starting a real injector container is async and can take time. */
  private readonly STATUS_CHANGE_TIMEOUT = 240_000;

  constructor(private page: Page) {}

  async waitForLoad(): Promise<void> {
    await this.page.waitForURL('**/integrations/injectors/**');
  }

  /** ActionButton "Start" - visible when the instance is not yet starting */
  get startButton(): Locator {
    return this.page.getByRole('button', { name: 'Start' });
  }

  /** Status chip showing "Started" */
  get startedChip(): Locator {
    return this.page.getByText('Started', { exact: true });
  }

  async clickStart(): Promise<void> {
    await this.startButton.waitFor({ state: 'visible' });
    await this.startButton.click();
  }

  /**
   * Polls until the status chip shows "Started".
   * Requires the actual injector agent to start; may take up to STATUS_CHANGE_TIMEOUT ms.
   */
  async waitForStarted(): Promise<void> {
    await expect(async () => {
      await this.page.reload();
      await expect(this.startedChip).toBeVisible();
    }).toPass({
      intervals: [5_000],
      timeout: this.STATUS_CHANGE_TIMEOUT,
    });
  }

  /**
   * Waits until a div containing `specificItem` is visible.
   * The backend populates configurations asynchronously once the injector is running.
   */
  async waitForConfigurations(specificItem: string, timeout = TIMEOUT): Promise<void> {
    await this.page.locator('div', { hasText: specificItem }).first().waitFor({
      state: 'visible',
      timeout,
    });
  }
}

export default InjectorInstancePage;
