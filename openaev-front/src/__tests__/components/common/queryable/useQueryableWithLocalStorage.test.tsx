import { act, renderHook } from '@testing-library/react';
import { type ReactNode } from 'react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { type SearchPaginationInput } from '../../../../utils/api-types';

// -- MOCKS --

const mockGetCurrentTenantId = vi.fn<() => string>(() => 'tenant-a');

vi.mock('../../../../utils/url-helper', async (importOriginal) => {
  // eslint-disable-next-line @typescript-eslint/consistent-type-imports
  const original = await importOriginal<typeof import('../../../../utils/url-helper')>();
  return {
    ...original,
    getCurrentTenantId: () => mockGetCurrentTenantId(),
  };
});

// -- HELPERS --

const createWrapper = () =>
  function Wrapper({ children }: { children: ReactNode }) {
    return <MemoryRouter initialEntries={['/admin/atomic_testings']}>{children}</MemoryRouter>;
  };

const KILL_CHAIN_PHASE_FILTER = {
  mode: 'and' as const,
  filters: [{
    id: 'be457cee-ca70-45d0-9ec9-8661c14e4292',
    key: 'injector_contract_kill_chain_phases',
    operator: 'eq' as const,
    mode: 'and' as const,
    values: ['a463b818-af7a-4dcb-aa34-bd7108298997'],
  }],
};

const importHook = async () => {
  const mod = await import('../../../../components/common/queryable/useQueryableWithLocalStorage');
  return mod.useQueryableWithLocalStorage;
};

/**
 * Tests that persisted list state (filters/sorts/size) saved to browser
 * localStorage by useQueryableWithLocalStorage is isolated per tenant.
 * Regression test for a cross-tenant leak: a filter referencing a
 * tenant-specific entity (e.g. a custom Kill Chain Phase id) saved on one
 * tenant must never resurface on another tenant after a tenant switch,
 * even though the switch drops the URL's `?query=` param and resets Redux
 * but leaves localStorage untouched.
 */
describe('useQueryableWithLocalStorage', () => {
  const STORAGE_KEY = 'injector-contracts-picker-atomic';

  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    mockGetCurrentTenantId.mockReturnValue('tenant-a');
  });

  describe('Tenant isolation', () => {
    it('given_filterSavedOnOneTenant_should_notLeakIntoAnotherTenant', async () => {
      // Arrange — tenant A saves a filter referencing its own custom entity
      const useQueryableWithLocalStorage = await importHook();
      const { result: resultTenantA, unmount: unmountTenantA } = renderHook(
        () => useQueryableWithLocalStorage(STORAGE_KEY, {}),
        { wrapper: createWrapper() },
      );

      act(() => {
        resultTenantA.current.setSearchPaginationInput((prev: SearchPaginationInput) => ({
          ...prev,
          filterGroup: KILL_CHAIN_PHASE_FILTER,
        }));
      });

      expect(resultTenantA.current.searchPaginationInput.filterGroup?.filters).toHaveLength(1);
      unmountTenantA();

      // Act — switch to tenant B (fresh mount, no URI query param, same storage key)
      mockGetCurrentTenantId.mockReturnValue('tenant-b');
      const { result: resultTenantB } = renderHook(
        () => useQueryableWithLocalStorage(STORAGE_KEY, {}),
        { wrapper: createWrapper() },
      );

      // Assert — tenant B must not see tenant A's persisted filter
      expect(resultTenantB.current.searchPaginationInput.filterGroup?.filters ?? []).toHaveLength(0);
    });

    it('given_tenantSwitchedBackAndForth_should_restoreEachTenantsOwnFilter', async () => {
      // Arrange — tenant A saves a filter
      const useQueryableWithLocalStorage = await importHook();
      const { result: resultA1, unmount: unmountA1 } = renderHook(
        () => useQueryableWithLocalStorage(STORAGE_KEY, {}),
        { wrapper: createWrapper() },
      );
      act(() => {
        resultA1.current.setSearchPaginationInput((prev: SearchPaginationInput) => ({
          ...prev,
          filterGroup: KILL_CHAIN_PHASE_FILTER,
        }));
      });
      unmountA1();

      // Act — switch to tenant B, mount fresh (no filter expected)
      mockGetCurrentTenantId.mockReturnValue('tenant-b');
      const { result: resultB, unmount: unmountB } = renderHook(
        () => useQueryableWithLocalStorage(STORAGE_KEY, {}),
        { wrapper: createWrapper() },
      );
      expect(resultB.current.searchPaginationInput.filterGroup?.filters ?? []).toHaveLength(0);
      unmountB();

      // Act — switch back to tenant A
      mockGetCurrentTenantId.mockReturnValue('tenant-a');
      const { result: resultA2 } = renderHook(
        () => useQueryableWithLocalStorage(STORAGE_KEY, {}),
        { wrapper: createWrapper() },
      );

      // Assert — tenant A's own filter is still there, untouched by tenant B
      expect(resultA2.current.searchPaginationInput.filterGroup?.filters).toHaveLength(1);
      expect(resultA2.current.searchPaginationInput.filterGroup?.filters?.[0].values).toEqual(
        KILL_CHAIN_PHASE_FILTER.filters[0].values,
      );
    });

    it('given_filterSaved_should_persistUnderTenantScopedStorageKey', async () => {
      // Arrange
      const useQueryableWithLocalStorage = await importHook();
      const { result } = renderHook(
        () => useQueryableWithLocalStorage(STORAGE_KEY, {}),
        { wrapper: createWrapper() },
      );

      // Act
      act(() => {
        result.current.setSearchPaginationInput((prev: SearchPaginationInput) => ({
          ...prev,
          filterGroup: KILL_CHAIN_PHASE_FILTER,
        }));
      });

      // Assert — stored under a key prefixed with the tenant id, not the bare key
      expect(localStorage.getItem(`tenant-a:${STORAGE_KEY}`)).not.toBeNull();
      expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
    });
  });
});
