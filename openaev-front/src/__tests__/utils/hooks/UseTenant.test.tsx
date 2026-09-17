import { faker } from '@faker-js/faker';
import { act, renderHook, waitFor } from '@testing-library/react';
import { type ReactNode } from 'react';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { TENANT_SWITCH_SUCCESS } from '../../../constants/ActionTypes';
import { type TenantOutput, type User } from '../../../utils/api-types';

// -- MOCKS --

const mockFetchUserTenants = vi.fn();

vi.mock('../../../actions/user/user-tenant-actions', () => ({ fetchUserTenants: () => mockFetchUserTenants() }));

const mockBuildTenantUrl = vi.fn(
  (tenantId: string, pathname: string, search: string, hash: string) =>
    `/fake-base/${tenantId}${pathname}${search}${hash}`,
);

const mockExtractTenantFromUrl = vi.fn<() => string | null>(() => null);

vi.mock('../../../utils/url-helper', async (importOriginal) => {
  // eslint-disable-next-line @typescript-eslint/consistent-type-imports
  const original = await importOriginal<typeof import('../../../utils/url-helper')>();
  return {
    ...original,
    buildTenantUrl: (...args: [string, string, string, string]) => mockBuildTenantUrl(...args),
    extractTenantFromUrl: () => mockExtractTenantFromUrl(),
  };
});

const mockDispatch = vi.fn();

vi.mock('../../../utils/hooks', () => ({ useAppDispatch: () => mockDispatch }));

// -- TEST DATA --

const TENANT_ALPHA: TenantOutput = {
  tenant_id: 'tenant-alpha-id',
  tenant_name: 'Alpha Corp',
  tenant_description: 'Primary tenant',
};

const TENANT_BETA: TenantOutput = {
  tenant_id: 'tenant-beta-id',
  tenant_name: 'Beta Industries',
  tenant_description: 'Secondary tenant',
};

const TENANT_GAMMA: TenantOutput = {
  tenant_id: 'tenant-gamma-id',
  tenant_name: 'Gamma Labs',
  tenant_description: 'Third tenant',
};

const MOCK_USER: User = {
  user_id: faker.string.uuid(),
  user_email: faker.internet.email(),
} as User;

// -- HELPERS --

/**
 * Creates a minimal Redux store mock that satisfies Provider requirements.
 */
const createMockStore = () => ({
  getState: () => ({}),
  subscribe: vi.fn(() => vi.fn()),
  dispatch: mockDispatch,
  replaceReducer: vi.fn(),
  [Symbol.observable]: vi.fn(),
});

/**
 * Wrapper that provides MemoryRouter + Redux Provider for renderHook.
 */
const createWrapper = (initialPath: string = '/admin/scenarios') =>
  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <Provider store={createMockStore() as never}>
        <MemoryRouter initialEntries={[initialPath]}>
          {children}
        </MemoryRouter>
      </Provider>
    );
  };

/**
 * Helper that resolves fetchUserTenants with the given tenant list.
 */
const mockTenantsResponse = (tenants: TenantOutput[]) => {
  mockFetchUserTenants.mockResolvedValue({ data: tenants });
};

// -- TESTS --

