import '@testing-library/jest-dom/vitest';

import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { type ReactNode } from 'react';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { type ConnectorItem } from '../../../../../admin/components/integrations/catalog_connectors/catalog-facets';

vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (value: string) => value }) }));

import CatalogConnectorCard from '../../../../../admin/components/integrations/catalog_connectors/CatalogConnectorCard';

const theme = createTheme();

const buildConnector = (overrides: Partial<ConnectorItem> = {}): ConnectorItem => ({
  id: 'connector-1',
  title: 'CrowdStrike',
  description: 'Validate detection and prevention expectations',
  type: 'COLLECTOR',
  useCases: ['SECURITY_RESPONSE', 'payload_collection'],
  verified: true,
  external: true,
  deployedCount: 0,
  detailUrl: '/admin/integrations/catalog/connector-1',
  ...overrides,
});

const renderCard = (connector: ConnectorItem, footerAction?: ReactNode) => render(
  <ThemeProvider theme={theme}>
    <TooltipProvider>
      <MemoryRouter>
        <CatalogConnectorCard connector={connector} footerAction={footerAction} />
      </MemoryRouter>
    </TooltipProvider>
  </ThemeProvider>,
);

describe('CatalogConnectorCard', () => {
  afterEach(cleanup);

  it('given_aConnector_should_showItsTypeTitleAndFirstUseCase', () => {
    // Arrange / Act
    renderCard(buildConnector(), <button type="button" aria-label="Deploy" />);

    // Assert
    expect(screen.getByTestId('connector-card')).toBeInTheDocument();
    expect(screen.getByText('Collector')).toBeInTheDocument();
    expect(screen.getByText('CrowdStrike')).toBeInTheDocument();
    expect(screen.getByText('Security response')).toBeInTheDocument();
    expect(screen.getByText('+1')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Deploy' })).toBeInTheDocument();
  });

  it('given_aDetailPage_should_linkToIt', () => {
    // Arrange / Act
    renderCard(buildConnector());

    // Assert
    expect(screen.getByRole('link')).toHaveAttribute('href', '/admin/integrations/catalog/connector-1');
  });

  it('given_noDetailPage_should_renderADisabledCard', () => {
    // Arrange / Act
    renderCard(buildConnector({ detailUrl: undefined }));

    // Assert
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });
});
