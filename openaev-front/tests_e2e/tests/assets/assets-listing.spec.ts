import { type APIRequestContext, expect } from '@playwright/test';

import { type Endpoint } from '../../../src/utils/api-types';
import EndpointsApiHelpers from '../../api-helpers/EndpointsApiHelpers';
import { test } from '../../fixtures';
import EndpointListPage from '../../model/assets/EndpointListPage';
import { newRequestContextAs } from '../../utils/auth';
import disableAnimations from '../../utils/disableAnimations';
import { type Persona } from '../../utils/personas';
import { tenantUrl } from '../../utils/url';

type AssetKey = 'green' | 'amber' | 'red';

// Default group clearances (PresetTenantData.DEFAULT_GROUP_MARKINGS): Admin TLP:RED, Manager TLP:AMBER,
// Observer TLP:GREEN. A clearance also covers every lower marking of the same type.
const CASES: {
  persona: Persona;
  visibleAssets: AssetKey[];
  invisibleAssets: AssetKey[];
}[] = [
  {
    persona: 'admin',
    visibleAssets: ['green', 'amber', 'red'],
    invisibleAssets: [],
  },
  {
    persona: 'manager',
    visibleAssets: ['green', 'amber'],
    invisibleAssets: ['red'],
  },
  {
    persona: 'observer',
    visibleAssets: ['green'],
    invisibleAssets: ['amber', 'red'],
  },
];

test.describe('Assets - Listing', () => {
  // Seeded once as admin for every persona: a read-only persona cannot create them, and the data does not
  // depend on who reads it. Playwright reruns these hooks in a new worker after a failure.
  let adminRequest: APIRequestContext;
  let assets: Record<AssetKey, Endpoint>;
  // Shared by the three asset names: searching it brings all of them to the first page of the list.
  const suffix = `${Date.now()}-${Math.random()}`;

  test.beforeAll(async ({ playwright, getMarkingDefinitionId }) => {
    adminRequest = await newRequestContextAs(playwright, 'admin');
    const endpointApiHelpers = new EndpointsApiHelpers(adminRequest);

    const [green, amber, red] = await Promise.all([
      endpointApiHelpers.createEndpoint(`Asset Green ${suffix}`, [getMarkingDefinitionId('TLP:GREEN')]),
      endpointApiHelpers.createEndpoint(`Asset Amber ${suffix}`, [getMarkingDefinitionId('TLP:AMBER')]),
      endpointApiHelpers.createEndpoint(`Asset Red ${suffix}`, [getMarkingDefinitionId('TLP:RED')]),
    ]);
    assets = {
      green,
      amber,
      red,
    };
  });

  test.afterAll(async () => {
    if (!adminRequest) {
      return;
    }
    if (assets) {
      const endpointApiHelpers = new EndpointsApiHelpers(adminRequest);
      await Promise.all(Object.values(assets).map(asset => endpointApiHelpers.deleteEndpoint(asset.asset_id)));
    }
    await adminRequest.dispose();
  });

  for (const { persona, visibleAssets, invisibleAssets } of CASES) {
    test.describe(`as ${persona}`, () => {
      test.use({ persona });

      test('should list assets corresponding with the correct marking', async ({ page }) => {
        // Arrange
        const endpointListPage = new EndpointListPage(page);
        await disableAnimations(page);

        // Act
        await page.goto(tenantUrl('/admin/assets'));
        await endpointListPage.waitForLoad();
        // The list is paginated: without the search, an asset off the first page would pass the absence check.
        await endpointListPage.search(suffix);

        // Assert
        await Promise.all(visibleAssets.map(key =>
          expect(endpointListPage.getEndpointByName(assets[key].asset_name)).toBeVisible()));
        // Runs after the visible checks, so the list has rendered and an absence is meaningful.
        await Promise.all(invisibleAssets.map(key =>
          expect(endpointListPage.getEndpointByName(assets[key].asset_name)).toHaveCount(0, { timeout: 1_000 })));
      });
    });
  }
});
