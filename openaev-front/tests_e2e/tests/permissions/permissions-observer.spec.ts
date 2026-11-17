import { expect } from '@playwright/test';

import { test } from '../../fixtures';
import LeftMenuComponent from '../../model/LeftMenuComponent';
import ThreatArsenalListPage from '../../model/threat-arsenals/ThreatArsenalListPage';
import { MARKING_DEFINITIONS_URL } from '../../utils/markingDefinitions.constants';
import { PERSONAS } from '../../utils/personas';
import { tenantApiPath, tenantUrl } from '../../utils/url';

test.describe('Observer persona', () => {
  test.use({ persona: 'observer' });

  test('API calls run as the observer', async ({ request }) => {
    const response = await request.get(tenantApiPath('/api/me'));

    expect(response.ok()).toBeTruthy();
    expect((await response.json()).user_email).toBe(PERSONAS.observer.email);
  });

  test('the threat arsenal has no create button', async ({ page }) => {
    const leftMenu = new LeftMenuComponent(page);
    const threatArsenalList = new ThreatArsenalListPage(page);

    await page.goto(tenantUrl('/admin'));
    await leftMenu.goToThreatArsenal();
    await threatArsenalList.waitForLoad();

    await expect(threatArsenalList.addButton).toHaveCount(0);
  });

  // Marking definitions stay Admin-only: the Observer role has no ACCESS_MARKING_DEFINITION.
  test('the marking definitions page answers with no access', async ({ page }) => {
    await page.goto(MARKING_DEFINITIONS_URL);

    await expect(page.getByRole('alert')).toContainText('You dont have the right access');
  });
});
