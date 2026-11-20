import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { type ReactElement } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import ThreatArsenalEmptyState from '../../../../admin/components/threat_arsenal/ThreatArsenalEmptyState';
import { expectLibraryButton } from '../../../utils/designSystemAssertions';

vi.mock('../../../../components/i18n', () => ({ useFormatter: () => ({ t: (s: string) => s }) }));

const renderState = (element: ReactElement) => render(<ThemeProvider theme={createTheme()}>{element}</ThemeProvider>);

describe('ThreatArsenalEmptyState', () => {
  afterEach(cleanup);

  it('given_activeFilters_should_offerToResetThem', () => {
    // Arrange
    const onResetFilters = vi.fn();
    renderState(<ThreatArsenalEmptyState hasFilters onResetFilters={onResetFilters} />);
    const reset = screen.getByRole('button', { name: 'Reset filters' });

    // Act
    fireEvent.click(reset);

    // Assert
    expect(screen.getByText('No actions match your filters')).toBeTruthy();
    expectLibraryButton(reset, 'Reset filters', { priority: 'secondary' });
    expect(onResetFilters).toHaveBeenCalledOnce();
  });

  it('given_noFilter_should_sayTheArsenalIsEmptyWithoutReset', () => {
    // Act
    renderState(<ThreatArsenalEmptyState hasFilters={false} onResetFilters={vi.fn()} />);

    // Assert
    expect(screen.getByText('Your threat arsenal is empty')).toBeTruthy();
    expect(screen.queryByRole('button')).toBeNull();
  });

  it('given_anotherTabText_should_showIt', () => {
    // Act
    renderState(
      <ThreatArsenalEmptyState
        hasFilters={false}
        onResetFilters={vi.fn()}
        empty={{
          title: 'Catalogue empty',
          description: 'Nothing to install',
        }}
      />,
    );

    // Assert
    expect(screen.getByText('Catalogue empty')).toBeTruthy();
    expect(screen.getByText('Nothing to install')).toBeTruthy();
  });
});
