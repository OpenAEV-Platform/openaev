import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { type ReactElement } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import FindingDetailPanel, { type ProducingAction } from '../../../../../../admin/components/simulations/simulation/attack_path/FindingDetailPanel';

// The execution-status badge does its own fetching (execution detail, then inject status): stub it and
// assert the panel wires each producing action to it, rather than re-testing the badge here.
vi.mock('../../../../../../admin/components/simulations/simulation/attack_path/ExecutionStatusBadge', () => ({
  ExecutionRowStatusBadge: ({ simulationId, executionRef, endpointName }: {
    simulationId: string;
    executionRef?: string;
    endpointName?: string;
  }) => (
    <span data-testid="exec-status" data-sim={simulationId} data-ref={executionRef} data-endpoint={endpointName} />
  ),
}));

vi.mock('../../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (s: string) => s }) }));

const action = (ref: string): ProducingAction => ({
  ref,
  contract: `contract-${ref}`,
  statusColor: '#ff0000',
  statusLabel: 'Undetected',
  subtitle: 'agent · admin',
});

const renderPanel = (props: Partial<Parameters<typeof FindingDetailPanel>[0]> = {}): ReactElement => {
  const element = (
    <ThemeProvider theme={createTheme()}>
      <FindingDetailPanel
        value="445"
        type="port"
        simulationId="sim-1"
        endpointLabel="CORP-HOST"
        endpointName="CORP-HOST"
        actions={[action('exec-1')]}
        activeRef={null}
        onSelect={vi.fn()}
        onClose={vi.fn()}
        {...props}
      />
    </ThemeProvider>
  );
  render(element);
  return element;
};

// An Nmap XML report as an output-only value: long enough to bury the rest of the panel.
const LONG_VALUE = `<?xml version="1.0" encoding="UTF-8"?><nmaprun scanner="nmap" args="nmap -Pn -sT">${'<port protocol="tcp" portid="445"/>'.repeat(20)}</nmaprun>`;

describe('FindingDetailPanel value clamping', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('offers no toggle for a short value', () => {
    renderPanel();
    expect(screen.getByText('445')).toBeDefined();
    expect(screen.queryByText('Show more')).toBeNull();
    expect(screen.queryByText('Show less')).toBeNull();
  });

  it('clamps a long value behind a Show more / Show less toggle', () => {
    renderPanel({ value: LONG_VALUE });

    const more = screen.getByText('Show more');
    expect(more).toBeDefined();

    fireEvent.click(more);
    expect(screen.getByText('Show less')).toBeDefined();
    expect(screen.queryByText('Show more')).toBeNull();

    fireEvent.click(screen.getByText('Show less'));
    expect(screen.getByText('Show more')).toBeDefined();
  });

  it('keeps the toggle when the value sits under the verdict badges', () => {
    renderPanel({
      value: LONG_VALUE,
      expectations: {
        prevention: 'failed',
        detection: 'failed',
        vulnerability: 'success',
      },
    });
    expect(screen.getByText('Show more')).toBeDefined();
  });
});

describe('FindingDetailPanel producing actions', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('shows the run status of every producing action, alongside its verdict', () => {
    renderPanel({ actions: [action('exec-1'), action('exec-2')] });

    const badges = screen.getAllByTestId('exec-status');
    expect(badges).toHaveLength(2);
    expect(badges.map(b => b.getAttribute('data-ref'))).toEqual(['exec-1', 'exec-2']);
    for (const badge of badges) {
      expect(badge.getAttribute('data-sim')).toBe('sim-1');
      expect(badge.getAttribute('data-endpoint')).toBe('CORP-HOST');
    }
    // The detection verdict stays: it answers a different question than "did it run".
    expect(screen.getAllByText('Undetected')).toHaveLength(2);
  });

  it('renders no run status when there is no producing action', () => {
    renderPanel({ actions: [] });
    expect(screen.queryByTestId('exec-status')).toBeNull();
    expect(screen.getByText('No producing action found for this finding')).toBeDefined();
  });
});
