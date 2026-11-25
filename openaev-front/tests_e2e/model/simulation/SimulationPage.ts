import { type Locator, type Page } from '@playwright/test';

import { tenantUrl } from '../../utils/url';

class SimulationPage {
  readonly page: Page;
  readonly targetListContainer: Locator;
  readonly endpointTracesContainer: Locator;
  readonly noTracesMessage: Locator;

  constructor(page: Page) {
    this.page = page;
    this.targetListContainer = page.getByTestId('target-list-section');
    this.endpointTracesContainer = page.getByTestId('endpoint-traces-container');
    // Shown once the selected target's traces are loaded and there is none. Scoped to the Execution section:
    // the Terminal view section below it shows the same message.
    this.noTracesMessage = page.locator('section')
      .filter({ has: page.getByText('Execution', { exact: true }) })
      .getByText('No traces on this target.');
  }

  async gotoInject(simulationId: string, injectId: string) {
    await this.page.goto(tenantUrl(`/admin/simulations/${simulationId}/injects/${injectId}`));
  }

  getAssetByName(name: string): Locator {
    return this.targetListContainer.getByRole('button').filter({ hasText: name });
  }
}

export default SimulationPage;
