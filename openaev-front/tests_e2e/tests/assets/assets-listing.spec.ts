import { type APIRequestContext, expect } from '@playwright/test';

import { test } from '../../fixtures';
import EndpointListPage from '../../model/assets/EndpointListPage';
import { disableAnimations } from '../../utils/animations';
import { newRequestContextAs } from '../../utils/auth';
import { CLEARANCE_CASES, createMarkedAssets, deleteMarkedAssets, type MarkedAssets } from '../../utils/markedAssets';

test.describe('Assets - Listing', () => {
  // Seeded once as admin for every persona. Playwright reruns these hooks in a new worker after a failure.
  let adminRequest: APIRequestContext;
  let assets: MarkedAssets;
  let endpointListPage: EndpointListPage;
  // Shared by the three asset names: searching it brings all of them to the first page of the list.
  const suffix = `${Date.now()}-${Math.random()}`;

  test.beforeAll(async ({ playwright, getMarkingDefinitionId }) => {
    adminRequest = await newRequestContextAs(playwright, 'admin');
    assets = await createMarkedAssets(adminRequest, getMarkingDefinitionId, suffix);
  });

  test.beforeEach(async ({ page }) => {
    endpointListPage = new EndpointListPage(page);
    await disableAnimations(page);
    await endpointListPage.goto();

    // The list is paginated: without the search, an asset off the first page would pass the absence check.
    await endpointListPage.search(suffix);
  });

  test.afterAll(async () => {
    if (!adminRequest) {
      return;
    }
    if (assets) {
      await deleteMarkedAssets(adminRequest, assets);
    }
    await adminRequest.dispose();
  });

  for (const { persona, visibleAssets, invisibleAssets } of CLEARANCE_CASES) {
    test.describe(`as ${persona}`, () => {
      test.use({ persona });

      test('should list assets corresponding with the correct marking', async () => {
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
