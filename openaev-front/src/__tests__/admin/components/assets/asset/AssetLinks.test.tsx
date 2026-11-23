import { TooltipProvider } from '@filigran/design-system';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { type ReactNode } from 'react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { type AssetLinkOutput } from '../../../../../utils/api-types';

const { fetchAssetLinks, deleteAssetLink, addAssetLink } = vi.hoisted(() => ({
  fetchAssetLinks: vi.fn(),
  deleteAssetLink: vi.fn(() => Promise.resolve({})),
  addAssetLink: vi.fn(() => Promise.resolve({})),
}));

vi.mock('../../../../../actions/assets/asset-link-actions', () => ({
  fetchAssetLinks,
  deleteAssetLink,
  addAssetLink,
}));
vi.mock('../../../../../actions/assets/endpoint-actions', () => ({ searchAssetsAsOption: vi.fn(() => Promise.resolve({ data: [] })) }));
vi.mock('../../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (value: string, values?: Record<string, unknown>) => (values
      ? Object.entries(values).reduce((text, [key, val]) => text.replace(`{${key}}`, String(val)), value)
      : value),
  }),
}));
// The drawer's MUI portal and banner settings are not what this test checks: a plain container
// stands in for the common Drawer, rendered only while open.
vi.mock('../../../../../components/common/Drawer', () => ({
  default: ({ open, children, title }: {
    open: boolean;
    children: ReactNode;
    title: string;
  }) => (open
    ? (
        <div data-testid="drawer">
          <h1>{title}</h1>
          {children}
        </div>
      )
    : null),
}));

import AssetLinks from '../../../../../admin/components/assets/asset/AssetLinks';

const link = (target: string, type: AssetLinkOutput['asset_link_type'] = 'USES'): AssetLinkOutput => ({
  asset_link_source_id: 'host',
  asset_link_source_name: 'srv-01',
  asset_link_target_id: target,
  asset_link_target_name: `name-${target}`,
  asset_link_type: type,
});

const renderLinks = (canManage: boolean) => render(
  <TooltipProvider>
    <MemoryRouter>
      <AssetLinks assetId="host" assetName="srv-01" canManage={canManage} />
    </MemoryRouter>
  </TooltipProvider>,
);

describe('AssetLinks', () => {
  beforeEach(() => {
    fetchAssetLinks.mockReset();
    deleteAssetLink.mockClear();
  });
  afterEach(() => cleanup());

  it('shows the links read-only on the overview: no form, no remove button', async () => {
    fetchAssetLinks.mockResolvedValue({ data: [link('a'), link('b', 'HOSTS')] });
    renderLinks(true);
    expect(await screen.findByText('name-a')).not.toBeNull();
    expect(screen.getByText('Relationships (2)')).not.toBeNull();
    expect(screen.queryByRole('button', { name: /Remove link/ })).toBeNull();
    expect(screen.queryByText('Add link')).toBeNull();
    expect(screen.queryByTestId('drawer')).toBeNull();
  });

  it('hides the Manage links action from a user who cannot manage assets', async () => {
    fetchAssetLinks.mockResolvedValue({ data: [link('a')] });
    renderLinks(false);
    expect(await screen.findByText('name-a')).not.toBeNull();
    expect(screen.queryByRole('button', { name: 'Manage links' })).toBeNull();
  });

  it('collapses a long line behind +N more', async () => {
    fetchAssetLinks.mockResolvedValue({ data: ['a', 'b', 'c', 'd', 'e', 'f', 'g'].map(id => link(id)) });
    renderLinks(false);
    expect(await screen.findByText('name-a')).not.toBeNull();
    expect(screen.queryByText('name-g')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: '+2 more' }));
    expect(screen.getByText('name-g')).not.toBeNull();
  });

  it('opens the drawer, removes a link there and refreshes the overview', async () => {
    fetchAssetLinks
      .mockResolvedValueOnce({ data: [link('a'), link('b')] })
      .mockResolvedValue({ data: [link('b')] });
    renderLinks(true);
    expect(await screen.findByText('name-a')).not.toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'Manage links' }));
    const drawer = screen.getByTestId('drawer');
    expect(drawer.textContent).toContain('Manage links');
    expect(drawer.textContent).toContain('Add link');

    fireEvent.click(screen.getByRole('button', { name: 'Remove link: name-a' }));
    expect(deleteAssetLink).toHaveBeenCalledWith('host', 'a', 'USES');
    await waitFor(() => expect(fetchAssetLinks).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(screen.getByText('Relationships (1)')).not.toBeNull());
  });
});
