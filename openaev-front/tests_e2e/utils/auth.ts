import * as fs from 'node:fs';

import { type APIRequestContext, type PlaywrightWorkerArgs } from '@playwright/test';

import { type Persona, PERSONAS } from './personas';
import appUrl from './url';

// Path to the persisted Playwright storage state (cookies + localStorage) of a persona.
export const authFile = (persona: Persona) => `tests_e2e/.auth/${persona}.json`;

/**
 * Reads the XSRF-TOKEN cookie value from a stored auth state.
 * auth.setup.ts ensures this cookie is present after login.
 */
const readCsrfToken = (file: string): string | null => {
  try {
    const state = JSON.parse(fs.readFileSync(file, 'utf-8'));
    const cookie = (state.cookies ?? []).find(
      (c: {
        name: string;
        value: string;
      }) => c.name === 'XSRF-TOKEN',
    );
    return cookie ? decodeURIComponent(cookie.value) : null;
  } catch {
    return null;
  }
};

/**
 * API context logged in as `persona`. Playwright sends the XSRF-TOKEN *cookie*
 * from the storage state, but the double-submit pattern also requires the
 * matching *header*.
 */
export const newRequestContextAs = (playwright: PlaywrightWorkerArgs['playwright'], persona: Persona) => {
  const file = authFile(persona);
  const token = readCsrfToken(file);
  return playwright.request.newContext({
    storageState: file,
    extraHTTPHeaders: token ? { 'X-XSRF-TOKEN': token } : {},
  });
};

/**
 * The login endpoint is CSRF-ignored so no XSRF-TOKEN cookie exists yet.
 * Any POST goes through CsrfFilter, and its response (even a 403) sets the
 * XSRF-TOKEN cookie, which storageState() then persists.
 */
export const fetchCsrfCookie = async (request: APIRequestContext) => {
  await request.post('/api/scenarios/search', { data: {} });
};

/**
 * Logs `persona` in through the API and saves its session to its auth file.
 * An API context has no page, but it keeps the origins of its initial storage
 * state and writes them back: `localStorage` entries land in the auth file as
 * if the browser had set them on the app origin.
 */
export const loginWithApi = async (
  playwright: PlaywrightWorkerArgs['playwright'],
  persona: Persona,
  localStorage: {
    name: string;
    value: string;
  }[] = [],
) => {
  const { email, password } = PERSONAS[persona];
  const request = await playwright.request.newContext({
    storageState: {
      cookies: [],
      origins: [{
        origin: new URL(appUrl()).origin,
        localStorage,
      }],
    },
  });
  try {
    const response = await request.post('/api/login', {
      data: {
        login: email,
        password,
      },
    });
    if (!response.ok()) {
      throw new Error(`Failed to log in as ${persona} (${email}): ${response.status()} - ${await response.text()}`);
    }
    await fetchCsrfCookie(request);
    await request.storageState({ path: authFile(persona) });
  } finally {
    await request.dispose();
  }
};
