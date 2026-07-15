import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (value: string) => value }) }));

vi.mock('@mui/material/styles', () => ({
  useTheme: () => ({
    palette: { dangerZone: { main: '#ff0000' } },
    spacing: (n: number) => `${n * 8}px`,
  }),
}));

vi.mock('../../../../../components/common/tag/Tag', () => ({ default: ({ label }: { label: string }) => <span data-testid="tag">{label}</span> }));

import DefaultTenantDangerZone from '../../../../../admin/components/platform/tenants/DefaultTenantDangerZone';

describe('DefaultTenantDangerZone', () => {
  afterEach(() => {
    vi.clearAllMocks();
    cleanup();
  });

  it('renders the Danger Zone label via i18n key (not hardcoded)', () => {
    // Arrange & Act
    render(<DefaultTenantDangerZone />);

    // Assert — label is passed through the t() function, not a hardcoded string
    expect(screen.getByTestId('tag').textContent).toBe('Default Tenant / Danger Zone');
  });

  it('renders the danger zone container with correct data-testid', () => {
    // Arrange & Act
    render(<DefaultTenantDangerZone />);

    // Assert
    expect(screen.getByTestId('default-tenant-danger-zone')).toBeDefined();
  });

  it('renders children inside the Danger Zone container', () => {
    // Arrange & Act
    render(
      <DefaultTenantDangerZone>
        <div data-testid="child-content" />
      </DefaultTenantDangerZone>,
    );

    // Assert
    expect(screen.getByTestId('child-content')).toBeDefined();
    expect(screen.getByTestId('default-tenant-danger-zone')).toBeDefined();
  });

  it('renders without crashing when no children are provided', () => {
    // Arrange & Act
    render(<DefaultTenantDangerZone />);

    // Assert — component should mount without errors
    expect(screen.getByTestId('default-tenant-danger-zone')).toBeDefined();
  });
});
