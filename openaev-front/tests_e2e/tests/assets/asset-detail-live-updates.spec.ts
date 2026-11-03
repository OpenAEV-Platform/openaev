import { type Page } from '@playwright/test';

import EndpointApiHelpers, { type EndpointFields, randomMacAddress } from '../../api-helpers/EndpointApiHelpers';
import { expect, test } from '../../fixtures';
import { tenantUrl } from '../../utils/url';

/**
 * The asset detail page refreshes from the platform stream: every change below is made through the
 * API, outside the page, and must show without reloading it. TanStack refetches on focus and mount
 * too, but the page keeps both, so a pass within the timeout proves the stream path.
 */
const LIVE_TIMEOUT = 10_000;

// Set on the window once the page is loaded: still there at the end means no reload happened.
const markPage = (page: Page) => page.evaluate(() => {
  (window as unknown as { liveMarker: boolean }).liveMarker = true;
});
const expectNoReload = async (page: Page) => {
  expect(await page.evaluate(() => (window as unknown as { liveMarker?: boolean }).liveMarker)).toBe(true);
};

const newFields = (token: string): EndpointFields => ({
  name: `E2E live ${token}`,
  hostname: `e2e-live-${token}`,
  macAddress: randomMacAddress(),
});

test.describe('Asset detail live updates', () => {
  // Assets left by a failed test; deleting one already gone is a harmless 404.
  const createdAssetIds: string[] = [];

  test.afterEach(async ({ request }) => {
    const api = new EndpointApiHelpers(request);
    await Promise.all(createdAssetIds.splice(0).map(id => api.deleteAsset(id)));
  });

  test('given_anOpenAssetDetail_should_showRenamesAndBaseInformationChangesWithoutReload', async ({ page, request }) => {
    // Arrange
    const api = new EndpointApiHelpers(request);
    const fields = newFields(`${Date.now()}`);
    const created = await api.createAgentlessEndpoint(fields);
    createdAssetIds.push(created.asset_id);
    await page.goto(tenantUrl(`/admin/assets/${created.asset_id}`));
    await expect(page.getByText(fields.hostname, { exact: true })).toBeVisible();
    await markPage(page);
    const changed = {
      ...fields,
      name: `${fields.name} renamed`,
      hostname: `${fields.hostname}-renamed`,
    };

    // Act
    await api.updateEndpoint(created.asset_id, changed);

    // Assert: the title and the network information follow
    await expect(page.getByRole('heading', {
      name: changed.name,
      exact: true,
    })).toBeVisible({ timeout: LIVE_TIMEOUT });
    await expect(page.getByText(changed.hostname, { exact: true })).toBeVisible({ timeout: LIVE_TIMEOUT });
    await expectNoReload(page);
  });

  test('given_anOpenAssetDetail_should_showTheDeletedStateWithoutReload', async ({ page, request }) => {
    // Arrange
    const api = new EndpointApiHelpers(request);
    const fields = newFields(`${Date.now()}`);
    const created = await api.createAgentlessEndpoint(fields);
    createdAssetIds.push(created.asset_id);
    await page.goto(tenantUrl(`/admin/assets/${created.asset_id}`));
    await expect(page.getByText(fields.hostname, { exact: true })).toBeVisible();
    await markPage(page);

    // Act
    await api.deleteAsset(created.asset_id);

    // Assert
    await expect(page.getByText('This item does not exist or you are not allowed to view it.')).toBeVisible({ timeout: LIVE_TIMEOUT });
    await expectNoReload(page);
  });
});
