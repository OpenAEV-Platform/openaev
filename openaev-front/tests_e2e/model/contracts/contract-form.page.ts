import { type Page } from '@playwright/test';

class ContractFormPage {
  constructor(private page: Page) {
  }

  getContractTitles() {
    return this.page.getByRole('heading');
  }
}

export default ContractFormPage;
