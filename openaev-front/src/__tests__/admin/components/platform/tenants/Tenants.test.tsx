import { cleanup, render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { TenantOutput } from '../../../../../utils/api-types';

const { mockIsFeatureEnabled, mockCan, mockTenants, mockDefaultTenantId } = vi.hoisted(() => ({
  mockIsFeatureEnabled: vi.fn(),
  mockCan: vi.fn(),
  mockTenants: { current: [] as TenantOutput[] },
  mockDefaultTenantId: { current: '2cffad3a-0001-4078-b0e2-ef74274022c3' },
}));

vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (value: string) => value }) }));

vi.mock('../../../../../utils/utils', () => ({ isFeatureEnabled: mockIsFeatureEnabled }));
vi.mock('../../../../../utils/hooks/useAuth', () => ({ default: () => ({ settings: { default_tenant_id: mockDefaultTenantId.current } }) }));

vi.mock('../../../../../utils/permissions/NoAccess', () => ({ default: () => <div data-testid="no-access" /> }));

vi.mock('../../../../../utils/permissions/permissionsContext', async () => {
  const React = await import('react');
  const AbilityContext = React.createContext({ can: mockCan } as { can: (action: string, subject: string) => boolean });
  return {
    AbilityContext,
    Can: ({
      I,
      a,
      this: resource,
      children,
    }: {
      I: string;
      a: string;
      this?: unknown;
      children: ReactNode;
    }) => (mockCan(I, a, resource as string) ? <>{children}</> : null),
  };
});

vi.mock('../../../../../components/Breadcrumbs', () => ({ default: ({ elements }: { elements: { label: string }[] }) => <div>{elements.map(element => element.label).join(' / ')}</div> }));

vi.mock('../../../../../components/common/list/PaginatedList', () => ({
  default: ({ secondaryAction, items }: {
    secondaryAction?: (item: TenantOutput) => ReactNode;
    items?: TenantOutput[];
  }) => (
    <div data-testid="paginated-list">
      {items?.map((item, i) => (
        // eslint-disable-next-line react/no-array-index-key
        <div key={i} data-testid="list-item">
          {secondaryAction?.(item)}
        </div>
      ))}
    </div>
  ),
}));

vi.mock('../../../../../components/common/queryable/pagination/PaginationComponentV2', () => ({ default: () => <div data-testid="pagination-component" /> }));

vi.mock('../../../../../components/common/queryable/sort/SortHeadersComponentV2', () => ({ default: () => <div data-testid="sort-headers" /> }));

vi.mock('../../../../../components/PaginatedListLoader', () => ({ default: () => <div data-testid="list-loader" /> }));

vi.mock('../../../../../components/common/queryable/useQueryableWithLocalStorage', () => ({
  useQueryableWithLocalStorage: () => ({
    queryableHelpers: { sortHelpers: {} },
    searchPaginationInput: {},
  }),
}));

vi.mock('../../../../../components/common/queryable/QueryableUtils', () => ({ buildSearchPagination: () => ({}) }));

vi.mock('../../../../../admin/components/settings/SecurityMenu', () => ({ default: () => <div data-testid="security-menu" /> }));

vi.mock('../../../../../admin/components/platform/tenants/hooks/useTenants', () => ({
  default: () => ({
    tenants: mockTenants.current,
    setTenantList: vi.fn(),
    loading: false,
    fetchTenants: vi.fn(),
    addTenant: vi.fn(),
    updateTenant: vi.fn(),
    softDeleteTenant: vi.fn(),
    reactivateTenant: vi.fn(),
  }),
}));

vi.mock('../../../../../admin/components/platform/tenants/tenant/TenantCreate', () => ({ default: () => <div data-testid="tenant-create" /> }));

vi.mock('../../../../../admin/components/platform/tenants/TenantPopover', () => ({ default: () => <div data-testid="tenant-popover" /> }));

vi.mock('../../../../../admin/components/platform/tenants/DefaultTenantDangerZone', () => ({
  default: ({ children }: { children?: ReactNode }) => (
    <div data-testid="default-tenant-danger-zone">{children}</div>
  ),
}));

