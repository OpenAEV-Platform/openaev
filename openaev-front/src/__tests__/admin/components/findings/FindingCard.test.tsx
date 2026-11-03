import '@testing-library/jest-dom/vitest';

import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { type AggregatedFindingOutput } from '../../../../utils/api-types';

vi.mock('../../../../components/i18n', () => ({ useFormatter: () => ({ t: (value: string) => value }) }));

const fetchVulnerabilityByExternalId = vi.fn();
vi.mock('../../../../actions/vulnerability-actions', () => ({ fetchVulnerabilityByExternalId: (...args: unknown[]) => fetchVulnerabilityByExternalId(...args) }));

const fetchFindingSummary = vi.fn();
vi.mock('../../../../actions/findings/finding-actions', () => ({ fetchFindingSummary: (...args: unknown[]) => fetchFindingSummary(...args) }));

import FindingCard from '../../../../admin/components/findings/FindingCard';

const theme = createTheme();

const buildFinding = (type: AggregatedFindingOutput['finding_type'], value: string): AggregatedFindingOutput => ({
  finding_id: 'finding-1',
  finding_type: type,
  finding_value: value,
  finding_created_at: '2026-10-16T10:00:00Z',
  finding_updated_at: '2026-10-16T10:00:00Z',
  finding_assets: [
    {
      asset_id: 'asset-1',
      asset_name: 'honey.scanme.sh',
    },
    {
      asset_id: 'asset-2',
      asset_name: 'scanme.sh',
    },
  ],
} as unknown as AggregatedFindingOutput);

const renderCard = (finding: AggregatedFindingOutput) => render(
  <ThemeProvider theme={theme}>
    <TooltipProvider>
      <MemoryRouter>
        <FindingCard finding={finding} />
      </MemoryRouter>
    </TooltipProvider>
  </ThemeProvider>,
);

describe('FindingCard', () => {
  beforeEach(() => {
    fetchFindingSummary.mockResolvedValue({ data: { finding_occurrences: 4 } });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('given_aPort_should_showTypeValueAndImpactedAssetsWithoutCvss', () => {
    // Arrange / Act
    renderCard(buildFinding('port', '443'));

    // Assert
    expect(screen.getByRole('link')).toHaveAttribute('href', '/admin/findings/finding-1');
    expect(screen.getByText('Port')).toBeInTheDocument();
    expect(screen.getByText('443')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
    expect(screen.getByText('Impacted assets')).toBeInTheDocument();
    expect(fetchVulnerabilityByExternalId).not.toHaveBeenCalled();
  });

  it('given_aSummary_should_showTheOccurrences', async () => {
    // Arrange / Act
    renderCard(buildFinding('port', '443'));

    // Assert
    expect(await screen.findByText('4')).toBeInTheDocument();
    expect(screen.getByText('Occurrences')).toBeInTheDocument();
    expect(fetchFindingSummary).toHaveBeenCalledWith('finding-1', false);
  });

  it('given_aFailingSummary_should_showNoOccurrences', async () => {
    // Arrange
    fetchFindingSummary.mockRejectedValue(new Error('500'));

    // Act
    renderCard(buildFinding('port', '443'));

    // Assert
    await vi.waitFor(() => expect(fetchFindingSummary).toHaveBeenCalled());
    expect(screen.queryByText('Occurrences')).not.toBeInTheDocument();
  });

  it('given_anyFinding_should_renderNoFooterBand', () => {
    // Arrange / Act
    renderCard(buildFinding('port', '443'));

    // Assert
    expect(screen.queryByRole('button', { name: 'More actions' })).not.toBeInTheDocument();
    expect(screen.getByTestId('finding-card').querySelector('.border-t.bg-elevation-default')).toBeNull();
  });

  it('given_aKnownCve_should_showCvssAndCriticality', async () => {
    // Arrange
    fetchVulnerabilityByExternalId.mockResolvedValue({ data: { vulnerability_cvss_v31: 8.2 } });

    // Act
    renderCard(buildFinding('cve', 'CVE-2023-46805'));

    // Assert
    expect(await screen.findByText('CVSS 8.2')).toBeInTheDocument();
    expect(screen.getByText('8.2')).toBeInTheDocument();
    expect(screen.getByText('High criticality')).toBeInTheDocument();
    expect(fetchVulnerabilityByExternalId).toHaveBeenCalledWith('CVE-2023-46805', false);
  });

  it('given_anUnknownCve_should_showNoCvssNorCriticality', async () => {
    // Arrange
    fetchVulnerabilityByExternalId.mockRejectedValue(new Error('404'));

    // Act
    renderCard(buildFinding('cve', 'CVE-2099-0001'));

    // Assert
    await vi.waitFor(() => expect(fetchVulnerabilityByExternalId).toHaveBeenCalled());
    expect(screen.queryByText(/^CVSS/)).not.toBeInTheDocument();
    expect(screen.queryByText(/criticality$/)).not.toBeInTheDocument();
  });
});
