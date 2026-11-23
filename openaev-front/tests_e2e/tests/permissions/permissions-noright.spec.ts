import { expect } from '@playwright/test';

import { test } from '../../fixtures';
import LeftMenuComponent from '../../model/LeftMenuComponent';
import { PERSONAS } from '../../utils/personas';
import { tenantApiPath, tenantUrl } from '../../utils/url';

test.describe('No Right persona', () => {
  test.use({ persona: 'noright' });

  test('API calls run as the noright', async ({ request }) => {
    const response = await request.get(tenantApiPath('/api/me'));

    expect(response.ok()).toBeTruthy();
    expect((await response.json()).user_email).toBe(PERSONAS.noright.email);
  });

  test('the menu has only home and getting started link', async ({ page }) => {
    const leftMenu = new LeftMenuComponent(page);

    await page.goto(tenantUrl('/'));
    const allNavLinks = leftMenu.getAllNavLinks();
    await expect(allNavLinks).toHaveText(['Home', 'Getting Started']);

    await expect(page.getByRole('alert')).toContainText('You dont have the right access');
  });

  for (const path of ['/admin', '/admin/scenarios', '/admin/simulations', '/admin/atomic_testings']) {
    test(`the ${path} list is blocked by its route guard`, async ({ page }) => {
      await page.goto(tenantUrl(path));

      await expect(page.getByRole('alert')).toContainText('You dont have the right access');
    });
  }
});
