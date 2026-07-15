import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const { mockCan, mockDefaultTenantId } = vi.hoisted(() => ({
  mockCan: vi.fn(),
  mockDefaultTenantId: { current: '2cffad3a-0001-4078-b0e2-ef74274022c3' },
}));

vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (value: string) => value }) }));

vi.mock('../../../../../utils/hooks/useAuth', () => ({
  default: () => ({
    reloadUserTenants: vi.fn(),
    settings: { default_tenant_id: mockDefaultTenantId.current },
  }),
}));

vi.mock('../../../../../utils/permissions/permissionsContext', async () => {
  const React = await import('react');
  const AbilityContext = React.createContext({ can: mockCan } as { can: (action: string, subject: string) => boolean });
  return { AbilityContext };
});

vi.mock('../../../../../components/common/ButtonPopover', () => ({
  default: ({ entries }: {
    entries: Array<{
      label: string;
      userRight: boolean;
    }>;
  }) => (
    <div data-testid="button-popover">
      {entries.filter(e => e.userRight).map(e => (
        <div key={e.label} data-testid={`popover-entry-${e.label.toLowerCase()}`}>{e.label}</div>
      ))}
    </div>
  ),
}));

vi.mock('../../../../../admin/components/platform/tenants/tenant/TenantUpdate', () => ({ default: () => <div data-testid="tenant-update" /> }));

vi.mock('../../../../../components/common/DialogDelete', () => ({ default: () => <div data-testid="dialog-delete" /> }));

vi.mock('../../../../../components/common/DialogConfirmation', () => ({ default: () => <div data-testid="dialog-confirmation" /> }));

vi.mock('../../../../../actions/platform/tenants/tenant-action', () => ({
  softDeleteTenant: vi.fn(),
  reactivateTenant: vi.fn(),
}));

import TenantPopover from '../../../../../admin/components/platform/tenants/TenantPopover';
import type { TenantOutput } from '../../../../../utils/api-types';
import { DEFAULT_TENANT_UUID } from '../../../../../utils/url-helper';

const defaultTenant: TenantOutput = {
  tenant_id: mockDefaultTenantId.current,
  tenant_name: 'Default Tenant',
};

const regularTenant: TenantOutput = {
  tenant_id: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
  tenant_name: 'Regular Tenant',
};

describe('TenantPopover', () => {
  beforeEach(() => {
    mockCan.mockReturnValue(true);
    mockDefaultTenantId.current = DEFAULT_TENANT_UUID;
  });

  afterEach(() => {
    vi.clearAllMocks();
    cleanup();
  });

  describe('default tenant', () => {
    it('hides the Delete action for the default tenant', () => {
      // Arrange & Act
      render(
        <TenantPopover
          tenant={defaultTenant}
          actions={['Update', 'Delete']}
        />,
      );

      // Assert
      expect(screen.queryByTestId('popover-entry-delete')).toBeNull();
    });

    it('shows the Update action for the default tenant', () => {
      // Arrange & Act
      render(
        <TenantPopover
          tenant={defaultTenant}
          actions={['Update', 'Delete']}
        />,
      );

      // Assert
      expect(screen.getByTestId('popover-entry-update')).toBeDefined();
    });
  });

  describe('non-default tenant', () => {
    it('shows the Delete action for a non-default tenant', () => {
      // Arrange & Act
      render(
        <TenantPopover
          tenant={regularTenant}
          actions={['Update', 'Delete']}
        />,
      );

      // Assert
      expect(screen.getByTestId('popover-entry-delete')).toBeDefined();
    });

    it('shows the Update action for a non-default tenant', () => {
      // Arrange & Act
      render(
        <TenantPopover
          tenant={regularTenant}
          actions={['Update', 'Delete']}
        />,
      );

      // Assert
      expect(screen.getByTestId('popover-entry-update')).toBeDefined();
    });
  });

  describe('edge case: tenant_id does not match any known default', () => {
    it('does not flag a tenant with empty id as default (shows Delete)', () => {
      // Arrange
      const unknownTenant: TenantOutput = {
        tenant_id: '',
        tenant_name: 'Unknown Tenant',
      };

      // Act
      render(
        <TenantPopover
          tenant={unknownTenant}
          actions={['Update', 'Delete']}
        />,
      );

      // Assert — empty string != DEFAULT_TENANT_UUID, so Delete is shown
      expect(screen.getByTestId('popover-entry-delete')).toBeDefined();
    });
  });

  it('uses settings default_tenant_id instead of the hardcoded constant', () => {
    // Arrange
    mockDefaultTenantId.current = '11111111-2222-3333-4444-555555555555';
    const tenantWithHardcodedId: TenantOutput = {
      tenant_id: DEFAULT_TENANT_UUID,
      tenant_name: 'Tenant with hardcoded id',
    };

    // Act
    render(
      <TenantPopover
        tenant={tenantWithHardcodedId}
        actions={['Update', 'Delete']}
      />,
    );

    // Assert
    expect(screen.getByTestId('popover-entry-delete')).toBeDefined();
  });
});
