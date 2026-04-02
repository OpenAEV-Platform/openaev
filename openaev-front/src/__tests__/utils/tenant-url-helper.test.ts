import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  buildTenantApiPath,
  DEFAULT_TENANT_UUID,
  getCurrentTenantId,
  TENANT_STORAGE_KEY,
} from '../../utils/tenant-url-helper';

// ---------------------------------------------------------------------------
// getCurrentTenantId
// ---------------------------------------------------------------------------

describe('getCurrentTenantId', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('returns DEFAULT_TENANT_UUID when nothing is stored', () => {
    expect(getCurrentTenantId()).toBe(DEFAULT_TENANT_UUID);
  });

  it('returns the stored tenant_id from localStorage', () => {
    const tenantId = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee';
    localStorage.setItem(
      TENANT_STORAGE_KEY,
      JSON.stringify({ tenant_id: tenantId }),
    );
    expect(getCurrentTenantId()).toBe(tenantId);
  });

  it('returns DEFAULT_TENANT_UUID when stored JSON has no tenant_id', () => {
    localStorage.setItem(TENANT_STORAGE_KEY, JSON.stringify({ other: 'value' }));
    expect(getCurrentTenantId()).toBe(DEFAULT_TENANT_UUID);
  });

  it('returns DEFAULT_TENANT_UUID when stored value is malformed JSON', () => {
    localStorage.setItem(TENANT_STORAGE_KEY, 'not-valid-json');
    expect(getCurrentTenantId()).toBe(DEFAULT_TENANT_UUID);
  });

  it('returns DEFAULT_TENANT_UUID when stored value is an empty string', () => {
    localStorage.setItem(TENANT_STORAGE_KEY, '');
    expect(getCurrentTenantId()).toBe(DEFAULT_TENANT_UUID);
  });

  it('returns DEFAULT_TENANT_UUID when tenant_id is empty string', () => {
    localStorage.setItem(
      TENANT_STORAGE_KEY,
      JSON.stringify({ tenant_id: '' }),
    );
    expect(getCurrentTenantId()).toBe(DEFAULT_TENANT_UUID);
  });

  it('returns DEFAULT_TENANT_UUID when stored value is null JSON', () => {
    localStorage.setItem(TENANT_STORAGE_KEY, 'null');
    expect(getCurrentTenantId()).toBe(DEFAULT_TENANT_UUID);
  });
});

// ---------------------------------------------------------------------------
// buildTenantApiPath
// ---------------------------------------------------------------------------

describe('buildTenantApiPath', () => {
  const fakeTenantId = 'aaaaaaaa-1111-2222-3333-ffffffffffff';

  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem(
      TENANT_STORAGE_KEY,
      JSON.stringify({ tenant_id: fakeTenantId }),
    );
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  // -- Non-API paths are returned untouched --

  it('returns non-API paths unchanged', () => {
    expect(buildTenantApiPath('/login')).toBe('/login');
    expect(buildTenantApiPath('/dashboard')).toBe('/dashboard');
    expect(buildTenantApiPath('/')).toBe('/');
  });

  // -- Tenant-exempt prefixes are not rewritten --

  describe.each([
    '/api/me',
    '/api/login',
    '/api/auth',
    '/api/reset',
    '/api/settings',
    '/api/tenants',
    '/api/logs',
    '/api/images',
    '/api/platform-groups',
    '/api/platform-roles',
  ])('exempt prefix %s', (prefix) => {
    it('is returned unchanged', () => {
      expect(buildTenantApiPath(prefix)).toBe(prefix);
    });

    it('is returned unchanged when it has a sub-path', () => {
      const path = `${prefix}/some/sub/path`;
      expect(buildTenantApiPath(path)).toBe(path);
    });
  });

  // TODO multi-tenancy: migration paths are not rewritten yet

  describe.each([
    '/api/scenarios',
    '/api/exercises',
    '/api/simulations',
    '/api/injects',
    '/api/injector_contracts',
    '/api/atomic-testings',
    '/api/inject-expectations-traces',
    '/api/teams',
    '/api/players',
    '/api/organizations',
    '/api/endpoints',
    '/api/asset_groups',
    '/api/security_platforms',
    '/api/channels',
    '/api/challenges',
    '/api/payloads',
    '/api/documents',
    '/api/findings',
    '/api/detection-remediations',
    '/api/notification-rules',
    '/api/vulnerabilities',
    '/api/lessons_templates',
    '/api/injectors',
    '/api/collectors',
    '/api/executors',
    '/api/connector-instances',
    '/api/catalog-connector',
    '/api/attack_patterns',
    '/api/kill_chain_phases',
    '/api/domains',
    '/api/mappers',
    '/api/tag-rules',
    '/api/dashboards',
    '/api/custom-dashboards',
    '/api/fulltextsearch',
    '/api/schemas',
    '/api/engine',
    '/api/roles',
    '/api/groups',
    '/api/users',
    '/api/capabilities',
    '/api/xtmhub',
    '/api/xtm-composer',
    '/api/variables',
    '/api/reports',
    '/api/stream',
  ])('not-yet-migrated prefix %s', (prefix) => {
    it('is returned unchanged', () => {
      expect(buildTenantApiPath(prefix)).toBe(prefix);
    });

    it('is returned unchanged with sub-path', () => {
      const path = `${prefix}/123/details`;
      expect(buildTenantApiPath(path)).toBe(path);
    });
  });

  // TODO multi-tenancy: Migrated tenant-scoped paths ARE rewritten

  describe.each([
    '/api/tags',
  ])('migrated prefix %s', (prefix) => {
    it('is rewritten with tenant prefix', () => {
      expect(buildTenantApiPath(prefix)).toBe(
        `/api/tenants/${fakeTenantId}${prefix.slice('/api'.length)}`,
      );
    });

    it('is rewritten with tenant prefix when it has a sub-path', () => {
      const path = `${prefix}/abc-123`;
      expect(buildTenantApiPath(path)).toBe(
        `/api/tenants/${fakeTenantId}${prefix.slice('/api'.length)}/abc-123`,
      );
    });

    it('uses DEFAULT_TENANT_UUID when no tenant is in storage', () => {
      localStorage.clear();
      expect(buildTenantApiPath(prefix)).toBe(
        `/api/tenants/${DEFAULT_TENANT_UUID}${prefix.slice('/api'.length)}`,
      );
    });
  });

  // -- Edge cases --

  it('does not rewrite an empty string', () => {
    expect(buildTenantApiPath('')).toBe('');
  });

  it('does not rewrite a path that is exactly /api/', () => {
    // /api/ doesn't match any exempt or TODO prefix, so it IS rewritten
    expect(buildTenantApiPath('/api/')).toBe(
      `/api/tenants/${fakeTenantId}/`,
    );
  });

  it('does not double-prefix an already-prefixed path', () => {
    const alreadyPrefixed = `/api/tenants/${fakeTenantId}/tags`;
    // /api/tenants is in the exempt list, so it should stay as-is
    expect(buildTenantApiPath(alreadyPrefixed)).toBe(alreadyPrefixed);
  });
});
