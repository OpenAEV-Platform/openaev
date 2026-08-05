import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import ExecutionResultTerminalPanel from '../../../../../../admin/components/simulations/simulation/attack_path/ExecutionResultTerminalPanel';
import type { AttackPathExecutionDetailDTO } from '../../../../../../utils/api-types';

// The Result tab's new execution-status badge (issue 244) is what these tests pin down: an
// execution that technically failed must be distinguishable from one that ran cleanly but simply
// went undetected. Stub the action layer (the badge resolves its data itself) and i18n.
const mocks = vi.hoisted(() => ({
  searchTargets: vi.fn(),
  getInjectStatusWithGlobalExecutionTraces: vi.fn(),
  fetchInjectExecutionResult: vi.fn(),
  fetchExecutionDetail: vi.fn(),
}));

vi.mock('../../../../../../actions/injects/inject-action', () => ({
  searchTargets: mocks.searchTargets,
  getInjectStatusWithGlobalExecutionTraces: mocks.getInjectStatusWithGlobalExecutionTraces,
}));

vi.mock('../../../../../../actions/inject_status/inject-status-action', () => ({ fetchInjectExecutionResult: mocks.fetchInjectExecutionResult }));

vi.mock('../../../../../../actions/attack-path/attack-path-actions', () => ({ fetchExecutionDetail: mocks.fetchExecutionDetail }));

vi.mock('../../../../../../actions/findings/finding-actions', () => ({ searchDistinctFindingsForInjects: vi.fn() }));

vi.mock('../../../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (s: string) => s,
    fldt: (d: string) => d,
    du: (d: number) => String(d),
  }),
}));

// Licence validated so the EE attribution chip stays out of the Result tab under test.
vi.mock('../../../../../../utils/hooks/useEnterpriseEdition', () => ({ default: () => ({ isValidated: true }) }));

// Heavy list machinery (queryable pagination + local storage) irrelevant to the badge under test.
vi.mock('../../../../../../admin/components/findings/FindingList', () => ({ default: () => null }));

const renderPanel = (detail: AttackPathExecutionDetailDTO, endpointLabel?: string) => render(
  <ThemeProvider theme={createTheme()}>
    <ExecutionResultTerminalPanel
      loading={false}
      detail={detail}
      onClose={() => {}}
      endpointLabel={endpointLabel}
    />
  </ThemeProvider>,
);

describe('ExecutionResultTerminalPanel execution-status badge', () => {
  beforeEach(() => {
    mocks.searchTargets.mockResolvedValue({ data: { content: [] } });
    mocks.getInjectStatusWithGlobalExecutionTraces.mockResolvedValue({ data: null });
    mocks.fetchInjectExecutionResult.mockResolvedValue({ data: null });
    mocks.fetchExecutionDetail.mockResolvedValue({ data: null });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('shows the inject-level status chip for a network-injector execution', async () => {
    mocks.getInjectStatusWithGlobalExecutionTraces.mockResolvedValue({
      data: {
        status_id: 'status-1',
        status_name: 'ERROR',
      },
    });

    renderPanel({
      injectId: 'inject-1',
      endpointKey: 'ep-1',
      payloadName: 'Nmap scan',
    } as AttackPathExecutionDetailDTO, 'CORP-HOST');

    // The technical verdict surfaces in the Result tab even though the run produced no
    // prevention/detection rows at all.
    expect(await screen.findByText('ERROR')).toBeDefined();
    expect(mocks.getInjectStatusWithGlobalExecutionTraces).toHaveBeenCalledWith('inject-1');
    // A network injector never goes through the per-target payload pipeline.
    expect(mocks.fetchInjectExecutionResult).not.toHaveBeenCalled();
  });

  it('shows the per-target trace status chip for a payload-backed execution', async () => {
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
            execution_status: 'ACCESS_DENIED',
            execution_time: '2026-01-01T00:00:00Z',
          }],
        },
      },
    });

    renderPanel({
      injectId: 'inject-2',
      payloadId: 'payload-1',
      endpointKey: 'ep-2',
      targetHostname: 'CORP-HOST',
    } as AttackPathExecutionDetailDTO, 'CORP-HOST');

    // ACCESS_DENIED reads as "Access denied" — the run was technically blocked, which the
    // detection verdicts alone would have flattened into a plain "not detected".
    expect(await screen.findByText('Access denied')).toBeDefined();
    // The per-target fetch fires only once the target is resolved (never with a placeholder).
    expect(mocks.fetchInjectExecutionResult).toHaveBeenCalledWith('inject-2', 'target-1', 'ASSETS');
    expect(mocks.getInjectStatusWithGlobalExecutionTraces).not.toHaveBeenCalled();
  });

  it('renders no badge and fires no fetch for a seeded snapshot without an inject', async () => {
    renderPanel({
      endpointKey: 'ep-3',
      command: 'whoami',
      terminalOutput: 'root',
    } as AttackPathExecutionDetailDTO, 'CORP-HOST');

    expect(await screen.findByText('CORP-HOST')).toBeDefined();
    await waitFor(() => {
      expect(mocks.searchTargets).not.toHaveBeenCalled();
      expect(mocks.getInjectStatusWithGlobalExecutionTraces).not.toHaveBeenCalled();
      expect(mocks.fetchInjectExecutionResult).not.toHaveBeenCalled();
    });
  });
});
