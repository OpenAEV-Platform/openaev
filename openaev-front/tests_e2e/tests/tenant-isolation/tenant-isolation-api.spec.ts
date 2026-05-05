import { test, expect } from '@playwright/test';

/**
 * API-level E2E tests for tenant isolation (RLS).
 *
 * These tests verify that PostgreSQL Row-Level Security policies correctly
 * prevent cross-tenant data access at the HTTP API level — no browser needed.
 *
 * We use Node's native fetch (no cookie jar) instead of Playwright's request
 * context, because the backend sets JSESSIONID cookies that trigger CSRF
 * validation on subsequent requests (403 Forbidden).
 *
 * Prerequisites: Backend running with a fresh DB (Flyway migrations applied).
 */

const BASE_URL = process.env.API_URL ?? process.env.APP_URL ?? 'http://localhost:8080';
const API_TOKEN = process.env.ADMIN_TOKEN ?? '5ccddea0-613c-4a91-a602-6a4eb243d21c';

const headers: Record<string, string> = {
  Authorization: `Bearer ${API_TOKEN}`,
  'Content-Type': 'application/json',
};

/** Stateless HTTP helper — no cookie jar, no CSRF issues */
async function api(method: string, path: string, body?: unknown) {
  const resp = await fetch(`${BASE_URL}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  return resp;
}

test.describe('Tenant Isolation (RLS)', () => {
  let tenantXId: string;
  let tenantYId: string;

  test.beforeAll(async () => {
    // Create two tenants for isolation testing
    const respX = await api('POST', '/api/tenants', {
      tenant_name: `Tenant X-${Date.now()}`,
      tenant_description: 'RLS test tenant X',
    });
    const bodyX = await respX.json();
    expect(respX.ok, `Create Tenant X failed: ${JSON.stringify(bodyX)}`).toBeTruthy();
    tenantXId = bodyX.tenant_id;

    const respY = await api('POST', '/api/tenants', {
      tenant_name: `Tenant Y-${Date.now()}`,
      tenant_description: 'RLS test tenant Y',
    });
    const bodyY = await respY.json();
    expect(respY.ok, `Create Tenant Y failed: ${JSON.stringify(bodyY)}`).toBeTruthy();
    tenantYId = bodyY.tenant_id;
  });

  test.afterAll(async () => {
    // Cleanup — ignore errors (tenants may have been partially created)
    await api('DELETE', `/api/tenants/${tenantXId}`).catch(() => {});
    await api('DELETE', `/api/tenants/${tenantYId}`).catch(() => {});
  });

  test.describe('Scenarios', () => {
    let scenarioId: string;

    test('scenario created in tenant X should NOT be readable from tenant Y', async () => {
      // Arrange — create scenario in tenant X
      const createResp = await api('POST', `/api/tenants/${tenantXId}/scenarios`, {
        scenario_name: 'RLS Isolation Test Scenario',
      });
      const scenarioBody = await createResp.json();
      expect(createResp.ok, `Create scenario failed: ${JSON.stringify(scenarioBody)}`).toBeTruthy();
      scenarioId = scenarioBody.scenario_id;

      // Act — try to read from tenant Y
      const crossResp = await api('GET', `/api/tenants/${tenantYId}/scenarios/${scenarioId}`);

      // Assert — should be blocked by RLS
      expect(
        crossResp.status === 403 || crossResp.status === 404,
        `Expected 403 or 404 but got ${crossResp.status} — cross-tenant scenario read was NOT blocked`,
      ).toBeTruthy();
    });

    test('scenario created in tenant X should be readable from tenant X', async () => {
      // Act — read from same tenant
      const resp = await api('GET', `/api/tenants/${tenantXId}/scenarios/${scenarioId}`);

      // Assert
      expect(resp.status).toBe(200);
      const body = await resp.json();
      expect(body.scenario_name).toBe('RLS Isolation Test Scenario');
    });
  });

  test.describe('Tenant Groups (tenant scope)', () => {
    test('tenant group created in tenant X should NOT be readable from tenant Y', async () => {
      // Arrange
      const createResp = await api('POST', `/api/tenants/${tenantXId}/groups`, {
        group_name: 'RLS Isolated Group',
      });
      const groupBody = await createResp.json();
      expect(createResp.ok, `Create group failed: ${JSON.stringify(groupBody)}`).toBeTruthy();
      const groupId = groupBody.group_id;

      // Act — try to read from tenant Y
      const crossResp = await api('GET', `/api/tenants/${tenantYId}/groups/${groupId}`);

      // Assert — should be blocked by RLS (403 Forbidden) or not found (404 Not Found)
      expect(
        crossResp.status === 403 || crossResp.status === 404,
        `Expected 403 or 404 but got ${crossResp.status} — cross-tenant group read was NOT blocked`,
      ).toBeTruthy();
    });
  });

  test.describe('Platform Groups (shared)', () => {
    test('platform group should be accessible regardless of tenant', async () => {
      // Arrange
      const createResp = await api('POST', '/api/platform-groups', {
        platform_group_name: `RLS Shared Group ${Date.now()}`,
      });
      const pgBody = await createResp.json();
      expect(createResp.ok, `Create platform group failed: ${JSON.stringify(pgBody)}`).toBeTruthy();
      const groupId = pgBody.platform_group_id;

      // Act — read without tenant context (platform-level)
      const readResp = await api('GET', `/api/platform-groups/${groupId}`);

      // Assert — platform groups are shared, always accessible
      expect(readResp.status).toBe(200);
    });
  });

  test.describe('Tenant Settings', () => {
    // FIXME: groups are dual-scope — RLS not yet applied, isolation relies on app-layer filters
    test.fixme('theme set in tenant X should NOT leak into tenant Y settings', async () => {
      // Arrange — set a distinctive theme color in tenant X
      const updateResp = await api('PUT', `/api/tenants/${tenantXId}/tenant-settings/theme/light`, {
        theme_primary: '#E2E_TEST_COLOR',
      });
      expect(updateResp.ok, `Update theme failed: ${await updateResp.text()}`).toBeTruthy();

      // Act — read settings from tenant Y
      const readResp = await api('GET', `/api/tenants/${tenantYId}/tenant-settings`);
      expect(readResp.ok).toBeTruthy();

      // Assert — tenant Y settings should NOT contain tenant X's theme
      const body = await readResp.text();
      expect(body).not.toContain('#E2E_TEST_COLOR');
    });

    // FIXME: home dashboard resolves via tenant_settings which is not yet RLS-protected
    test.fixme('home dashboard set in tenant X should NOT be readable from tenant Y', async () => {
      // Arrange — create a custom dashboard in tenant X
      const dashResp = await api('POST', `/api/tenants/${tenantXId}/custom-dashboards`, {
        custom_dashboard_name: 'RLS Home Dashboard Test',
      });
      const dashBody = await dashResp.json();
      expect(dashResp.ok, `Create dashboard failed: ${JSON.stringify(dashBody)}`).toBeTruthy();
      const dashboardId = dashBody.custom_dashboard_id;

      // Arrange — read current tenant X settings to preserve existing values
      const currentResp = await api('GET', `/api/tenants/${tenantXId}/tenant-settings`);
      const currentSettings = await currentResp.json();

      // Arrange — set home dashboard while preserving other fields
      const settingsResp = await api('PUT', `/api/tenants/${tenantXId}/tenant-settings`, {
        platform_name: currentSettings.platform_name ?? 'Tenant X',
        platform_theme: currentSettings.platform_theme ?? 'dark',
        platform_lang: currentSettings.platform_lang ?? 'auto',
        platform_home_dashboard: dashboardId,
        platform_scenario_dashboard: currentSettings.platform_scenario_dashboard ?? '',
        platform_simulation_dashboard: currentSettings.platform_simulation_dashboard ?? '',
      });
      const settingsBody = await settingsResp.json();
      expect(settingsResp.ok, `Set home dashboard failed: ${JSON.stringify(settingsBody)}`).toBeTruthy();

      // Act — read home dashboard from tenant Y
      const crossResp = await api('GET', `/api/tenants/${tenantYId}/tenant-settings/home-dashboard`);

      // Assert — tenant Y should NOT see tenant X's home dashboard
      expect(
        crossResp.status === 403 || crossResp.status === 404,
        `Expected 403 or 404 but got ${crossResp.status} — cross-tenant home dashboard read was NOT blocked`,
      ).toBeTruthy();


    });
  });
});
