import { type Page } from '@playwright/test';

import { TIMEOUT } from '../../utils/constants';

/**
 * Wraps the LeftBarTenantSwitcher component interactions.
 * The switcher is a MenuItem in the left navigation bar that opens a Popover
 * listing all accessible tenants.
 */
class TenantSwitcherComponent {
  constructor(private page: Page) {}
  /**
   * Expands the left bar if it is currently collapsed.
   * Uses count() (DOM presence) instead of isVisible() to reliably detect
   * the collapsed state even when the button is partially obscured.
   */
  async expandIfCollapsed(): Promise<void> {
    const expandButton = this.page.getByRole('menuitem', { name: 'Expand menu' });
    if ((await expandButton.count()) > 0) {
      await expandButton.click();
      // Wait for the animation to finish — the toggle flips to "Collapse menu"
      await this.page.getByRole('menuitem', { name: 'Collapse menu' }).waitFor({
        state: 'visible',
        timeout: TIMEOUT,
      });
    }
  }

  /**
   * Opens the tenant-switcher popover by clicking the left-bar button that
   * shows the current tenant name.
   * Expands the left bar first if it is collapsed (tenant name not visible).
   *
   * @param currentTenantName  The displayed name of the active tenant.
   */
  async openSwitcher(currentTenantName: string): Promise<void> {
    await this.expandIfCollapsed();
    const tenantMenuItemByName = this.page.getByRole('menuitem').filter({ hasText: currentTenantName }).first();
    const tenantMenuItemByIcon = this.page
      .getByRole('menuitem')
      .filter({ has: this.page.locator('svg[data-testid="HomeWorkOutlinedIcon"]') })
      .first();

    if ((await tenantMenuItemByName.count()) > 0 && (await tenantMenuItemByName.isVisible())) {
      await tenantMenuItemByName.click();
    } else {
      await tenantMenuItemByIcon.waitFor({
        state: 'visible',
        timeout: TIMEOUT,
      });
      await tenantMenuItemByIcon.click();
    }

    await this.page.locator('.MuiPopover-root').last().waitFor({
      state: 'visible',
      timeout: TIMEOUT,
    });
  }

  /**
   * All tenant menu items inside the currently open switcher popover.
   * Call {@link openSwitcher} first to open the popover.
   */
  get popoverTenantItems() {
    return this.page.locator('.MuiPopover-root').last().getByRole('menuitem');
  }

  /**
   * Clicks a specific tenant by name from the open switcher popover.
   */
  async selectTenantByName(tenantName: string): Promise<void> {
    const popover = this.page.locator('.MuiPopover-root').last();
    await popover.waitFor({ state: 'visible' });
    await popover.getByRole('menuitem').filter({ hasText: tenantName }).click();
  }
}
export default TenantSwitcherComponent;
