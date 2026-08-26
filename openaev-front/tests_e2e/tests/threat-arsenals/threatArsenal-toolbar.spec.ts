import { expect, test } from '@playwright/test';

import LeftMenuComponent from '../../model/LeftMenuComponent';
import ThreatArsenalListPage from '../../model/threat-arsenals/ThreatArsenalListPage';
import { tenantUrl } from '../../utils/url';

/**
 * The list toolbar used to be a single row: selection, search, filters, sort,
 * page size and pagination all side by side. At a 1400px viewport that ran past
 * the window — the pagination arrows sat at x=1471 with no horizontal scroll to
 * reach them. It is now three rows: what acts on the whole list, what narrows
 * it, then the filter chips.
 */
test.describe('Threat Arsenal list toolbar', () => {
  let leftMenu: LeftMenuComponent;
  let list: ThreatArsenalListPage;

  test.beforeEach(async ({ page }) => {
    await page.setViewportSize({
      width: 1400,
      height: 950,
    });
    leftMenu = new LeftMenuComponent(page);
    list = new ThreatArsenalListPage(page);

    await page.goto(tenantUrl('/admin'));
    await leftMenu.goToThreatArsenal();
    await list.waitForLoad();
  });

  for (const view of ['grid', 'list'] as const) {
    test(`the ${view} view splits the toolbar into a selection row and a filter row`, async ({ page }) => {
      await list.switchToView(view);

      const selectionRow = page.getByTestId('toolbar-selection-row');
      const filtersRow = page.getByTestId('toolbar-filters-row');
      await expect(selectionRow).toBeVisible();
      await expect(filtersRow).toBeVisible();

      // What acts on the whole list is on the first row...
      await expect(selectionRow.locator('.MuiTablePagination-root')).toHaveCount(1);
      // ...and what narrows it is not.
      await expect(selectionRow.getByPlaceholder('Search these results...')).toHaveCount(0);
      await expect(filtersRow.getByPlaceholder('Search these results...')).toHaveCount(1);

      // The filter row sits strictly below the selection row.
      const selectionBox = await selectionRow.boundingBox();
      const filtersBox = await filtersRow.boundingBox();
      expect(selectionBox).not.toBeNull();
      expect(filtersBox).not.toBeNull();
      expect(filtersBox!.y).toBeGreaterThanOrEqual(selectionBox!.y + selectionBox!.height);
    });

    test(`the ${view} view keeps the toolbar inside the window at 1400px`, async ({ page }) => {
      await list.switchToView(view);

      const overflow = await page.evaluate(() => {
        const scroller = document.scrollingElement!;
        return scroller.scrollWidth - scroller.clientWidth;
      });
      expect(overflow).toBe(0);

      // The pagination controls are the ones that used to fall off the edge.
      const nextPage = page.getByRole('button', { name: 'Go to next page' });
      await expect(nextPage).toBeInViewport();
    });
  }

  test('the chips row shows only while a filter is applied', async ({ page }) => {
    await expect(page.getByTestId('toolbar-chips-row')).toHaveCount(0);

    await list.addFirstAvailableFilter();
    await expect(page.getByTestId('toolbar-chips-row')).toBeVisible();

    await list.clearFilters();
    await expect(page.getByTestId('toolbar-chips-row')).toHaveCount(0);
  });
});
