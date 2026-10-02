import { expect, type Page } from '@playwright/test';

import { TIMEOUT } from '../../utils/constants';

/**
 * Wraps the LeftBarTenantSwitcher component interactions.
 * The switcher is a MenuItem in the left navigation bar that opens a Popover
 * listing all accessible tenants.
 */
class TenantSwitcherComponent {
  constructor(private page: Page) {}

  get switcher() {
    return this.page.getByTestId('tenant-switcher');
  }

  get switcherPopover() {
    return this.page.getByTestId('tenant-switcher-popover');
  }

  /**
   * Opens the tenant-switcher popover by clicking the icon-based menu item.
   * Works regardless of whether the left bar is expanded or collapsed.
   */
  async openSwitcher(_currentTenantName?: string): Promise<void> {
    const switcher = this.switcher;

    await switcher.waitFor({
      state: 'attached',
      timeout: TIMEOUT,
    });
    await switcher.waitFor({
      state: 'visible',
      timeout: TIMEOUT,
    });

    await expect(async () => {
      if (await this.switcherPopover.isVisible().catch(() => false)) {
        return;
      }
      await switcher.click();
      await this.switcherPopover.waitFor({
        state: 'visible',
        timeout: TIMEOUT,
      });
    }).toPass({
      intervals: [200, 500, 1_000],
      timeout: TIMEOUT,
    });
  }

  /**
   * All tenant menu items inside the currently open switcher popover.
   * Call {@link openSwitcher} first to open the popover.
   */
  get popoverTenantItems() {
    return this.switcherPopover.getByRole('menuitem');
  }

  /**
   * Clicks a specific tenant by name from the open switcher popover.
   */
  async selectTenantByName(tenantName: string): Promise<void> {
    const popover = this.switcherPopover;
    await popover.waitFor({ state: 'visible' });
    await popover.getByRole('menuitem').filter({ hasText: tenantName }).click();
  }
}
export default TenantSwitcherComponent;
