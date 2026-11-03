import { type Page } from '@playwright/test';

import EndpointApiHelpers, { type EndpointFields, randomMacAddress } from '../../api-helpers/EndpointApiHelpers';
import { expect, test } from '../../fixtures';
import { tenantUrl } from '../../utils/url';

/**
 * The assets list refreshes from the platform stream: every change below is made through the API,
 * outside the page, and must show without reloading it. The list is narrowed by a text search on a
 * token unique to the test, so the rows it creates are the only ones listed. A row is found by its
 * selection checkbox, named after the asset.
 */
// Above the list's refetch throttle (10 s, `assetQueries.list`): changes close together wait for it.
const LIVE_TIMEOUT = 20_000;

// Set on the window once the page is loaded: still there at the end means no reload happened.
const markPage = (page: Page) => page.evaluate(() => {
  (window as unknown as { liveMarker: boolean }).liveMarker = true;
});
const expectNoReload = async (page: Page) => {
  expect(await page.evaluate(() => (window as unknown as { liveMarker?: boolean }).liveMarker)).toBe(true);
};

const newFields = (token: string): EndpointFields => ({
  name: `E2E list ${token}`,
  hostname: `e2e-list-${token}`,
  macAddress: randomMacAddress(),
});

// The list searched on the test's token, once its first page is loaded.
const openList = async (page: Page, token: string) => {
  const listed = page.waitForResponse(response => response.url().includes('/assets/search')
    && (response.request().postData() ?? '').includes(token));
  await page.goto(tenantUrl(`/admin/assets?search=${encodeURIComponent(token)}`));
  await listed;
  await markPage(page);
};

const row = (page: Page, name: string) => page.getByRole('checkbox', {
  name,
  exact: true,
});

test.describe('Assets list live updates', () => {
  // Every asset a test created, deleted afterwards even when the test failed.
  const createdAssetIds: string[] = [];

  test.afterEach(async ({ request }) => {
    const api = new EndpointApiHelpers(request);
    await Promise.all(createdAssetIds.splice(0).map(id => api.deleteAssetIfPresent(id)));
  });

  test('given_anOpenAssetsList_should_showCreatedAndRenamedAssetsWithoutReload', async ({ page, request }) => {
    // Arrange
    const api = new EndpointApiHelpers(request);
    const token = `${Date.now()}`;
    const fields = newFields(token);
    await openList(page, token);

    // Act: created
    const created = await api.createAgentlessEndpoint(fields);
    createdAssetIds.push(created.asset_id);

    // Assert
    await expect(row(page, fields.name)).toBeVisible({ timeout: LIVE_TIMEOUT });

    // Act: renamed
    const renamed = `${fields.name} renamed`;
    await api.updateEndpoint(created.asset_id, {
      ...fields,
      name: renamed,
    });

    // Assert
    await expect(row(page, renamed)).toBeVisible({ timeout: LIVE_TIMEOUT });
    await expect(row(page, fields.name)).toBeHidden();
    await expectNoReload(page);
  });

  test('given_anOpenAssetsList_should_dropADeletedAssetWithoutReload', async ({ page, request }) => {
    // Arrange
    const api = new EndpointApiHelpers(request);
    const token = `${Date.now()}`;
    const fields = newFields(token);
    const created = await api.createAgentlessEndpoint(fields);
    createdAssetIds.push(created.asset_id);
    await openList(page, token);
    await expect(row(page, fields.name)).toBeVisible();

    // Act
    await api.deleteAsset(created.asset_id);

    // Assert
    await expect(row(page, fields.name)).toBeHidden({ timeout: LIVE_TIMEOUT });
    await expectNoReload(page);
  });
});
