import { type Page } from '@playwright/test';

class ContractPage {
  constructor(private page: Page) {}

  async goToPreviousPage() {
    await this.page.getByLabel('Go to previous page').click();
  }

  async goToNextPage() {
    await this.page.getByLabel('Go to next page').click();
  }
}

export default ContractPage;
