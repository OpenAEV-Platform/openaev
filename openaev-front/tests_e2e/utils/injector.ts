import { type Page } from '@playwright/test';

import CatalogPage from '../model/integrations/CatalogPage';
import InjectorInstancePage from '../model/integrations/InjectorInstancePage';
import InjectorsListPage from '../model/integrations/InjectorsListPage';
import { tenantUrl } from './url';

interface DeployAndStartInjectorOptions {
  connectorTitle: string;
  displayName: string;
  tenantId?: string;
}

const deployAndStartInjector = async (
  page: Page,
  { connectorTitle, displayName, tenantId }: DeployAndStartInjectorOptions,
): Promise<void> => {
  const catalogPage = new CatalogPage(page);
  await page.goto(tenantUrl('/admin/integrations/available', tenantId));
  await catalogPage.waitForLoad();
  await catalogPage.searchConnector(connectorTitle);
  await catalogPage.clickDeployOnConnector(connectorTitle);
  await catalogPage.fillDisplayName(displayName);
  await catalogPage.submitInstall();

  const injectorsListPage = new InjectorsListPage(page);
  await page.goto(tenantUrl('/admin/integrations/deployed', tenantId));
  await injectorsListPage.waitForLoad();
  await injectorsListPage.waitForConnectorToAppear(displayName);
  await injectorsListPage.clickOnInjector(displayName);

  const injectorInstancePage = new InjectorInstancePage(page);
  await injectorInstancePage.waitForLoad();
  await injectorInstancePage.clickStart();
  await injectorInstancePage.waitForStarted();
};

export default deployAndStartInjector;
