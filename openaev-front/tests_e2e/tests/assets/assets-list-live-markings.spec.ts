import { type APIRequestContext, expect, type Page } from '@playwright/test';

import EndpointApiHelpers, { type EndpointFields, randomMacAddress } from '../../api-helpers/EndpointApiHelpers';
import GroupApiHelpers from '../../api-helpers/GroupApiHelpers';
import MarkingDefinitionApiHelpers from '../../api-helpers/MarkingDefinitionApiHelpers';
import { test } from '../../fixtures';
import { newRequestContextAs } from '../../utils/auth';
import { PERSONAS } from '../../utils/personas';
import { tenantUrl } from '../../utils/url';

/**
 * The assets list follows marking and clearance changes made outside the page. The reader is the
 * manager persona, whose group (Manager) grants TLP:AMBER; the admin, cleared for TLP:RED, makes
 * every change through the API. The page must follow without reloading (MARKING preview on, as in
 * the `ci` profile).
 */
test.use({ persona: 'manager' });

// Above the list's refetch throttle (30 s, `assetQueries.list`): changes close together wait for it.
const LIVE_TIMEOUT = 40_000;

const markPage = (page: Page) => page.evaluate(() => {
  (window as unknown as { liveMarker: boolean }).liveMarker = true;
});
const expectNoReload = async (page: Page) => {
  expect(await page.evaluate(() => (window as unknown as { liveMarker?: boolean }).liveMarker)).toBe(true);
};

const newFields = (token: string): EndpointFields => ({
  name: `E2E marking ${token}`,
  hostname: `e2e-marking-${token}`,
  macAddress: randomMacAddress(),
});

const openList = async (page: Page, token: string) => {
  const listed = page.waitForResponse(response => response.url().includes('/assets/search')
    && (response.request().postData() ?? '').includes(token));
  await page.goto(tenantUrl(`/admin/assets?search=${encodeURIComponent(token)}`));
  await listed;
  await markPage(page);
};

// A row is found by its selection checkbox, named after the asset.
const rowCheckbox = (page: Page, name: string) => page.getByRole('checkbox', {
  name,
  exact: true,
});
const row = (page: Page, name: string) => page.getByRole('listitem').filter({ has: rowCheckbox(page, name) });

test.describe('Assets list live markings', () => {
  let admin: APIRequestContext;
  let red: string;
  let managerGroupId: string;
  let managerGroupMarkings: string[];
  const createdAssetIds: string[] = [];

  test.beforeAll(async ({ playwright }) => {
    admin = await newRequestContextAs(playwright, 'admin');
    red = await new MarkingDefinitionApiHelpers(admin).findIdByDefinition('TLP:RED');
    const group = await new GroupApiHelpers(admin).findGroupByName(PERSONAS.manager.groupName);
    managerGroupId = group.group_id;
    managerGroupMarkings = group.group_markings;
  });

  test.afterEach(async () => {
    // The Manager group is shared by every suite: its clearance is restored even when a test failed.
    await new GroupApiHelpers(admin).updateGroupMarkings(managerGroupId, managerGroupMarkings);
    const api = new EndpointApiHelpers(admin);
    await Promise.all(createdAssetIds.splice(0).map(id => api.deleteAssetIfPresent(id)));
  });

  test.afterAll(async () => {
    await admin.dispose();
  });

  test('given_anAssetMarkedAboveTheReadersClearance_should_dropItThenShowItBackWithoutReload', async ({ page }) => {
    // Arrange
    const api = new EndpointApiHelpers(admin);
    const token = `${Date.now()}`;
    const fields = newFields(token);
    const created = await api.createAgentlessEndpoint(fields);
    createdAssetIds.push(created.asset_id);
    await openList(page, token);
    await expect(rowCheckbox(page, fields.name)).toBeVisible();

    // Act: marked TLP:RED, above the reader's TLP:AMBER
    await api.updateAssetMarkings(created.asset_id, [red]);

    // Assert
    await expect(rowCheckbox(page, fields.name)).toBeHidden({ timeout: LIVE_TIMEOUT });

    // Act: the marking removed
    await api.updateAssetMarkings(created.asset_id, []);

    // Assert
    await expect(rowCheckbox(page, fields.name)).toBeVisible({ timeout: LIVE_TIMEOUT });
    await expectNoReload(page);
  });

  test('given_theReadersClearanceRaisedThenLowered_should_showTheAssetWithItsMarkingThenDropItWithoutReload', async ({ page }) => {
    // Arrange: an asset marked TLP:RED, which the reader does not see
    const api = new EndpointApiHelpers(admin);
    const groups = new GroupApiHelpers(admin);
    const token = `${Date.now()}`;
    const fields = newFields(token);
    const created = await api.createAgentlessEndpoint(fields);
    createdAssetIds.push(created.asset_id);
    await api.updateAssetMarkings(created.asset_id, [red]);
    await openList(page, token);
    await expect(rowCheckbox(page, fields.name)).toBeHidden();

    // Act: the reader's group now grants TLP:RED
    await groups.updateGroupMarkings(managerGroupId, [...managerGroupMarkings, red]);

    // Assert: the row comes back with its marking, and the marking picker offers it
    await expect(row(page, fields.name).getByText('TLP:RED', { exact: true })).toBeVisible({ timeout: LIVE_TIMEOUT });
    await row(page, fields.name).getByRole('button', { name: 'More actions' }).click();
    await page.getByRole('menuitem', { name: 'Update' }).click();
    await page.getByRole('combobox', { name: 'Markings' }).click();
    await expect(page.getByRole('option', {
      name: 'TLP:RED',
      exact: true,
    })).toBeVisible();
    await page.keyboard.press('Escape');
    await page.keyboard.press('Escape');

    // Act: the grant removed
    await groups.updateGroupMarkings(managerGroupId, managerGroupMarkings);

    // Assert
    await expect(rowCheckbox(page, fields.name)).toBeHidden({ timeout: LIVE_TIMEOUT });
    await expectNoReload(page);
  });
});
