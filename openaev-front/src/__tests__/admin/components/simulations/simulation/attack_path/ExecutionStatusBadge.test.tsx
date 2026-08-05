import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { type ReactElement } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ExecutionRowStatusBadge } from '../../../../../../admin/components/simulations/simulation/attack_path/ExecutionStatusBadge';

// The endpoint/injector list rows only carry an execution ref — the badge has to resolve the
// inject/payload ids first, then delegate to the same payload/injector badges the Result tab uses.
const mocks = vi.hoisted(() => ({
  fetchExecutionDetail: vi.fn(),
  searchTargets: vi.fn(),
  getInjectStatusWithGlobalExecutionTraces: vi.fn(),
  fetchInjectExecutionResult: vi.fn(),
}));

vi.mock('../../../../../../actions/attack-path/attack-path-actions', () => ({ fetchExecutionDetail: mocks.fetchExecutionDetail }));

vi.mock('../../../../../../actions/injects/inject-action', () => ({
  searchTargets: mocks.searchTargets,
  getInjectStatusWithGlobalExecutionTraces: mocks.getInjectStatusWithGlobalExecutionTraces,
}));

vi.mock('../../../../../../actions/inject_status/inject-status-action', () => ({ fetchInjectExecutionResult: mocks.fetchInjectExecutionResult }));

vi.mock('../../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (s: string) => s }) }));

const renderWithTheme = (element: ReactElement) => render(
  <ThemeProvider theme={createTheme()}>{element}</ThemeProvider>,
);

describe('ExecutionRowStatusBadge', () => {
  beforeEach(() => {
    mocks.fetchExecutionDetail.mockResolvedValue({ data: null });
    mocks.searchTargets.mockResolvedValue({ data: { content: [] } });
    mocks.getInjectStatusWithGlobalExecutionTraces.mockResolvedValue({ data: null });
    mocks.fetchInjectExecutionResult.mockResolvedValue({ data: null });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('resolves the row ref then shows the per-target chip for a payload-backed execution', async () => {
    mocks.fetchExecutionDetail.mockResolvedValue({
      data: {
        injectId: 'inject-1',
        payloadId: 'payload-1',
      },
    });
    mocks.searchTargets.mockResolvedValue({
      data: {
        content: [{
          target_id: 'target-1',
          target_type: 'ASSETS',
          target_name: 'CORP-HOST',
        }],
      },
    });
    mocks.fetchInjectExecutionResult.mockResolvedValue({
      data: {
        execution_traces: {
          'target-1': [{
            execution_action: 'COMPLETE',
            execution_status: 'TIMEOUT',
            execution_time: '2026-01-01T00:00:00Z',
          }],
        },
      },
    });

    renderWithTheme(<ExecutionRowStatusBadge simulationId="sim-1" executionRef="exec-ref-1" endpointName="CORP-HOST" />);

    expect(await screen.findByText('Timeout')).toBeDefined();
    expect(mocks.fetchExecutionDetail).toHaveBeenCalledWith('sim-1', 'exec-ref-1');
    expect(mocks.fetchInjectExecutionResult).toHaveBeenCalledWith('inject-1', 'target-1', 'ASSETS');
  });

  it('shows the inject-level chip for a network-injector execution', async () => {
    mocks.fetchExecutionDetail.mockResolvedValue({ data: { injectId: 'inject-2' } });
    mocks.getInjectStatusWithGlobalExecutionTraces.mockResolvedValue({
      data: {
        status_id: 'status-1',
        status_name: 'EXECUTED',
      },
    });

    renderWithTheme(<ExecutionRowStatusBadge simulationId="sim-1" executionRef="exec-ref-2" />);

    expect(await screen.findByText('EXECUTED')).toBeDefined();
    expect(mocks.searchTargets).not.toHaveBeenCalled();
  });

  it('renders nothing and fires no resolution without an execution ref', async () => {
    const { container } = renderWithTheme(<ExecutionRowStatusBadge simulationId="sim-1" />);

    await waitFor(() => {
      expect(mocks.fetchExecutionDetail).not.toHaveBeenCalled();
    });
    expect(container.textContent).toBe('');
  });
});
