import { cleanup, render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const { mockIsFeatureEnabled, mockCan } = vi.hoisted(() => ({
  mockIsFeatureEnabled: vi.fn(),
  mockCan: vi.fn(),
}));

vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (value: string) => value }) }));

vi.mock('../../../../../utils/utils', () => ({ isFeatureEnabled: mockIsFeatureEnabled }));

vi.mock('../../../../../utils/hooks/useAuth', () => ({ default: () => ({ settings: { default_tenant_id: '2cffad3a-0001-4078-b0e2-ef74274022c3' } }) }));

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

vi.mock('../../../../../components/common/list/PaginatedList', () => ({ default: () => <div data-testid="paginated-list" /> }));

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
    tenants: [],
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

vi.mock('../../../../../admin/components/platform/tenants/tenants.queryable', () => ({
  ENTITY_TENANT_PREFIX: 'tenant',
  getTenantHeaders: () => [],
  LOCAL_STORAGE_KEY_TENANT: 'tenant-queryable',
  TENANT_FILTERS: [],
  TENANT_INLINE_STYLES: {},
  TENANT_SORTS: [],
}));

import Tenants from '../../../../../admin/components/platform/tenants/Tenants';

// Validates that the Tenants page renders or hides UI based on permissions and feature-flag state.
describe('Tenants', () => {
  beforeEach(() => {
    mockCan.mockReturnValue(true);
    mockIsFeatureEnabled.mockReturnValue(true);
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
});
