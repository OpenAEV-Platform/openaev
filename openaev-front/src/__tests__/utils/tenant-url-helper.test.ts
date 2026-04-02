import { faker } from '@faker-js/faker';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  buildTenantApiUri,
  buildTenantUrl,
  computeTenantBasename,
  DEFAULT_TENANT_UUID,
  extractTenantFromUrl,
  getCurrentTenantId,
  TENANT_STORAGE_KEY,
  TENANT_URI,
} from '../../utils/tenant-url-helper';

// -- HELPERS --

const VALID_UUID = faker.string.uuid();

const setPathname = (pathname: string) => {
  Object.defineProperty(window, 'location', {
    writable: true,
    value: { ...window.location, pathname },
  });
};

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

describe('Constants', () => {
  it('given_import_should_exportTenantStorageKey', () => {
    expect(TENANT_STORAGE_KEY).toBe('current-tenant-storage');
  });

  it('given_import_should_exportDefaultTenantUuid', () => {
    expect(DEFAULT_TENANT_UUID).toBe('2cffad3a-0001-4078-b0e2-ef74274022c3');
  });

  it('given_import_should_exportTenantUri', () => {
    expect(TENANT_URI).toBe('/api/tenants');
  });
});

// ---------------------------------------------------------------------------
// extractTenantFromUrl
// ---------------------------------------------------------------------------

