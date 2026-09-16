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

    expect(await screen.findByText('Executed')).toBeDefined();
    expect(mocks.searchTargets).not.toHaveBeenCalled();
  });

  // The graph row now carries injectId/payloadId/executionStatus (AttackPathGraphService resolves them
  // from the durable step), so the common case renders with no round-trip at all.
  it('renders the graph-provided status immediately, with no fetch, for a settled injector row', async () => {
    renderWithTheme(
      <ExecutionRowStatusBadge
        simulationId="sim-1"
        executionRef="exec-ref-3"
        injectId="inject-3"
        executionStatus="EXECUTED"
      />,
    );

    expect(screen.getByText('Executed')).toBeDefined();
    await waitFor(() => {
      expect(mocks.fetchExecutionDetail).not.toHaveBeenCalled();
    });
    expect(mocks.getInjectStatusWithGlobalExecutionTraces).not.toHaveBeenCalled();
  });

  it('still resolves per target when the row is payload-backed, without refetching the detail', async () => {
    mocks.searchTargets.mockResolvedValue({
      data: {
        content: [{
          target_id: 'target-4',
          target_type: 'ASSETS',
          target_name: 'CORP-HOST',
        }],
      },
    });
    mocks.fetchInjectExecutionResult.mockResolvedValue({
      data: {
        execution_traces: {
          'target-4': [{
            execution_action: 'COMPLETE',
            execution_status: 'TIMEOUT',
            execution_time: '2026-01-01T00:00:00Z',
          }],
        },
      },
    });

    renderWithTheme(
      <ExecutionRowStatusBadge
        simulationId="sim-1"
        executionRef="exec-ref-4"
        endpointName="CORP-HOST"
        injectId="inject-4"
        payloadId="payload-4"
        executionStatus="EXECUTED"
      />,
    );

    // The inject ran, but THIS agent timed out: the per-target status wins — resolved straight from
    // the graph-provided injectId/payloadId, with no detail round-trip.
    expect(await screen.findByText('Timeout')).toBeDefined();
    expect(mocks.fetchExecutionDetail).not.toHaveBeenCalled();
  });

  // A payload action whose per-agent resolution yields nothing used to render a blank Execution
  // cell even though the action clearly ran (issue 7337): the graph-shipped inject-level status now
  // fills that hole, and only that hole - the per-agent chip still wins when traces resolve (covered
  // by the per-target test above).
  it('falls back to the inject-level chip when the per-agent result resolves empty', async () => {
    mocks.searchTargets.mockResolvedValue({
      data: {
        content: [{
          target_id: 'target-7',
          target_type: 'ASSETS',
          target_name: 'CORP-HOST',
        }],
      },
    });
    mocks.fetchInjectExecutionResult.mockResolvedValue({ data: null });

    renderWithTheme(
      <ExecutionRowStatusBadge
        simulationId="sim-1"
        executionRef="exec-ref-7"
        endpointName="CORP-HOST"
        injectId="inject-7"
        payloadId="payload-7"
        executionStatus="EXECUTED"
      />,
    );

    expect(await screen.findByText('Executed')).toBeDefined();
    expect(mocks.fetchInjectExecutionResult).toHaveBeenCalledWith('inject-7', 'target-7', 'ASSETS');
  });

  it('falls back to the inject-level chip when the per-agent result fetch fails', async () => {
    mocks.searchTargets.mockResolvedValue({
      data: {
        content: [{
          target_id: 'target-8',
          target_type: 'ASSETS',
          target_name: 'CORP-HOST',
        }],
      },
    });
    mocks.fetchInjectExecutionResult.mockRejectedValue(new Error('boom'));

    renderWithTheme(
      <ExecutionRowStatusBadge
        simulationId="sim-1"
        executionRef="exec-ref-8"
        endpointName="CORP-HOST"
        injectId="inject-8"
        payloadId="payload-8"
        executionStatus="EXECUTED"
      />,
    );

    expect(await screen.findByText('Executed')).toBeDefined();
  });

  // A seeded/demo snapshot ships no live inject status: the historical empty render is kept rather
  // than inventing a status.
  it('keeps the empty render when the per-agent result is empty and there is no fallback status', async () => {
    mocks.searchTargets.mockResolvedValue({
      data: {
        content: [{
          target_id: 'target-9',
          target_type: 'ASSETS',
          target_name: 'CORP-HOST',
        }],
      },
    });
    mocks.fetchInjectExecutionResult.mockResolvedValue({ data: null });

    const { container } = renderWithTheme(
      <ExecutionRowStatusBadge
        simulationId="sim-1"
        executionRef="exec-ref-9"
        endpointName="CORP-HOST"
        injectId="inject-9"
        payloadId="payload-9"
      />,
    );

    await waitFor(() => {
      expect(mocks.fetchInjectExecutionResult).toHaveBeenCalled();
    });
    expect(container.textContent).toBe('');
  });

  it('refines a non-terminal graph status instead of trusting it, straight from its injectId', async () => {
    mocks.getInjectStatusWithGlobalExecutionTraces.mockResolvedValue({
      data: {
        status_id: 'status-5',
        status_name: 'EXECUTED',
      },
    });

    renderWithTheme(
      <ExecutionRowStatusBadge
        simulationId="sim-1"
        executionRef="exec-ref-5"
        injectId="inject-5"
        executionStatus="PENDING"
      />,
    );

    expect(await screen.findByText('Executed')).toBeDefined();
    // The graph already named the inject: refining its status needs no detail fetch.
    expect(mocks.fetchExecutionDetail).not.toHaveBeenCalled();
    expect(mocks.getInjectStatusWithGlobalExecutionTraces).toHaveBeenCalledWith('inject-5');
  });

  it('drops a fetched status when the execution ref goes away instead of keeping it stale', async () => {
    mocks.fetchExecutionDetail.mockResolvedValue({ data: { injectId: 'inject-6' } });
    mocks.getInjectStatusWithGlobalExecutionTraces.mockResolvedValue({
      data: {
        status_id: 'status-6',
        status_name: 'EXECUTED',
      },
    });

    const { rerender, container } = renderWithTheme(
      <ExecutionRowStatusBadge simulationId="sim-1" executionRef="exec-ref-6" />,
    );
    expect(await screen.findByText('Executed')).toBeDefined();

    rerender(
      <ThemeProvider theme={createTheme()}>
        <ExecutionRowStatusBadge simulationId="sim-1" />
      </ThemeProvider>,
    );

    await waitFor(() => {
      expect(container.textContent).toBe('');
    });
  });

  it('renders nothing and fires no resolution without an execution ref', async () => {
    const { container } = renderWithTheme(<ExecutionRowStatusBadge simulationId="sim-1" />);

    await waitFor(() => {
      expect(mocks.fetchExecutionDetail).not.toHaveBeenCalled();
    });
    expect(container.textContent).toBe('');
  });
});
