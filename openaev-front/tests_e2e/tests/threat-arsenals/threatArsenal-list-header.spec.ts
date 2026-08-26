import { expect, test } from '@playwright/test';

import LeftMenuComponent from '../../model/LeftMenuComponent';
import ThreatArsenalListPage from '../../model/threat-arsenals/ThreatArsenalListPage';
import { tenantUrl } from '../../utils/url';

/**
 * The create button used to ride the pagination row. The card view makes that
 * row wider than the list view — it adds the sort select there — so the button
 * was pushed off screen in cards only, with no way to scroll to it. It now sits
 * in the page header beside the import icons: one instance, both views.
 */
test.describe('Threat Arsenal list header', () => {
  let leftMenu: LeftMenuComponent;
  let threatArsenalList: ThreatArsenalListPage;

  test.beforeEach(async ({ page }) => {
    leftMenu = new LeftMenuComponent(page);
    threatArsenalList = new ThreatArsenalListPage(page);

    await page.goto(tenantUrl('/admin'));
    await leftMenu.goToThreatArsenal();
    await threatArsenalList.waitForLoad();
  });

  test('the create button is present exactly once in the grid view', async () => {
    await threatArsenalList.switchToGridView();

    await expect(threatArsenalList.addButton).toHaveCount(1);
    await expect(threatArsenalList.addButton).toBeVisible();
  });

  test('the create button is present exactly once in the list view', async () => {
    await threatArsenalList.switchToListView();

    await expect(threatArsenalList.addButton).toHaveCount(1);
    await expect(threatArsenalList.addButton).toBeVisible();
  });

  test('the create button is no longer inside the pagination row', async () => {
    await expect(threatArsenalList.paginationRow).toBeVisible();
    await expect(threatArsenalList.paginationRow.getByTestId('button-create')).toHaveCount(0);
  });
});