describe('extractTenantFromUrl', () => {
  beforeEach(() => {
    setPathname('/');
  });

  it('given_urlWithUuidFirstSegment_should_returnUuid', () => {
    // Arrange
    setPathname(`/${VALID_UUID}/admin/scenarios`);

    // Act
    const result = extractTenantFromUrl();

    // Assert
    expect(result).toBe(VALID_UUID);
  });

  it('given_urlWithUuidOnly_should_returnUuid', () => {
    // Arrange
    setPathname(`/${VALID_UUID}`);

    // Act & Assert
    expect(extractTenantFromUrl()).toBe(VALID_UUID);
  });

  it('given_urlWithUppercaseUuid_should_returnUuid', () => {
    // Arrange
    const uppercaseUuid = VALID_UUID.toUpperCase();
    setPathname(`/${uppercaseUuid}/admin`);

    // Act & Assert
    expect(extractTenantFromUrl()).toBe(uppercaseUuid);
  });

  it('given_urlWithNonUuidFirstSegment_should_returnNull', () => {
    // Arrange
    setPathname('/login');

    // Act & Assert
    expect(extractTenantFromUrl()).toBeNull();
  });

  it('given_publicRouteComcheck_should_returnNull', () => {
    setPathname('/comcheck/some-id');
    expect(extractTenantFromUrl()).toBeNull();
  });

  it('given_resetRoute_should_returnNull', () => {
    setPathname('/reset');
    expect(extractTenantFromUrl()).toBeNull();
  });

  it('given_rootPath_should_returnNull', () => {
    setPathname('/');
    expect(extractTenantFromUrl()).toBeNull();
  });

  it('given_emptyPath_should_returnNull', () => {
    setPathname('');
    expect(extractTenantFromUrl()).toBeNull();
  });

  it('given_partialUuid_should_returnNull', () => {
    setPathname('/2cffad3a-0001-4078/admin');
    expect(extractTenantFromUrl()).toBeNull();
  });

  it('given_nonHexUuid_should_returnNull', () => {
    // 'g' is not a valid hex character
    setPathname('/gggggggg-0001-4078-b0e2-ef74274022c3/admin');
    expect(extractTenantFromUrl()).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// computeTenantBasename
// ---------------------------------------------------------------------------

describe('computeTenantBasename', () => {
  it('given_urlWithTenantUuid_should_returnBaseWithTenant', () => {
    // Arrange
    setPathname(`/${VALID_UUID}/admin/scenarios`);

    // Act & Assert
    expect(computeTenantBasename()).toBe(`/${VALID_UUID}`);
  });

  it('given_urlWithoutTenantUuid_should_returnEmptyString', () => {
    // Arrange
    setPathname('/login');

    // Act & Assert
    expect(computeTenantBasename()).toBe('');
  });

  it('given_rootUrl_should_returnEmptyString', () => {
    // Arrange
    setPathname('/');

    // Act & Assert
    expect(computeTenantBasename()).toBe('');
  });
});

// ---------------------------------------------------------------------------
// buildTenantUrl
// ---------------------------------------------------------------------------

describe('buildTenantUrl', () => {
  it('given_tenantIdAndPathname_should_buildCorrectUrl', () => {
    expect(buildTenantUrl(VALID_UUID, '/admin/scenarios')).toBe(
      `/${VALID_UUID}/admin/scenarios`,
    );
  });

  it('given_pathnameWithoutLeadingSlash_should_normalizeAndBuildUrl', () => {
    expect(buildTenantUrl(VALID_UUID, 'admin/scenarios')).toBe(
      `/${VALID_UUID}/admin/scenarios`,
    );
  });

  it('given_searchAndHash_should_appendThem', () => {
    expect(buildTenantUrl(VALID_UUID, '/admin', '?page=1', '#section')).toBe(
      `/${VALID_UUID}/admin?page=1#section`,
    );
  });

  it('given_emptySearchAndHash_should_notAppendExtra', () => {
    expect(buildTenantUrl(VALID_UUID, '/admin')).toBe(
      `/${VALID_UUID}/admin`,
    );
  });

  it('given_rootPathname_should_buildUrlWithSlash', () => {
    expect(buildTenantUrl(VALID_UUID, '/')).toBe(`/${VALID_UUID}/`);
  });
});

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

  it('given_nothing_should_returnDefaultTenantUuid', () => {
    expect(getCurrentTenantId()).toBe(DEFAULT_TENANT_UUID);
  });

  it('given_validTenantInStorage_should_returnStoredTenantId', () => {
    // Arrange
    const tenantId = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee';
    localStorage.setItem(
      TENANT_STORAGE_KEY,
      JSON.stringify({ tenant_id: tenantId }),
    );

    // Act & Assert
    expect(getCurrentTenantId()).toBe(tenantId);
  });

  it('given_storedJsonWithNoTenantId_should_returnDefaultTenantUuid', () => {
    localStorage.setItem(TENANT_STORAGE_KEY, JSON.stringify({ other: 'value' }));
    expect(getCurrentTenantId()).toBe(DEFAULT_TENANT_UUID);
  });

  it('given_malformedJson_should_returnDefaultTenantUuid', () => {
    localStorage.setItem(TENANT_STORAGE_KEY, 'not-valid-json');
    expect(getCurrentTenantId()).toBe(DEFAULT_TENANT_UUID);
  });

  it('given_emptyString_should_returnDefaultTenantUuid', () => {
    localStorage.setItem(TENANT_STORAGE_KEY, '');
    expect(getCurrentTenantId()).toBe(DEFAULT_TENANT_UUID);
  });

  it('given_emptyTenantId_should_returnDefaultTenantUuid', () => {
    localStorage.setItem(
      TENANT_STORAGE_KEY,
      JSON.stringify({ tenant_id: '' }),
    );
    expect(getCurrentTenantId()).toBe(DEFAULT_TENANT_UUID);
  });

  it('given_nullJson_should_returnDefaultTenantUuid', () => {
    localStorage.setItem(TENANT_STORAGE_KEY, 'null');
    expect(getCurrentTenantId()).toBe(DEFAULT_TENANT_UUID);
  });
});

// ---------------------------------------------------------------------------
// buildTenantApiUri
// ---------------------------------------------------------------------------

describe('buildTenantApiUri', () => {
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

  it('given_pathAndStoredTenant_should_buildScopedApiUri', () => {
    expect(buildTenantApiUri('/tags')).toBe(
      `/api/tenants/${fakeTenantId}/tags`,
    );
  });

  it('given_nestedPath_should_buildCorrectUri', () => {
    expect(buildTenantApiUri('/tags/search')).toBe(
      `/api/tenants/${fakeTenantId}/tags/search`,
    );
  });

  it('given_pathWithIdParameter_should_buildCorrectUri', () => {
    // Arrange
    const tagId = faker.string.uuid();

    // Act & Assert
    expect(buildTenantApiUri(`/tags/${tagId}`)).toBe(
      `/api/tenants/${fakeTenantId}/tags/${tagId}`,
    );
  });

  it('given_emptyPath_should_buildUriWithTenantOnly', () => {
    expect(buildTenantApiUri('')).toBe(
      `/api/tenants/${fakeTenantId}`,
    );
  });

  it('given_noStoredTenant_should_useDefaultTenantUuid', () => {
    // Arrange
    localStorage.clear();

    // Act & Assert
    expect(buildTenantApiUri('/tags')).toBe(
      `/api/tenants/${DEFAULT_TENANT_UUID}/tags`,
    );
  });
});
