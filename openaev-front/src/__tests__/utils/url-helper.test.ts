import { faker } from '@faker-js/faker';
import { beforeEach, describe, expect, it, vi } from 'vitest';

// -- MOCKS --

let mockAppBasePath = '';

vi.mock('../../utils/Environment', () => ({
  get APP_BASE_PATH() {
    return mockAppBasePath;
  },
}));

vi.mock('../../actions/platform/tenants/tenant-action', () => ({ TENANT_URI: '/api/tenants' }));

// -- HELPERS --

const VALID_UUID = faker.string.uuid();
const ANOTHER_UUID = faker.string.uuid();

const setPathname = (pathname: string) => {
  Object.defineProperty(window, 'location', {
    writable: true,
    value: {
      ...window.location,
      pathname,
    },
  });
};

// -- TESTS --

describe('url-helper', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAppBasePath = '';
    setPathname('/');
  });

  // Lazy import so mocks are applied
  const importHelper = async () => import('../../utils/url-helper');

  // -- CONSTANTS --

  describe('Constants', () => {
    it('given import should export DefaultTenantUuid', async () => {
      // Act
      const { DEFAULT_TENANT_UUID } = await importHelper();

      // Assert
      expect(DEFAULT_TENANT_UUID).toBe('2cffad3a-0001-4078-b0e2-ef74274022c3');
    });

    it('given import should export TenantUri', async () => {
      // Act
      const { TENANT_URI } = await importHelper();

      // Assert
      expect(TENANT_URI).toBe('/api/tenants');
    });
  });

  // -- extractTenantFromUrl --

  describe('extractTenantFromUrl', () => {
    it('given url with UUID first segment should return UUID', async () => {
      // Arrange
      setPathname(`/${VALID_UUID}/admin/scenarios`);
      const { extractTenantFromUrl } = await importHelper();

      // Act
      const result = extractTenantFromUrl();

      // Assert
      expect(result).toBe(VALID_UUID);
    });

    it('given url with UUID only should return UUID', async () => {
      // Arrange
      setPathname(`/${VALID_UUID}`);
      const { extractTenantFromUrl } = await importHelper();

      // Act
      const result = extractTenantFromUrl();

      // Assert
      expect(result).toBe(VALID_UUID);
    });

    it('given url with uppercase UUID should return UUID', async () => {
      // Arrange
      const uppercaseUuid = VALID_UUID.toUpperCase();
      setPathname(`/${uppercaseUuid}/admin`);
      const { extractTenantFromUrl } = await importHelper();

      // Act
      const result = extractTenantFromUrl();

      // Assert
      expect(result).toBe(uppercaseUuid);
    });

    it('given url with non-UUID first segment should return null', async () => {
      // Arrange
      setPathname('/login');
      const { extractTenantFromUrl } = await importHelper();

      // Act
      const result = extractTenantFromUrl();

      // Assert
      expect(result).toBeNull();
    });

    it('given public route comcheck should return null', async () => {
      // Arrange
      setPathname('/comcheck/some-id');
      const { extractTenantFromUrl } = await importHelper();

      // Act
      const result = extractTenantFromUrl();

      // Assert
      expect(result).toBeNull();
    });

    it('given reset route should return null', async () => {
      // Arrange
      setPathname('/reset');
      const { extractTenantFromUrl } = await importHelper();

      // Act
      const result = extractTenantFromUrl();

      // Assert
      expect(result).toBeNull();
    });

    it('given root path should return null', async () => {
      // Arrange
      setPathname('/');
      const { extractTenantFromUrl } = await importHelper();

      // Act
      const result = extractTenantFromUrl();

      // Assert
      expect(result).toBeNull();
    });

    it('given empty path should return null', async () => {
      // Arrange
      setPathname('');
      const { extractTenantFromUrl } = await importHelper();

      // Act
      const result = extractTenantFromUrl();

      // Assert
      expect(result).toBeNull();
    });

    it('given url with base path and UUID should strip base and return UUID', async () => {
      // Arrange
      mockAppBasePath = '/app';
      setPathname(`/app/${VALID_UUID}/admin`);
      const { extractTenantFromUrl } = await importHelper();

      // Act
      const result = extractTenantFromUrl();

      // Assert
      expect(result).toBe(VALID_UUID);
    });

    it('given url with base path and no UUID should return null', async () => {
      // Arrange
      mockAppBasePath = '/app';
      setPathname('/app/login');
      const { extractTenantFromUrl } = await importHelper();

      // Act
      const result = extractTenantFromUrl();

      // Assert
      expect(result).toBeNull();
    });

    it('given partial UUID in path should return null', async () => {
      // Arrange
      setPathname('/2cffad3a-0001-4078/admin');
      const { extractTenantFromUrl } = await importHelper();

      // Act
      const result = extractTenantFromUrl();

      // Assert
      expect(result).toBeNull();
    });

    it('given non-hex UUID should return null', async () => {
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
    it('given url with tenant UUID should return base with tenant', async () => {
      // Arrange
      setPathname(`/${VALID_UUID}/admin/scenarios`);
      const { computeTenantBasename } = await importHelper();

      // Act
      const result = computeTenantBasename();

      // Assert
      expect(result).toBe(`/${VALID_UUID}`);
    });

    it('given url with base path and tenant UUID should return full base with tenant', async () => {
      // Arrange
      mockAppBasePath = '/app';
      setPathname(`/app/${VALID_UUID}/dashboard`);
      const { computeTenantBasename } = await importHelper();

      // Act
      const result = computeTenantBasename();

      // Assert
      expect(result).toBe(`/app/${VALID_UUID}`);
    });

    it('given url without tenant UUID should return base only', async () => {
      // Arrange
      setPathname('/login');
      const { computeTenantBasename } = await importHelper();

      // Act
      const result = computeTenantBasename();

      // Assert
      expect(result).toBe('');
    });

    it('given url with base path and no UUID should return base path only', async () => {
      // Arrange
      mockAppBasePath = '/app';
      setPathname('/app/login');
      const { computeTenantBasename } = await importHelper();

      // Act
      const result = computeTenantBasename();

      // Assert
      expect(result).toBe('/app');
    });

    it('given root url should return empty string', async () => {
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
    it('given tenant id and pathname should build correct url', async () => {
      // Arrange
      const { buildTenantUrl } = await importHelper();

      // Act
      const result = buildTenantUrl(VALID_UUID, '/admin/scenarios');

      // Assert
      expect(result).toBe(`/${VALID_UUID}/admin/scenarios`);
    });

    it('given pathname without leading slash should normalize and build url', async () => {
      // Arrange
      const { buildTenantUrl } = await importHelper();

      // Act
      const result = buildTenantUrl(VALID_UUID, 'admin/scenarios');

      // Assert
      expect(result).toBe(`/${VALID_UUID}/admin/scenarios`);
    });

    it('given search and hash should append them', async () => {
      // Arrange
      const { buildTenantUrl } = await importHelper();

      // Act
      const result = buildTenantUrl(VALID_UUID, '/admin', '?page=1', '#section');

      // Assert
      expect(result).toBe(`/${VALID_UUID}/admin?page=1#section`);
    });

    it('given empty search and hash should not append extra', async () => {
      // Arrange
      const { buildTenantUrl } = await importHelper();

      // Act
      const result = buildTenantUrl(VALID_UUID, '/admin');

      // Assert
      expect(result).toBe(`/${VALID_UUID}/admin`);
    });

    it('given base path should prepend base path', async () => {
      // Arrange
      mockAppBasePath = '/app';
      const { buildTenantUrl } = await importHelper();

      // Act
      const result = buildTenantUrl(VALID_UUID, '/admin/scenarios');

      // Assert
      expect(result).toBe(`/app/${VALID_UUID}/admin/scenarios`);
    });

    it('given base path with search and hash should build full url', async () => {
      // Arrange
      mockAppBasePath = '/app';
      const { buildTenantUrl } = await importHelper();

      // Act
      const result = buildTenantUrl(VALID_UUID, '/dashboard', '?tab=overview', '#top');

      // Assert
      expect(result).toBe(`/app/${VALID_UUID}/dashboard?tab=overview#top`);
    });

    it('given root pathname should build url with slash', async () => {
      // Arrange
      const { buildTenantUrl } = await importHelper();

      // Act
      const result = buildTenantUrl(VALID_UUID, '/');

      // Assert
      expect(result).toBe(`/${VALID_UUID}/`);
    });
  });

  // -- getCurrentTenantId (URL-only, no localStorage) --

  describe('getCurrentTenantId', () => {
    it('given tenant UUID in URL should return it', async () => {
      // Arrange
      setPathname(`/${VALID_UUID}/admin/scenarios`);
      const { getCurrentTenantId } = await importHelper();

      // Act
      const result = getCurrentTenantId();

      // Assert
      expect(result).toBe(VALID_UUID);
    });

    it('given different tenant UUID in URL should return it', async () => {
      // Arrange
      setPathname(`/${ANOTHER_UUID}/admin`);
      const { getCurrentTenantId } = await importHelper();

      // Act
      const result = getCurrentTenantId();

      // Assert
      expect(result).toBe(ANOTHER_UUID);
    });

    it('given no tenant in URL should return default tenant UUID', async () => {
      // Arrange
      setPathname('/login');
      const { getCurrentTenantId, DEFAULT_TENANT_UUID } = await importHelper();

      // Act
      const result = getCurrentTenantId();

      // Assert
      expect(result).toBe(DEFAULT_TENANT_UUID);
    });

    it('given root path should return default tenant UUID', async () => {
      // Arrange
      setPathname('/');
      const { getCurrentTenantId, DEFAULT_TENANT_UUID } = await importHelper();

      // Act
      const result = getCurrentTenantId();

      // Assert
      expect(result).toBe(DEFAULT_TENANT_UUID);
    });

    it('given base path with tenant UUID should return it', async () => {
      // Arrange
      mockAppBasePath = '/app';
      setPathname(`/app/${VALID_UUID}/admin`);
      const { getCurrentTenantId } = await importHelper();

      // Act
      const result = getCurrentTenantId();

      // Assert
      expect(result).toBe(VALID_UUID);
    });
  });

  // -- buildTenantApiPath --

  describe('buildTenantApiPath', () => {
    it('given API path and tenant in URL should build scoped API URI', async () => {
      // Arrange
      setPathname(`/${VALID_UUID}/admin/tags`);
      const { buildTenantApiPath } = await importHelper();

      // Act
      const result = buildTenantApiPath('/api/tags');

      // Assert
      expect(result).toBe(`/api/tenants/${VALID_UUID}/tags`);
    });

    it('given API path and no tenant in URL should use default tenant UUID', async () => {
      // Arrange
      setPathname('/');
      const { buildTenantApiPath, DEFAULT_TENANT_UUID } = await importHelper();

      // Act
      const result = buildTenantApiPath('/api/tags');

      // Assert
      expect(result).toBe(`/api/tenants/${DEFAULT_TENANT_UUID}/tags`);
    });

    it('given nested API path should build correct URI', async () => {
      // Arrange
      setPathname(`/${VALID_UUID}/admin`);
      const { buildTenantApiPath } = await importHelper();

      // Act
      const result = buildTenantApiPath('/api/tags/search');

      // Assert
      expect(result).toBe(`/api/tenants/${VALID_UUID}/tags/search`);
    });

    it('given API path with id parameter should build correct URI', async () => {
      // Arrange
      setPathname(`/${VALID_UUID}/admin`);
      const tagId = faker.string.uuid();
      const { buildTenantApiPath } = await importHelper();

      // Act
      const result = buildTenantApiPath(`/api/tags/${tagId}`);

      // Assert
      expect(result).toBe(`/api/tenants/${VALID_UUID}/tags/${tagId}`);
    });

    it('given non-API path should return unchanged', async () => {
      // Arrange
      const { buildTenantApiPath } = await importHelper();

      // Act
      const result = buildTenantApiPath('/some/other/path');

      // Assert
      expect(result).toBe('/some/other/path');
    });

    it('given exempt prefix should return unchanged', async () => {
      // Arrange
      setPathname(`/${VALID_UUID}/admin`);
      const { buildTenantApiPath } = await importHelper();

      // Act
      const result = buildTenantApiPath('/api/settings');

      // Assert
      expect(result).toBe('/api/settings');
    });
  });
});
