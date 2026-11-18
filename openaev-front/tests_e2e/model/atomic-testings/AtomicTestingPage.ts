import { type Locator, type Page } from '@playwright/test';

import { tenantUrl } from '../../utils/url';

class AtomicTestingPage {
  readonly page: Page;
  // Header kebab: always offers "Update", whether the inject is ready or not (the
  // "Configure" shortcut only shows up while mandatory content is missing).
  readonly headerActionsButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.headerActionsButton = page.getByTestId('detail-hero').getByRole('button', { name: 'More actions' });
  }

  async goto(atomicTestingId: string) {
    await this.page.goto(tenantUrl(`/admin/atomic_testings/${atomicTestingId}`));
    await this.page.waitForLoadState('domcontentloaded');
  }

  async openUpdateInjectForm() {
    await this.headerActionsButton.click();
    await this.page.getByRole('menuitem', { name: 'Update' }).click();
  }
}

export default AtomicTestingPage;
