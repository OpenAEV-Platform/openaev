import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const { composer } = vi.hoisted(() => ({ composer: { reachable: false as boolean | null } }));

vi.mock('../../../../components/i18n', () => ({ useFormatter: () => ({ t: (key: string) => key }) }));
vi.mock('../../../../actions/catalog/catalog-actions', () => ({ fetchCatalogConnectors: vi.fn() }));
vi.mock('../../../../store', () => ({ useHelper: () => ({ catalogConnectors: [] }) }));
vi.mock('../../../../utils/hooks', () => ({ useAppDispatch: () => vi.fn() }));
vi.mock('../../../../utils/hooks/useDataLoader', () => ({ default: vi.fn() }));
vi.mock('../../../../utils/hooks/useEnterpriseEdition', () => ({ default: () => ({ isValidated: true }) }));
vi.mock('../../../../admin/components/integrations/common/useXtmComposerReachable', () => ({ default: () => composer.reachable }));
vi.mock('../../../../components/common/marketplace/page/MarketplaceHero', () => ({ default: () => null }));
vi.mock('../../../../components/common/marketplace/page/MarketplaceTabs', () => ({ default: () => null }));
vi.mock('../../../../components/Loader', () => ({ default: () => null }));

import Integrations from '../../../../admin/components/integrations/Integrations';

const WARNING = 'Some deployment requires the installation of our';

const renderPage = () => render(
  <ThemeProvider theme={createTheme()}>
    <MemoryRouter initialEntries={['/deployed']}>
      <Routes>
        <Route path="/:tab" element={<Integrations />} />
      </Routes>
    </MemoryRouter>
  </ThemeProvider>,
);

describe('Integrations', () => {
  beforeEach(() => {
    localStorage.clear();
    composer.reachable = false;
  });

  afterEach(cleanup);

  it('given_theIntegrationManagerIsDown_should_warnUntilDismissed', () => {
    // Arrange
    renderPage();
    expect(screen.getByText(WARNING)).toBeTruthy();

    // Act
    fireEvent.click(screen.getByRole('button', { name: 'Dismiss' }));

    // Assert
    expect(screen.queryByText(WARNING)).toBeNull();
    expect(localStorage.getItem('integration_manager_warning_dismissed')).toBe('true');
  });

  it.each([
    ['still probed', null],
    ['up', true],
  ])('given_theIntegrationManagerIs_%s_should_notWarn', (_, reachable) => {
    // Arrange
    composer.reachable = reachable;

    // Act
    renderPage();

    // Assert
    expect(screen.queryByText(WARNING)).toBeNull();
  });

  it('given_aWarningDismissedEarlier_should_notWarn', () => {
    // Arrange
    localStorage.setItem('integration_manager_warning_dismissed', 'true');

    // Act
    renderPage();

    // Assert
    expect(screen.queryByText(WARNING)).toBeNull();
  });
});
