import { type Page } from '@playwright/test';

/**
 * The navigation rows are real links rendered by the design system's `Navbar`
 * (they used to be MUI menu items, hence the previous `menuitem` role).
 */
class LeftMenuComponent {
  constructor(private page: Page) {
  }

  private navLink(name: string) {
    return this.page.getByRole('link', {
      name,
      exact: true,
    });
  }

  goToAssets() {
    return this.navLink('Assets').click();
  }

  goToContracts() {
    return this.navLink('Integrations').click();
  }

  goToThreatArsenal() {
    return this.navLink('Threat Arsenal').click();
  };

  async goToTeams() {
    return this.navLink('Persons').click();
  }
}

export default LeftMenuComponent;
