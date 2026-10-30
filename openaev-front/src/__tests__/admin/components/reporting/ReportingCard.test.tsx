import '@testing-library/jest-dom/vitest';

import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { type Reporting } from '../../../../utils/api-types';

vi.mock('../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (value: string) => value,
    fldt: (value: string) => value,
  }),
}));

// The menu reads permissions from the store; the card only places it.
vi.mock('../../../../admin/components/reporting/ReportingPopover', () => ({ default: () => <button type="button" aria-label="More actions" /> }));

import ReportingCard from '../../../../admin/components/reporting/ReportingCard';

const theme = createTheme();

const buildReporting = (generationStatus?: 'SUCCESS' | 'ERROR'): Reporting => ({
  reporting_id: 'reporting-1',
  reporting_name: 'Quarterly report',
  reporting_description: 'Results of the quarter',
  reporting_context_type: 'SIMULATION',
  reporting_default_format: 'PDF',
  reporting_updated_at: '2026-10-16T10:00:00Z',
  reporting_generations: generationStatus
    ? [{
        reporting_generation_id: 'generation-1',
        reporting_generation_status: generationStatus,
        reporting_generation_document: generationStatus === 'SUCCESS' ? 'document-1' : undefined,
        reporting_generation_created_at: '2026-10-16T10:00:00Z',
      }]
    : [],
} as unknown as Reporting);

const renderCard = (reporting: Reporting) => render(
  <ThemeProvider theme={theme}>
    <TooltipProvider>
      <MemoryRouter>
        <ReportingCard reporting={reporting} />
      </MemoryRouter>
    </TooltipProvider>
  </ThemeProvider>,
);

describe('ReportingCard', () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it('given_aReport_should_linkToItsPageWithTheContextAsOverline', () => {
    // Arrange / Act
    renderCard(buildReporting());

    // Assert
    expect(screen.getByRole('link')).toHaveAttribute('href', '/admin/reporting/reporting-1');
    expect(screen.getByText('Simulation')).toBeInTheDocument();
    expect(screen.getByText('Quarterly report')).toBeInTheDocument();
  });

  it('given_aSuccessfulGeneration_should_downloadItWithoutOpeningTheReport', () => {
    // Arrange
    const assign = vi.fn();
    vi.spyOn(window, 'location', 'get').mockReturnValue({
      ...window.location,
      assign,
    });
    renderCard(buildReporting('SUCCESS'));

    // Act
    const notCanceled = fireEvent.click(screen.getByRole('button', { name: 'Download latest generation' }));

    // Assert
    expect(assign).toHaveBeenCalledWith(expect.stringContaining('/generations/generation-1/file'));
    // The button sits inside the card link: the click must not follow it.
    expect(notCanceled).toBe(false);
  });

  it('given_aFailedGeneration_should_notOfferTheDownload', () => {
    // Arrange / Act
    renderCard(buildReporting('ERROR'));

    // Assert
    expect(screen.queryByRole('button', { name: 'Download latest generation' })).not.toBeInTheDocument();
  });
});
