import { faker } from '@faker-js/faker';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

// -- MOCKS --

let mockAppBasePath = '';

vi.mock('../../utils/Environment', () => ({
  get APP_BASE_PATH() {
    return mockAppBasePath;
  },
}));

vi.mock('../../actions/platform/tenants/tenant-action', () => ({
  TENANT_URI: '/api/tenants',
}));

// -- HELPERS --

const VALID_UUID = faker.string.uuid();
const ANOTHER_UUID = faker.string.uuid();

const setPathname = (pathname: string) => {
  Object.defineProperty(window, 'location', {
    writable: true,
    value: { ...window.location, pathname },
  });
};

// -- TESTS --

describe('tenant-url-helper', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    mockAppBasePath = '';
    setPathname('/');
  });

  afterEach(() => {
    localStorage.clear();
  });

  // Lazy import so mocks are applied
  const importHelper = async () => import('../../utils/tenant-url-helper');

  // -- CONSTANTS --

  describe('Constants', () => {
    it('given_import_should_exportTenantStorageKey', async () => {
      // Act
      const { TENANT_STORAGE_KEY } = await importHelper();

      // Assert
      expect(TENANT_STORAGE_KEY).toBe('current-tenant-storage');
    });

    it('given_import_should_exportDefaultTenantUuid', async () => {
      // Act
      const { DEFAULT_TENANT_UUID } = await importHelper();

      // Assert
      expect(DEFAULT_TENANT_UUID).toBe('2cffad3a-0001-4078-b0e2-ef74274022c3');
    });
  });

  // -- extractTenantFromUrl --

  describe('extractTenantFromUrl', () => {
    it('given_urlWithUuidFirstSegment_should_returnUuid', async () => {
      // Arrange
      setPathname(`/${VALID_UUID}/admin/scenarios`);
      const { extractTenantFromUrl } = await importHelper();

      // Act
      const result = extractTenantFromUrl();

      // Assert
      expect(result).toBe(VALID_UUID);
    });

    it('given_urlWithUuidOnly_should_returnUuid', async () => {
      // Arrange
      setPathname(`/${VALID_UUID}`);
      const { extractTenantFromUrl } = await importHelper();

      // Act
      const result = extractTenantFromUrl();

      // Assert
      expect(result).toBe(VALID_UUID);
    });

    it('given_urlWithUppercaseUuid_should_returnUuid', async () => {
      // Arrange
      const uppercaseUuid = VALID_UUID.toUpperCase();
      setPathname(`/${uppercaseUuid}/admin`);
      const { extractTenantFromUrl } = await importHelper();

      // Act
      const result = extractTenantFromUrl();

      // Assert
      expect(result).toBe(uppercaseUuid);
    });

    it('given_urlWithNonUuidFirstSegment_should_returnNull', async () => {
      // Arrange
      setPathname('/login');
      const { extractTenantFromUrl } = await importHelper();

      // Act
      const result = extractTenantFromUrl();

      // Assert
      expect(result).toBeNull();
    });

    it('given_publicRouteComcheck_should_returnNull', async () => {
      // Arrange
      setPathname('/comcheck/some-id');
      const { extractTenantFromUrl } = await importHelper();

      // Act
      const result = extractTenantFromUrl();

      // Assert
      expect(result).toBeNull();
    });

    it('given_resetRoute_should_returnNull', async () => {
      // Arrange
      setPathname('/reset');
      const { extractTenantFromUrl } = await importHelper();

      // Act
      const result = extractTenantFromUrl();

      // Assert
      expect(result).toBeNull();
    });

    it('given_rootPath_should_returnNull', async () => {
      // Arrange
      setPathname('/');
      const { extractTenantFromUrl } = await importHelper();

      // Act
      const result = extractTenantFromUrl();

      // Assert
      expect(result).toBeNull();
    });

    it('given_emptyPath_should_returnNull', async () => {
      // Arrange
      setPathname('');
      const { extractTenantFromUrl } = await importHelper();

      // Act
      const result = extractTenantFromUrl();

      // Assert
      expect(result).toBeNull();
    });

    it('given_urlWithBasePathAndUuid_should_stripBaseAndReturnUuid', async () => {
      // Arrange
      mockAppBasePath = '/app';
      setPathname(`/app/${VALID_UUID}/admin`);
      const { extractTenantFromUrl } = await importHelper();

      // Act
      const result = extractTenantFromUrl();

      // Assert
      expect(result).toBe(VALID_UUID);
    });

    it('given_urlWithBasePathAndNoUuid_should_returnNull', async () => {
      // Arrange
      mockAppBasePath = '/app';
      setPathname('/app/login');
      const { extractTenantFromUrl } = await importHelper();

      // Act
      const result = extractTenantFromUrl();

      // Assert
      expect(result).toBeNull();
    });

    it('given_partialUuidInPath_should_returnNull', async () => {
      // Arrange
      setPathname('/2cffad3a-0001-4078/admin');
      const { extractTenantFromUrl } = await importHelper();

      // Act
      const result = extractTenantFromUrl();

      // Assert
      expect(result).toBeNull();
    });

    it('given_nonHexUuid_should_returnNull', async () => {
      // Arrange — 'g' is not a valid hex character
      setPathname('/gggggggg-0001-4078-b0e2-ef74274022c3/admin');
      const { extractTenantFromUrl } = await importHelper();

      // Act
      const result = extractTenantFromUrl();

      // Assert
      expect(result).toBeNull();
    });
  });

  // -- computeTenantBasename --

  describe('computeTenantBasename', () => {
    it('given_urlWithTenantUuid_should_returnBaseWithTenant', async () => {
      // Arrange
      setPathname(`/${VALID_UUID}/admin/scenarios`);
      const { computeTenantBasename } = await importHelper();

      // Act
      const result = computeTenantBasename();

      // Assert
      expect(result).toBe(`/${VALID_UUID}`);
    });

    it('given_urlWithBasPathAndTenantUuid_should_returnFullBaseWithTenant', async () => {
      // Arrange
      mockAppBasePath = '/app';
      setPathname(`/app/${VALID_UUID}/dashboard`);
      const { computeTenantBasename } = await importHelper();

      // Act
      const result = computeTenantBasename();

      // Assert
      expect(result).toBe(`/app/${VALID_UUID}`);
    });

    it('given_urlWithoutTenantUuid_should_returnBaseOnly', async () => {
      // Arrange
      setPathname('/login');
      const { computeTenantBasename } = await importHelper();

      // Act
      const result = computeTenantBasename();

      // Assert
      expect(result).toBe('');
    });

    it('given_urlWithBasePathAndNoUuid_should_returnBasePathOnly', async () => {
      // Arrange
      mockAppBasePath = '/app';
      setPathname('/app/login');
      const { computeTenantBasename } = await importHelper();

      // Act
      const result = computeTenantBasename();

      // Assert
      expect(result).toBe('/app');
    });

    it('given_rootUrl_should_returnEmptyString', async () => {
      // Arrange
      setPathname('/');
      const { computeTenantBasename } = await importHelper();

      // Act
      const result = computeTenantBasename();

      // Assert
      expect(result).toBe('');
    });
  });

  // -- buildTenantUrl --

  describe('buildTenantUrl', () => {
    it('given_tenantIdAndPathname_should_buildCorrectUrl', async () => {
      // Arrange
      const { buildTenantUrl } = await importHelper();

      // Act
      const result = buildTenantUrl(VALID_UUID, '/admin/scenarios');

      // Assert
      expect(result).toBe(`/${VALID_UUID}/admin/scenarios`);
    });

    it('given_pathnameWithoutLeadingSlash_should_normalizeAndBuildUrl', async () => {
      // Arrange
      const { buildTenantUrl } = await importHelper();

      // Act
      const result = buildTenantUrl(VALID_UUID, 'admin/scenarios');

      // Assert
      expect(result).toBe(`/${VALID_UUID}/admin/scenarios`);
    });

    it('given_searchAndHash_should_appendThem', async () => {
      // Arrange
      const { buildTenantUrl } = await importHelper();

      // Act
      const result = buildTenantUrl(VALID_UUID, '/admin', '?page=1', '#section');

      // Assert
      expect(result).toBe(`/${VALID_UUID}/admin?page=1#section`);
    });

    it('given_emptySearchAndHash_should_notAppendExtra', async () => {
      // Arrange
      const { buildTenantUrl } = await importHelper();

      // Act
      const result = buildTenantUrl(VALID_UUID, '/admin');

      // Assert
      expect(result).toBe(`/${VALID_UUID}/admin`);
    });

    it('given_basePath_should_prependBasePath', async () => {
      // Arrange
      mockAppBasePath = '/app';
      const { buildTenantUrl } = await importHelper();

      // Act
      const result = buildTenantUrl(VALID_UUID, '/admin/scenarios');

      // Assert
      expect(result).toBe(`/app/${VALID_UUID}/admin/scenarios`);
    });

    it('given_basePathWithSearchAndHash_should_buildFullUrl', async () => {
      // Arrange
      mockAppBasePath = '/app';
      const { buildTenantUrl } = await importHelper();

      // Act
      const result = buildTenantUrl(VALID_UUID, '/dashboard', '?tab=overview', '#top');

      // Assert
      expect(result).toBe(`/app/${VALID_UUID}/dashboard?tab=overview#top`);
    });

    it('given_rootPathname_should_buildUrlWithSlash', async () => {
      // Arrange
      const { buildTenantUrl } = await importHelper();

      // Act
      const result = buildTenantUrl(VALID_UUID, '/');

      // Assert
      expect(result).toBe(`/${VALID_UUID}/`);
    });
  });

  // -- getCurrentTenantId --

  describe('getCurrentTenantId', () => {
    it('given_validTenantInStorage_should_returnStoredTenantId', async () => {
      // Arrange
      const tenant = { tenant_id: VALID_UUID, tenant_name: 'Test' };
      localStorage.setItem('current-tenant-storage', JSON.stringify(tenant));
      const { getCurrentTenantId } = await importHelper();

      // Act
      const result = getCurrentTenantId();

      // Assert
      expect(result).toBe(VALID_UUID);
    });

    it('given_emptyStorage_should_returnDefaultTenantUuid', async () => {
      // Arrange
      const { getCurrentTenantId, DEFAULT_TENANT_UUID } = await importHelper();

      // Act
      const result = getCurrentTenantId();

      // Assert
      expect(result).toBe(DEFAULT_TENANT_UUID);
    });

    it('given_malformedJsonInStorage_should_returnDefaultTenantUuid', async () => {
      // Arrange
      localStorage.setItem('current-tenant-storage', '{not valid json');
      const { getCurrentTenantId, DEFAULT_TENANT_UUID } = await importHelper();

      // Act
      const result = getCurrentTenantId();

      // Assert
      expect(result).toBe(DEFAULT_TENANT_UUID);
    });

    it('given_storedObjectWithoutTenantId_should_returnDefaultTenantUuid', async () => {
      // Arrange
      localStorage.setItem('current-tenant-storage', JSON.stringify({ tenant_name: 'No ID' }));
      const { getCurrentTenantId, DEFAULT_TENANT_UUID } = await importHelper();

      // Act
      const result = getCurrentTenantId();

      // Assert
      expect(result).toBe(DEFAULT_TENANT_UUID);
    });

    it('given_storedNullValue_should_returnDefaultTenantUuid', async () => {
      // Arrange
      localStorage.setItem('current-tenant-storage', 'null');
      const { getCurrentTenantId, DEFAULT_TENANT_UUID } = await importHelper();

      // Act
      const result = getCurrentTenantId();

      // Assert
      expect(result).toBe(DEFAULT_TENANT_UUID);
    });

    it('given_storedEmptyString_should_returnDefaultTenantUuid', async () => {
      // Arrange
      localStorage.setItem('current-tenant-storage', '');
      const { getCurrentTenantId, DEFAULT_TENANT_UUID } = await importHelper();

      // Act
      const result = getCurrentTenantId();

      // Assert
      expect(result).toBe(DEFAULT_TENANT_UUID);
    });

    it('given_differentTenantStored_should_returnThatTenantId', async () => {
      // Arrange
      const tenant = { tenant_id: ANOTHER_UUID, tenant_name: 'Another' };
      localStorage.setItem('current-tenant-storage', JSON.stringify(tenant));
      const { getCurrentTenantId } = await importHelper();

      // Act
      const result = getCurrentTenantId();

      // Assert
      expect(result).toBe(ANOTHER_UUID);
    });
  });

  // -- buildTenantApiUri --

  describe('buildTenantApiUri', () => {
    it('given_pathAndStoredTenant_should_buildScopedApiUri', async () => {
      // Arrange
      const tenant = { tenant_id: VALID_UUID, tenant_name: 'Test' };
      localStorage.setItem('current-tenant-storage', JSON.stringify(tenant));
      const { buildTenantApiUri } = await importHelper();

      // Act
      const result = buildTenantApiUri('/tags');

      // Assert
      expect(result).toBe(`/api/tenants/${VALID_UUID}/tags`);
    });

    it('given_pathAndNoStoredTenant_should_useDefaultTenantUuid', async () => {
      // Arrange
      const { buildTenantApiUri, DEFAULT_TENANT_UUID } = await importHelper();

      // Act
      const result = buildTenantApiUri('/tags');

      // Assert
      expect(result).toBe(`/api/tenants/${DEFAULT_TENANT_UUID}/tags`);
    });

    it('given_nestedPath_should_buildCorrectUri', async () => {
      // Arrange
      const tenant = { tenant_id: VALID_UUID, tenant_name: 'Test' };
      localStorage.setItem('current-tenant-storage', JSON.stringify(tenant));
      const { buildTenantApiUri } = await importHelper();

      // Act
      const result = buildTenantApiUri('/tags/search');

      // Assert
      expect(result).toBe(`/api/tenants/${VALID_UUID}/tags/search`);
    });

    it('given_pathWithIdParameter_should_buildCorrectUri', async () => {
      // Arrange
      const tenant = { tenant_id: VALID_UUID, tenant_name: 'Test' };
      localStorage.setItem('current-tenant-storage', JSON.stringify(tenant));
      const tagId = faker.string.uuid();
      const { buildTenantApiUri } = await importHelper();

      // Act
      const result = buildTenantApiUri(`/tags/${tagId}`);

      // Assert
      expect(result).toBe(`/api/tenants/${VALID_UUID}/tags/${tagId}`);
    });

    it('given_emptyPath_should_buildUriWithTrailingTenantOnly', async () => {
      // Arrange
      const tenant = { tenant_id: VALID_UUID, tenant_name: 'Test' };
      localStorage.setItem('current-tenant-storage', JSON.stringify(tenant));
      const { buildTenantApiUri } = await importHelper();

      // Act
      const result = buildTenantApiUri('');

      // Assert
      expect(result).toBe(`/api/tenants/${VALID_UUID}`);
    });
  });
});

