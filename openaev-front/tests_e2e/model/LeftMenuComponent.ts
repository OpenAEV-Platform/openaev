import { type Page } from '@playwright/test';

/**
 * The navigation rows are real links rendered by the design system's `Navbar`
 * (they used to be MUI menu items, hence the previous `menuitem` role).
 */
class LeftMenuComponent {
  constructor(private page: Page) {
  }

  private navLinkByName(name: string) {
    return this.page.getByRole('link', {
      name,
      exact: true,
    });
  }

  private getMainNavigation() {
    return this.page.getByLabel('Main navigation', { exact: true });
  }

  // The header logo is also a link named "Home", but it shows only an image:
  // keeping the links with text leaves the navigation rows
  getAllNavLinks() {
    return this.getMainNavigation().getByRole('link').filter({ hasText: /\S/ });
  }

  goToAssets() {
    return this.navLinkByName('Assets').click();
  }

  goToContracts() {
    return this.navLinkByName('Integrations').click();
  }

  goToThreatArsenal() {
    return this.navLinkByName('Threat Arsenal').click();
  };

  goToTeams() {
    return this.navLinkByName('Persons').click();
  }
}

export default LeftMenuComponent;
