import { expect } from '@playwright/test';

import { test } from '../../fixtures';
import { MARKING_DEFINITIONS_URL } from '../../utils/markingDefinitions.constants';
import { PERSONAS } from '../../utils/personas';
import { tenantApiPath } from '../../utils/url';

test.describe('Manager persona', () => {
  test.use({ persona: 'manager' });

  test('API calls run as the manager', async ({ request }) => {
    const response = await request.get(tenantApiPath('/api/me'));

    expect(response.ok()).toBeTruthy();
    expect((await response.json()).user_email).toBe(PERSONAS.manager.email);
  });

  // Marking definitions stay Admin-only: the Manager role has no ACCESS_MARKING_DEFINITION.
  test('the marking definitions page answers with no access', async ({ page }) => {
    await page.goto(MARKING_DEFINITIONS_URL);

    await expect(page.getByRole('alert')).toContainText('You dont have the right access');
  });
});
