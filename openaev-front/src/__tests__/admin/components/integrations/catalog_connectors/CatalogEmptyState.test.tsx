import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import CatalogEmptyState from '../../../../../admin/components/integrations/catalog_connectors/CatalogEmptyState';
import { expectLibraryButton } from '../../../../utils/designSystemAssertions';

vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (s: string) => s }) }));

describe('CatalogEmptyState', () => {
  afterEach(cleanup);

  it('given_noMatchingConnector_should_offerToResetTheFilters', () => {
    // Arrange
    const onResetFilters = vi.fn();
    render(<ThemeProvider theme={createTheme()}><CatalogEmptyState onResetFilters={onResetFilters} /></ThemeProvider>);
    const reset = screen.getByRole('button', { name: 'Reset filters' });

    // Act
    fireEvent.click(reset);

    // Assert
    expect(screen.getByText('No connectors match your filters')).toBeTruthy();
    expectLibraryButton(reset, 'Reset filters', {
      priority: 'secondary',
      size: 'sm',
    });
    expect(onResetFilters).toHaveBeenCalledOnce();
  });
});
