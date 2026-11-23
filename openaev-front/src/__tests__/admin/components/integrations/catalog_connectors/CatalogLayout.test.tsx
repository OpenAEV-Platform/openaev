import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useOutletContext } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

const { composer } = vi.hoisted(() => ({ composer: { reachable: null as boolean | null } }));

vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (key: string) => key }) }));
vi.mock('../../../../../actions/catalog/catalog-actions', () => ({
  fetchCatalogConnectors: vi.fn(),
  fetchConnector: vi.fn(),
}));
vi.mock('../../../../../store', () => ({ useHelper: () => ({ catalogConnector: { catalog_connector_title: 'Nmap' } }) }));
vi.mock('../../../../../utils/hooks', () => ({ useAppDispatch: () => vi.fn() }));
vi.mock('../../../../../utils/hooks/useDataLoader', () => ({ default: vi.fn() }));
vi.mock('../../../../../admin/components/integrations/common/useXtmComposerReachable', () => ({ default: () => composer.reachable }));
vi.mock('../../../../../components/Breadcrumbs', () => ({ default: ({ elements }: { elements: { label: string }[] }) => <nav>{elements.map(element => element.label).join(' / ')}</nav> }));

import CatalogLayout, { type CatalogContextType } from '../../../../../admin/components/integrations/catalog_connectors/CatalogLayout';

const ComposerProbe = () => {
  const { isXtmComposerUp } = useOutletContext<CatalogContextType>();
  return <p>{`Composer up: ${isXtmComposerUp}`}</p>;
};

const renderLayout = () => render(
  <MemoryRouter initialEntries={['/nmap']}>
    <Routes>
      <Route path="/:catalogConnectorId" element={<CatalogLayout />}>
        <Route index element={<ComposerProbe />} />
      </Route>
    </Routes>
  </MemoryRouter>,
);

describe('CatalogLayout', () => {
  afterEach(cleanup);

  it.each([
    [true, true],
    [false, false],
    [null, false],
  ])('given_theProbeSays_%s_should_tellTheConnectorPage_%s', (reachable, expected) => {
    // Arrange
    composer.reachable = reachable;

    // Act
    renderLayout();

    // Assert
    expect(screen.getByText(`Composer up: ${expected}`)).toBeTruthy();
    expect(screen.getByText('Integrations / Nmap')).toBeTruthy();
  });
});