describe('useTenant', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockExtractTenantFromUrl.mockReturnValue(null);
    // Reset window.location.href tracking
    Object.defineProperty(window, 'location', {
      writable: true,
      value: {
        ...window.location,
        href: '',
      },
    });
  });

  // We must lazily import to ensure mocks are applied before module init
  const importUseTenant = async () => {
    const mod = await import('../../../utils/hooks/useTenant');
    return mod.default;
  };

  // -- INITIAL LOAD --

  describe('Initial load', () => {
    it('given_userAndLogged_should_fetchTenantsAndSelectFirst', async () => {
      // Arrange — URL already points to ALPHA so setTenant is called
      mockExtractTenantFromUrl.mockReturnValue(TENANT_ALPHA.tenant_id);
      mockTenantsResponse([TENANT_ALPHA, TENANT_BETA]);
      const useTenant = await importUseTenant();

      // Act
      const { result } = renderHook(() => useTenant(MOCK_USER, true), { wrapper: createWrapper() });

      // Assert
      await waitFor(() => {
        expect(result.current.userTenants).toHaveLength(2);
      });
      expect(result.current.currentUserTenant?.tenant_id).toBe(TENANT_ALPHA.tenant_id);
      expect(mockFetchUserTenants).toHaveBeenCalledTimes(1);
    });

    it('given_noUser_should_notFetchTenants', async () => {
      // Arrange
      mockTenantsResponse([TENANT_ALPHA]);
      const useTenant = await importUseTenant();

      // Act
      const { result } = renderHook(() => useTenant(undefined, true), { wrapper: createWrapper() });

      // Assert
      await waitFor(() => {
        expect(mockFetchUserTenants).not.toHaveBeenCalled();
      });
      expect(result.current.userTenants).toBeUndefined();
      expect(result.current.currentUserTenant).toBeNull();
    });

    it('given_notLogged_should_notFetchTenants', async () => {
      // Arrange
      mockTenantsResponse([TENANT_ALPHA]);
      const useTenant = await importUseTenant();

      // Act
      const { result } = renderHook(() => useTenant(MOCK_USER, false), { wrapper: createWrapper() });

      // Assert
      await waitFor(() => {
        expect(mockFetchUserTenants).not.toHaveBeenCalled();
      });
      expect(result.current.userTenants).toBeUndefined();
      expect(result.current.currentUserTenant).toBeNull();
    });

    it('given_emptyTenantsResponse_should_setEmptyStateAndNullTenant', async () => {
      // Arrange
      mockTenantsResponse([]);
      const useTenant = await importUseTenant();

      // Act
      const { result } = renderHook(() => useTenant(MOCK_USER, true), { wrapper: createWrapper() });

      // Assert
      await waitFor(() => {
        expect(mockFetchUserTenants).toHaveBeenCalledTimes(1);
        expect(result.current.userTenants).toHaveLength(0);
      });
      expect(result.current.currentUserTenant).toBeNull();
    });
  });

  // -- URL-BASED TENANT RESOLUTION (multi-tab safe) --

  describe('URL-based tenant resolution', () => {
    it('given_tenantInUrl_should_selectUrlTenant', async () => {
      // Arrange — URL has ALPHA tenant
      mockExtractTenantFromUrl.mockReturnValue(TENANT_ALPHA.tenant_id);
      mockTenantsResponse([TENANT_ALPHA, TENANT_BETA, TENANT_GAMMA]);
      const useTenant = await importUseTenant();

      // Act
      const { result } = renderHook(() => useTenant(MOCK_USER, true), { wrapper: createWrapper() });

      // Assert — should select URL tenant (ALPHA)
      await waitFor(() => {
        expect(result.current.currentUserTenant?.tenant_id).toBe(TENANT_ALPHA.tenant_id);
      });
    });

    it('given_differentTenantInUrl_should_selectUrlTenant', async () => {
      // Arrange — URL has BETA tenant
      mockExtractTenantFromUrl.mockReturnValue(TENANT_BETA.tenant_id);
      mockTenantsResponse([TENANT_ALPHA, TENANT_BETA, TENANT_GAMMA]);
      const useTenant = await importUseTenant();

      // Act
      const { result } = renderHook(() => useTenant(MOCK_USER, true), { wrapper: createWrapper() });

      // Assert — should select URL tenant (BETA)
      await waitFor(() => {
        expect(result.current.currentUserTenant?.tenant_id).toBe(TENANT_BETA.tenant_id);
      });
    });

    it('given_noTenantInUrl_should_navigateToFirstTenant', async () => {
      // Arrange — no tenant UUID in URL (e.g. post-login at "/")
      mockExtractTenantFromUrl.mockReturnValue(null);
      mockTenantsResponse([TENANT_ALPHA, TENANT_BETA]);
      const useTenant = await importUseTenant();

      // Act
      renderHook(() => useTenant(MOCK_USER, true), { wrapper: createWrapper() });

      // Assert — should trigger full page navigation to first tenant
      await waitFor(() => {
        expect(mockBuildTenantUrl).toHaveBeenCalledWith(
          TENANT_ALPHA.tenant_id,
          expect.any(String),
        );
      });
      expect(window.location.href).toContain(TENANT_ALPHA.tenant_id);
    });

    it('given_urlTenantNotInList_should_navigateToFirstTenant', async () => {
      // Arrange — URL has a tenant UUID that is not in the user's tenant list
      mockExtractTenantFromUrl.mockReturnValue('unknown-tenant-id');
      mockTenantsResponse([TENANT_ALPHA, TENANT_BETA]);
      const useTenant = await importUseTenant();

      // Act
      renderHook(() => useTenant(MOCK_USER, true), { wrapper: createWrapper() });

      // Assert — should trigger full page navigation to first tenant
      await waitFor(() => {
        expect(mockBuildTenantUrl).toHaveBeenCalledWith(
          TENANT_ALPHA.tenant_id,
          expect.any(String),
        );
      });
      expect(window.location.href).toContain(TENANT_ALPHA.tenant_id);
    });
  });

  // -- DISPATCH TENANT_SWITCH_SUCCESS --

  describe('Dispatch TENANT_SWITCH_SUCCESS', () => {
    it('given_tenantLoad_should_dispatchTenantSwitchSuccess', async () => {
      // Arrange — URL already points to the tenant so setTenant is called (no full-page nav)
      mockExtractTenantFromUrl.mockReturnValue(TENANT_ALPHA.tenant_id);
      mockTenantsResponse([TENANT_ALPHA]);
      const useTenant = await importUseTenant();

      // Act
      renderHook(() => useTenant(MOCK_USER, true), { wrapper: createWrapper() });

      // Assert
      await waitFor(() => {
        expect(mockDispatch).toHaveBeenCalledWith({
          type: TENANT_SWITCH_SUCCESS,
          payload: { tenantId: TENANT_ALPHA.tenant_id },
        });
      });
    });
  });

  // -- SWITCH TENANT --

  describe('switchUserTenant', () => {
    it('given_differentTenantId_should_navigateToNewTenantUrl', async () => {
      // Arrange — URL points to ALPHA so setTenant is called during init
      mockExtractTenantFromUrl.mockReturnValue(TENANT_ALPHA.tenant_id);
      mockTenantsResponse([TENANT_ALPHA, TENANT_BETA]);
      const useTenant = await importUseTenant();

      const { result } = renderHook(() => useTenant(MOCK_USER, true), { wrapper: createWrapper('/admin/scenarios') });

      await waitFor(() => {
        expect(result.current.currentUserTenant?.tenant_id).toBe(TENANT_ALPHA.tenant_id);
      });

      // Reset mock so the switch triggers navigation
      mockExtractTenantFromUrl.mockReturnValue(TENANT_ALPHA.tenant_id);
      mockBuildTenantUrl.mockClear();

      // Act
      await act(async () => {
        await result.current.switchUserTenant(TENANT_BETA.tenant_id);
      });

      // Assert
      expect(mockBuildTenantUrl).toHaveBeenCalledWith(
        TENANT_BETA.tenant_id,
        expect.any(String),
      );
      expect(window.location.href).toContain(TENANT_BETA.tenant_id);
    });

    it('given_sameTenantId_should_notNavigate', async () => {
      // Arrange — URL points to ALPHA so setTenant is called during init
      mockExtractTenantFromUrl.mockReturnValue(TENANT_ALPHA.tenant_id);
      mockTenantsResponse([TENANT_ALPHA, TENANT_BETA]);
      const useTenant = await importUseTenant();

      const { result } = renderHook(() => useTenant(MOCK_USER, true), { wrapper: createWrapper() });

      await waitFor(() => {
        expect(result.current.currentUserTenant?.tenant_id).toBe(TENANT_ALPHA.tenant_id);
      });

      mockBuildTenantUrl.mockClear();

      // Act
      await act(async () => {
        await result.current.switchUserTenant(TENANT_ALPHA.tenant_id);
      });

      // Assert
      expect(mockBuildTenantUrl).not.toHaveBeenCalled();
    });

    it('given_unknownTenantId_should_notNavigate', async () => {
      // Arrange — URL points to ALPHA so setTenant is called during init
      mockExtractTenantFromUrl.mockReturnValue(TENANT_ALPHA.tenant_id);
      mockTenantsResponse([TENANT_ALPHA, TENANT_BETA]);
      const useTenant = await importUseTenant();

      const { result } = renderHook(() => useTenant(MOCK_USER, true), { wrapper: createWrapper() });

      await waitFor(() => {
        expect(result.current.currentUserTenant).not.toBeNull();
      });

      mockBuildTenantUrl.mockClear();

      // Act
      await act(async () => {
        await result.current.switchUserTenant('nonexistent-tenant-id');
      });

      // Assert
      expect(mockBuildTenantUrl).not.toHaveBeenCalled();
    });
  });

  // -- RELOAD --

  describe('reloadUserTenants', () => {
    it('given_reloadCalled_should_refetchTenants', async () => {
      // Arrange — URL points to ALPHA so setTenant is called during init
      mockExtractTenantFromUrl.mockReturnValue(TENANT_ALPHA.tenant_id);
      mockTenantsResponse([TENANT_ALPHA]);
      const useTenant = await importUseTenant();

      const { result } = renderHook(() => useTenant(MOCK_USER, true), { wrapper: createWrapper() });

      await waitFor(() => {
        expect(result.current.userTenants).toHaveLength(1);
      });

      // Arrange — second call returns updated list
      mockTenantsResponse([TENANT_ALPHA, TENANT_BETA, TENANT_GAMMA]);

      // Act
      await act(async () => {
        await result.current.reloadUserTenants();
      });

      // Assert
      await waitFor(() => {
        expect(result.current.userTenants).toHaveLength(3);
      });
      expect(mockFetchUserTenants).toHaveBeenCalledTimes(2);
    });

    it('given_reloadCalledWithTenantId_should_navigateToThatTenant', async () => {
      // Arrange — URL points to ALPHA so setTenant is called during init
      mockExtractTenantFromUrl.mockReturnValue(TENANT_ALPHA.tenant_id);
      mockTenantsResponse([TENANT_ALPHA, TENANT_BETA]);
      const useTenant = await importUseTenant();

      const { result } = renderHook(() => useTenant(MOCK_USER, true), { wrapper: createWrapper() });

      await waitFor(() => {
        expect(result.current.currentUserTenant?.tenant_id).toBe(TENANT_ALPHA.tenant_id);
      });

      // Arrange — reload returns a list that includes GAMMA
      mockTenantsResponse([TENANT_ALPHA, TENANT_BETA, TENANT_GAMMA]);
      mockBuildTenantUrl.mockClear();

      // Act — explicitly request a switch to GAMMA
      await act(async () => {
        await result.current.reloadUserTenants(TENANT_GAMMA.tenant_id);
      });

      // Assert — navigates to GAMMA (full page nav since URL is on ALPHA)
      expect(mockBuildTenantUrl).toHaveBeenCalledWith(
        TENANT_GAMMA.tenant_id,
        expect.any(String),
      );
      expect(window.location.href).toContain(TENANT_GAMMA.tenant_id);
    });

    it('given_reloadCalledWithoutTenantId_should_keepCurrentUrlTenant', async () => {
      // Arrange — URL points to BETA so setTenant is called during init
      mockExtractTenantFromUrl.mockReturnValue(TENANT_BETA.tenant_id);
      mockTenantsResponse([TENANT_ALPHA, TENANT_BETA]);
      const useTenant = await importUseTenant();

      const { result } = renderHook(() => useTenant(MOCK_USER, true), { wrapper: createWrapper() });

      await waitFor(() => {
        expect(result.current.currentUserTenant?.tenant_id).toBe(TENANT_BETA.tenant_id);
      });

      // Arrange — reload returns updated list, no preferred tenant ID passed
      mockTenantsResponse([TENANT_ALPHA, TENANT_BETA, TENANT_GAMMA]);
      mockBuildTenantUrl.mockClear();

      // Act — reload without specifying a tenant (e.g. after creating a tenant without auto-switch)
      await act(async () => {
        await result.current.reloadUserTenants();
      });

      // Assert — stays on URL tenant (BETA), no navigation triggered
      await waitFor(() => {
        expect(result.current.currentUserTenant?.tenant_id).toBe(TENANT_BETA.tenant_id);
      });
      expect(mockBuildTenantUrl).not.toHaveBeenCalled();
    });
  });

  // -- ERROR HANDLING --

  describe('Error handling', () => {
    it('given_fetchUserTenantsRejects_should_setEmptyTenantsAndNotStayLoading', async () => {
      // Arrange — fetchUserTenants returns a 500 error
      mockFetchUserTenants.mockRejectedValue({
        status: 500,
        message: 'Internal error',
      });
      const useTenant = await importUseTenant();

      // Act
      const { result } = renderHook(() => useTenant(MOCK_USER, true), { wrapper: createWrapper() });

      // Assert — userTenants should be [] (not undefined), so the app doesn't hang on Loader
      await waitFor(() => {
        expect(result.current.userTenants).toEqual([]);
      });
      expect(result.current.currentUserTenant).toBeNull();
    });

    it('given_fetchUserTenantsNetworkError_should_setEmptyTenantsAndNotStayLoading', async () => {
      // Arrange — fetchUserTenants throws a network error
      mockFetchUserTenants.mockRejectedValue(new Error('Network Error'));
      const useTenant = await importUseTenant();

      // Act
      const { result } = renderHook(() => useTenant(MOCK_USER, true), { wrapper: createWrapper() });

      // Assert
      await waitFor(() => {
        expect(result.current.userTenants).toEqual([]);
      });
      expect(result.current.currentUserTenant).toBeNull();
    });
  });

  // -- RETURN VALUE SHAPE --

  describe('Return value', () => {
    it('given_hookRendered_should_returnExpectedShape', async () => {
      // Arrange
      mockTenantsResponse([TENANT_ALPHA]);
      const useTenant = await importUseTenant();

      // Act
      const { result } = renderHook(() => useTenant(MOCK_USER, true), { wrapper: createWrapper() });

      // Assert
      await waitFor(() => {
        expect(result.current.userTenants).toBeDefined();
        expect(result.current.currentUserTenant).toBeDefined();
        expect(result.current.switchUserTenant).toBeTypeOf('function');
        expect(result.current.reloadUserTenants).toBeTypeOf('function');
      });
    });
  });
});
