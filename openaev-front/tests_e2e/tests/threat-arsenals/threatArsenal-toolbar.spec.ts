import { expect, test } from '@playwright/test';

import LeftMenuComponent from '../../model/LeftMenuComponent';
import ThreatArsenalListPage from '../../model/threat-arsenals/ThreatArsenalListPage';
import { tenantUrl } from '../../utils/url';

/**
 * The list toolbar is a single row at every width: selection, search, filters,
 * sort, page size and pagination side by side. At a 1400px viewport it used to
 * run past the window — the pagination arrows sat at x=1471 with no horizontal
 * scroll to reach them (#7340).
 *
 * Two things keep it inside the window now: the toolbar decides who gives way
 * (the search and filter inputs compress, the sort and pagination never do),
 * and the create button no longer rides this row — it sits in the page header,
 * covered by threatArsenal-list-header.spec.ts.
 *
 * The assertion below is deliberately arrangement-agnostic: no horizontal
 * overflow, and the pagination reachable — in BOTH views, the card view being
 * the tight one since it carries the sort select the list view does not.
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