vi.mock('../../../../../admin/components/platform/tenants/tenants.queryable', () => ({
  ENTITY_TENANT_PREFIX: 'tenant',
  getTenantHeaders: () => [],
  LOCAL_STORAGE_KEY_TENANT: 'tenant-queryable',
  TENANT_FILTERS: [],
  TENANT_INLINE_STYLES: {},
  TENANT_SORTS: [],
}));

import Tenants from '../../../../../admin/components/platform/tenants/Tenants';
import { DEFAULT_TENANT_UUID } from '../../../../../utils/url-helper';

// Validates that the Tenants page renders or hides UI based on permissions and feature-flag state.
describe('Tenants', () => {
  beforeEach(() => {
    mockCan.mockReturnValue(true);
    mockIsFeatureEnabled.mockReturnValue(true);
    mockTenants.current = [];
    mockDefaultTenantId.current = DEFAULT_TENANT_UUID;
  });

  afterEach(() => {
    vi.clearAllMocks();
    cleanup();
  });

  it('renders tenants page even when MULTI_TENANCY feature flag is disabled (route handles access gate)', () => {
    // Arrange
    mockIsFeatureEnabled.mockReturnValue(false);

    // Act
    render(<Tenants />);

    // Assert
    expect(screen.getByText('Platform / Tenants management')).toBeDefined();
    expect(screen.getByTestId('pagination-component')).toBeDefined();
  });

  it('hides tenant creation when user cannot manage tenants', () => {
    // Arrange
    mockCan.mockReturnValue(false);

    // Act
    render(<Tenants />);

    // Assert
    expect(screen.queryByTestId('tenant-create')).toBeNull();
    expect(screen.getByText('Platform / Tenants management')).toBeDefined();
  });

  it('renders tenants page when feature is enabled and user has access', () => {
    // Arrange
    mockIsFeatureEnabled.mockReturnValue(true);
    mockCan.mockReturnValue(true);

    // Act
    render(<Tenants />);

    // Assert
    expect(screen.getByText('Platform / Tenants management')).toBeDefined();
    expect(screen.getByTestId('pagination-component')).toBeDefined();
  });

  describe('default tenant danger zone', () => {
    it('shows DefaultTenantDangerZone for the default tenant row in the list', () => {
      // Arrange
      mockTenants.current = [{
        tenant_id: DEFAULT_TENANT_UUID,
        tenant_name: 'Default Tenant',
      }];

      // Act
      render(<Tenants />);

      // Assert
      expect(screen.getByTestId('default-tenant-danger-zone')).toBeDefined();
    });

    it('does not show DefaultTenantDangerZone for a non-default tenant row', () => {
      // Arrange
      mockTenants.current = [{
        tenant_id: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
        tenant_name: 'Regular Tenant',
      }];

      // Act
      render(<Tenants />);

      // Assert
      expect(screen.queryByTestId('default-tenant-danger-zone')).toBeNull();
    });

    it('shows DefaultTenantDangerZone only for the default tenant when both are present', () => {
      // Arrange
      mockTenants.current = [
        {
          tenant_id: DEFAULT_TENANT_UUID,
          tenant_name: 'Default Tenant',
        },
        {
          tenant_id: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
          tenant_name: 'Regular Tenant',
        },
      ];

      // Act
      render(<Tenants />);

      // Assert — exactly one DangerZone (for the default tenant only)
      expect(screen.getAllByTestId('default-tenant-danger-zone')).toHaveLength(1);
    });

    it('uses settings default_tenant_id for DangerZone matching, not the hardcoded constant', () => {
      // Arrange
      mockDefaultTenantId.current = '11111111-2222-3333-4444-555555555555';
      mockTenants.current = [
        {
          tenant_id: DEFAULT_TENANT_UUID,
          tenant_name: 'Hardcoded constant tenant',
        },
        {
          tenant_id: mockDefaultTenantId.current,
          tenant_name: 'Settings default tenant',
        },
      ];

      // Act
      render(<Tenants />);

      // Assert
      expect(screen.getAllByTestId('default-tenant-danger-zone')).toHaveLength(1);
    });
  });
});
