import { type Locator, type Page } from '@playwright/test';

import { TIMEOUT } from '../../utils/constants';

/**
 * Wraps the LeftBarTenantSwitcher component interactions.
 *
 * The switcher is a rail row (`data-testid="tenant-switcher"`) that opens the
 * design system `Menu`; the panel is portalled to the end of the document, so
 * its rows are located globally and not under `.app-navbar`. Its rows are real
 * links (switching tenant is a URL navigation), so this object clicks anchors,
 * not menu buttons.
 *
 * The trigger keeps its test id in both rail states, and without a validated
 * Enterprise Edition licence it is the same row minus the menu — it only opens
 * the upsell dialog and lists nothing.
 */
class TenantSwitcherComponent {
  constructor(private page: Page) {}

  get switcher() {
    return this.page.getByTestId('tenant-switcher');
  }

  /**
   * The trigger row. It carries its own test id in both rail states; the
   * tenant name is only used to disambiguate when a caller passes one (the
   * label stays in the DOM, visually hidden, when the rail is collapsed).
   */
  private triggerFor(currentTenantName?: string): Locator {
    if (!currentTenantName) {
      return this.switcher;
    }
    return this.switcher.filter({ hasText: currentTenantName }).first();
  }

  /**
   * Opens the tenant list.
   * Works regardless of whether the left bar is expanded or collapsed.
   */
  async openSwitcher(currentTenantName?: string): Promise<void> {
    const trigger = this.triggerFor(currentTenantName);

    await trigger.waitFor({
      state: 'visible',
      timeout: TIMEOUT,
    });
    await trigger.click();

    await this.popoverTenantItems.first().waitFor({
      state: 'visible',
      timeout: TIMEOUT,
    });
  }

  /**
   * All tenant rows of the open switcher — anchors inside the portalled menu
   * panel. Call {@link openSwitcher} first.
   */
  get popoverTenantItems() {
    return this.page.getByTestId('tenant-switcher-option');
  }

  /**
   * Clicks a specific tenant by name in the open switcher.
   */
  async selectTenantByName(tenantName: string): Promise<void> {
    await this.popoverTenantItems.filter({ hasText: tenantName }).first().click();
  }
}
export default TenantSwitcherComponent;
