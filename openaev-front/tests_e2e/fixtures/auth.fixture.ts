import { test as base } from '@playwright/test';

import { authFile, newRequestContextAs } from '../utils/auth';
import { type Persona } from '../utils/personas';

/**
 * Runs a test as a persona (admin by default): `test.use({ persona: 'observer' })`.
 * Both the browser session and the `request` fixture follow it, and every
 * request carries the X-XSRF-TOKEN header the double-submit CSRF check needs.
 */
const authFixture = base.extend<{ persona: Persona }>({
  persona: ['admin', { option: true }],

  storageState: async ({ persona }, use) => {
    await use(authFile(persona));
  },

  request: async ({ playwright, persona }, use) => {
    const context = await newRequestContextAs(playwright, persona);
    await use(context);
    await context.dispose();
  },
});

export default authFixture;
