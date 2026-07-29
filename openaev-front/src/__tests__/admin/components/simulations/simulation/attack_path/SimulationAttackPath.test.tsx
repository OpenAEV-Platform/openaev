import { createTheme, ThemeProvider } from '@mui/material/styles';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { type ReactNode } from 'react';
import type * as ReactRouter from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import SimulationAttackPath from '../../../../../../admin/components/simulations/simulation/attack_path/SimulationAttackPath';

type FlowProps = {
  focusRequest?: { nodeId: string };
  fitRequest?: number;
  onEndpointClick?: (nodeId: string, ref?: string, label?: string) => void;
  onInjectorSelect?: (injectorId: string, label?: string) => void;
};

// Capture the props AttackPathFlow receives (mocked so ReactFlow is not instantiated), and stub the
// action layer + i18n + router so the test drives only the drawer and the cross-focus wiring.
const mocks = vi.hoisted(() => ({
  fetchAttackPathGraph: vi.fn(),
  fetchAttackPathGraphDelta: vi.fn(),
  fetchAttackPathSimulations: vi.fn(),
  fetchSimulationsMetaById: vi.fn(),
  fetchEndpointFindings: vi.fn(),
  fetchEndpointRelations: vi.fn(),
  fetchFindingsByCategory: vi.fn(),
  fetchExecutionDetail: vi.fn(),
  flowProps: { current: null as FlowProps | null },
  // Read by the useEnterpriseEdition mock below; a test flips it to exercise the unlicensed path.
  licenceValidated: true,
}));

vi.mock('../../../../../../actions/attack-path/attack-path-actions', () => ({
  fetchAttackPathGraph: mocks.fetchAttackPathGraph,
  fetchAttackPathGraphDelta: mocks.fetchAttackPathGraphDelta,
  fetchAttackPathSimulations: mocks.fetchAttackPathSimulations,
  fetchSimulationsMetaById: mocks.fetchSimulationsMetaById,
  fetchEndpointFindings: mocks.fetchEndpointFindings,
  fetchEndpointRelations: mocks.fetchEndpointRelations,
  fetchFindingsByCategory: mocks.fetchFindingsByCategory,
  fetchExecutionDetail: mocks.fetchExecutionDetail,
}));

vi.mock('../../../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (s: string) => s,
    fldt: (d: string) => d,
  }),
}));

vi.mock('../../../../../../admin/components/simulations/simulation/attack_path/AttackPathFlow', () => ({
  default: (props: FlowProps) => {
    mocks.flowProps.current = props;
    return <div data-testid="attack-path-flow" />;
  },
}));

// The shared Drawer pulls in useAuth (needs a user context we do not set up here); stub it to render
// its title and children when open so the findings drawer stays testable.
vi.mock('../../../../../../components/common/Drawer', () => ({
  default: ({ open, title, children }: {
    open: boolean;
    title: string;
    children: ReactNode;
  }) =>
    (open
      ? (
          <div data-testid="shared-drawer">
            <span>{title}</span>
            {children}
          </div>
        )
      : null),
}));

vi.mock('react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof ReactRouter>();
  return {
    ...actual,
    useParams: () => ({ exerciseId: 'sim-1' }),
    useNavigate: () => vi.fn(),
  };
});

const ENDPOINT_NODE = 'NODE_ENDPOINT|host-x';

const graphDto = {
  mode: 'collapsed',
  counters: {
    endpoints: 1,
    credentials: 1,
    users: 0,
    cves: 0,
    ports: 0,
  },
  attackPathNodes: [{
    id: 'NODE_INJECTOR|nmap',
    type: 'INJECTOR',
    label: 'nmap',
  }, {
    id: ENDPOINT_NODE,
    type: 'ASSET',
    label: 'CORP-HOST',
    hostname: 'CORP-HOST',
    ref: 'host-x',
    findingCounts: { credentials: 1 },
  }],
  attackPathEdges: [{
    edgeId: 'edge-nmap-host-x',
    edgeSourceId: 'NODE_INJECTOR|nmap',
    edgeTargetId: ENDPOINT_NODE,
    type: 'EDGE_EXECUTIONS',
    count: 1,
  }],
  attackPathExecutions: [],
  staticAttackPathFindings: [],
};

// The Result panel gates platform attribution on the Enterprise licence. Most tests are about the
// panel's content, so they run licensed; the unlicensed affordance flips this flag in its own test.
vi.mock('../../../../../../utils/hooks/useEnterpriseEdition', () => ({
  default: () => ({
    isValidated: mocks.licenceValidated,
    openDialog: vi.fn(),
    setEEFeatureDetectedInfo: vi.fn(),
  }),
}));

// The shared EE chip reads `theme.palette.ee`, which the bare test theme does not carry; it has its
// own coverage, so stand it in by a marker and assert the panel renders it.
vi.mock('../../../../../../admin/components/common/entreprise_edition/EEChip', () => ({ default: () => <span data-testid="ee-chip" /> }));

const wrapper = ({ children }: { children: ReactNode }) => (
  <ThemeProvider theme={createTheme()}>{children}</ThemeProvider>
);

// One steady-state graph read plus a delta poll that reports "nothing changed": the default the tests
// override when they exercise a live update.
const setup = () => {
  mocks.fetchAttackPathGraph.mockResolvedValue({ data: graphDto });
  mocks.fetchAttackPathGraphDelta.mockResolvedValue({
    data: {
      sinceVersion: 0,
      newVersion: 0,
    },
  });
  mocks.fetchAttackPathSimulations.mockResolvedValue({ data: [] });
  mocks.fetchSimulationsMetaById.mockResolvedValue({ data: [] });
  mocks.fetchEndpointFindings.mockResolvedValue({
    data: {
      findingTypes: [],
      findings: [],
    },
  });
  mocks.fetchEndpointRelations.mockResolvedValue({
    data: {
      executions: [{
        id: 'NODE_EXEC|exec-1',
        ref: 'exec-1',
        type: 'EXECUTION',
        payloadName: 'nmap',
        status: 'RED',
      }],
      // The injector's execution edge, so the injector panel can scope to its own executions.
      edges: [{
        edgeSourceId: 'NODE_INJECTOR|nmap',
        edgeTargetId: ENDPOINT_NODE,
        type: 'EDGE_EXECUTIONS',
        executionIds: ['exec-1'],
      }],
    },
  });
  mocks.fetchFindingsByCategory.mockResolvedValue({
    data: {
      total: 1,
      items: [{
        type: 'credentials',
        value: 'admin:••••',
        endpointKey: 'host-x',
        endpointNodeId: ENDPOINT_NODE,
        executionIds: ['exec-1'],
      }],
    },
  });
  mocks.fetchExecutionDetail.mockResolvedValue({
    data: {
      payloadName: 'nmap',
      agentName: 'agent-x',
      agentPrivilege: 'user',
      endpointKey: 'host-x',
      targetHostname: 'CORP-HOST',
      targetIp: '10.0.0.5',
      targetPlatform: 'Linux',
      preventionStatus: 'FAILED',
      detectionStatus: 'DETECTED',
      // The platforms that actually acted, as the backend resolves them from the inject's
      // expectations — the panel renders these, it never fabricates a platform row.
      securityPlatforms: [{
        platformName: 'CrowdStrike',
        platformType: 'openaev_crowdstrike',
        bucket: 'detection',
        status: 'SUCCESS',
        alerts: [{
          id: 'alert-1',
          title: 'Suspicious activity flagged',
        }],
      }],
      findings: [{
        type: 'credentials',
        value: 'admin:••••',
      }],
      command: 'nmap -p 445 host-x -u admin -p ••••',
      terminalOutput: 'open\nclosed',
    },
  });
  return render(<SimulationAttackPath />, { wrapper });
};

describe('SimulationAttackPath findings drawer + cross-focus', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    mocks.flowProps.current = null;
    mocks.licenceValidated = true;
  });

  it('opens a category drawer, lists the findings, and shows the finding attack path on item click', async () => {
    setup();

    // Widgets appear once the graph counters are loaded; open the credentials drawer.
    const credentialsWidget = await screen.findByRole('button', { name: /Credentials/ });
    fireEvent.click(credentialsWidget);
    expect(mocks.fetchFindingsByCategory).toHaveBeenCalledWith('sim-1', 'credentials', 0, 1000);

    // The drawer renders the fetched finding; the client re-masks defensively (server also masks).
    const item = await screen.findByText('admin : ••••••');
    fireEvent.click(item);

    // Clicking the finding refocuses the map on its attack path (fit requested) and loads the
    // endpoint's feed for detail.
    await waitFor(() => {
      expect(mocks.fetchEndpointRelations).toHaveBeenCalledWith('sim-1', 'host-x');
      expect(mocks.flowProps.current?.fitRequest ?? 0).toBeGreaterThan(0);
    });

    // The feed panel is titled with the endpoint's friendly hostname, not the raw key.
    expect(await screen.findByText(/CORP-HOST/)).toBeTruthy();
  });

  it('opens the result & terminal panel for a clicked execution and focuses its endpoint on the map', async () => {
    setup();

    // Select the endpoint on the map (mocked flow) to load its execution feed, without a focus request.
    await screen.findByTestId('attack-path-flow');
    await act(async () => {
      mocks.flowProps.current?.onEndpointClick?.(ENDPOINT_NODE, 'host-x', 'CORP-HOST');
    });

    // Click the endpoint's execution in the feed: its detail is fetched by raw id and the panel opens.
    const feedRow = await screen.findByText('nmap');
    fireEvent.click(feedRow);
    await waitFor(() => expect(mocks.fetchExecutionDetail).toHaveBeenCalledWith('sim-1', 'exec-1'));

    // Header (agent · privilege) + both tabs render; the Result tab shows the prevented/detected-by
    // rows, each carrying the expectation's own verdict and the platforms the run actually recorded
    // (detection by CrowdStrike here; prevention has no platform, so it says so instead of inventing
    // one).
    expect(await screen.findByText('agent-x · user')).toBeTruthy();
    expect(screen.getByRole('tab', { name: 'Result' })).toBeTruthy();
    expect(screen.getByRole('tab', { name: 'Terminal view' })).toBeTruthy();
    expect(screen.getByText('Prevented by')).toBeTruthy();
    expect(screen.getByText('Detected by')).toBeTruthy();
    expect(screen.getByText('CrowdStrike')).toBeTruthy();
    expect(screen.getByText('No platform attribution available')).toBeTruthy();

    // The Terminal tab shows the masked command and output via the shared Terminal.
    fireEvent.click(screen.getByRole('tab', { name: 'Terminal view' }));
    expect(await screen.findByText('$ nmap -p 445 host-x -u admin -p ••••')).toBeTruthy();
    expect(screen.getByText('open')).toBeTruthy();
  });

  it('says platform attribution needs Enterprise instead of implying nothing was detected', async () => {
    // Arrange: no Enterprise licence, so the backend resolves no platform at all. The panel must not
    // let that read as "no platform prevented or detected this".
    mocks.licenceValidated = false;
    setup();
    // Without a licence the backend resolves no platform at all — that is the shape to render.
    mocks.fetchExecutionDetail.mockResolvedValue({
      data: {
        payloadName: 'nmap',
        agentName: 'agent-x',
        agentPrivilege: 'user',
        endpointKey: 'host-x',
        preventionStatus: 'FAILED',
        detectionStatus: 'DETECTED',
        securityPlatforms: [],
        command: 'nmap -p 445 host-x',
        terminalOutput: 'open',
      },
    });
    await screen.findByTestId('attack-path-flow');
    await act(async () => {
      mocks.flowProps.current?.onEndpointClick?.(ENDPOINT_NODE, 'host-x', 'CORP-HOST');
    });
    fireEvent.click(await screen.findByText('nmap'));
    await waitFor(() => expect(mocks.fetchExecutionDetail).toHaveBeenCalled());

    // Assert: the Enterprise affordance for the section, and never the plain "none" wording.
    expect(await screen.findByText('Prevented by')).toBeTruthy();
    expect(screen.getByTestId('ee-chip')).toBeTruthy();
    expect(screen.getAllByText('Platform attribution requires Enterprise Edition').length).toBe(2);
    expect(screen.queryByText('No platform attribution available')).toBeNull();
    // The verdicts themselves still come from the run — the licence gates attribution, not results.
    expect(screen.getByText('DETECTED')).toBeTruthy();
  });

  it('opens the injector panel listing its own executions, one click from the execution detail', async () => {
    setup();
    await screen.findByTestId('attack-path-flow');

    // Click the injector (action) node on the map.
    await act(async () => {
      mocks.flowProps.current?.onInjectorSelect?.('NODE_INJECTOR|nmap', 'nmap');
    });

    // The injector panel lists this injector's own executions (scoped via the endpoint-relations edges),
    // under an "Executions" section — same component as the endpoint panel.
    expect(await screen.findByText('Executions (1)')).toBeTruthy();
    // Its Findings section shows only findings attributed to this injector's executions (exec-1), via the
    // category endpoint's executionIds — here the captured credential.
    expect(await screen.findByText('admin : ••••••')).toBeTruthy();

    // Clicking the listed execution opens its Result / Execution details / Remediation detail (fetched by
    // its raw ref), showing the global command it ran.
    const execRow = screen.getAllByRole('button').find(b => /nmap/i.test(b.textContent ?? '') && /button/i.test(b.getAttribute('role') ?? 'button'));
    fireEvent.click(execRow as HTMLElement);
    await waitFor(() => expect(mocks.fetchExecutionDetail).toHaveBeenCalledWith('sim-1', 'exec-1'));
  });

  it('switches to the table view, lists the exposed endpoint and exposes CSV export', async () => {
    setup();
    await screen.findByTestId('attack-path-flow');

    // Toggle to the table view; the graph is replaced by the sortable endpoint table.
    fireEvent.click(screen.getByRole('button', { name: 'Table' }));

    // The single exposed endpoint (score 1) is listed with its friendly hostname and total findings,
    // and the CSV export action is available.
    expect(await screen.findByText('CORP-HOST')).toBeTruthy();
    expect(screen.getByRole('button', { name: /Export CSV/ })).toBeTruthy();

    // Clicking the row returns to the graph and focuses that endpoint's path.
    fireEvent.click(screen.getByText('CORP-HOST'));
    await waitFor(() => expect(screen.getByTestId('attack-path-flow')).toBeTruthy());
    await waitFor(() => expect(mocks.flowProps.current?.fitRequest ?? 0).toBeGreaterThan(0));
  });
});

// The live path (issue 6647): one snapshot then a 3 s delta poll. Timers are faked so the cadence, the
// pause/stop rules and the degraded state are asserted deterministically instead of waited on.
describe('SimulationAttackPath live delta updates', () => {
  // A second endpoint reached by the same injector, as a delta would ship it.
  const HOST_Y = 'NODE_ENDPOINT|host-y';
  const deltaWithNewEndpoint = {
    sinceVersion: 0,
    newVersion: 7,
    attackPathNodes: [{
      id: HOST_Y,
      type: 'ASSET',
      label: 'CORP-OTHER',
      hostname: 'CORP-OTHER',
      ref: 'host-y',
      findingCounts: { credentials: 2 },
    }],
    attackPathEdges: [{
      edgeId: 'edge-nmap-host-y',
      edgeSourceId: 'NODE_INJECTOR|nmap',
      edgeTargetId: HOST_Y,
      type: 'EDGE_EXECUTIONS',
      count: 1,
    }],
    counters: {
      endpoints: 2,
      credentials: 3,
      users: 0,
      cves: 0,
      ports: 0,
    },
  };

  // Let the mount's snapshot read settle before touching timers.
  const mounted = async () => {
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
  };
  const tick = async (ms = 3000) => {
    await act(async () => {
      await vi.advanceTimersByTimeAsync(ms);
    });
  };

  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    cleanup();
    vi.clearAllMocks();
    mocks.flowProps.current = null;
  });

  it('polls the delta every 3 s from the current version and applies it in place', async () => {
    setup();
    mocks.fetchAttackPathGraphDelta.mockResolvedValue({ data: deltaWithNewEndpoint });
    await mounted();

    // Nothing is polled before the cadence elapses; the snapshot was the only read.
    expect(mocks.fetchAttackPathGraphDelta).not.toHaveBeenCalled();

    await tick();
    expect(mocks.fetchAttackPathGraphDelta).toHaveBeenCalledWith('sim-1', 0);

    // The next tick continues from the version the delta reported, not from zero again.
    await tick();
    expect(mocks.fetchAttackPathGraphDelta).toHaveBeenLastCalledWith('sim-1', 7);

    // The applied delta reached the view: the new endpoint is listed, and the graph was patched, not
    // re-read (a single snapshot call for the whole run).
    fireEvent.click(screen.getByRole('button', { name: 'Table' }));
    expect(screen.getByText('CORP-OTHER')).toBeTruthy();
    expect(mocks.fetchAttackPathGraph).toHaveBeenCalledTimes(1);
  });

  it('re-reads the snapshot when the backend cannot answer the cursor', async () => {
    setup();
    mocks.fetchAttackPathGraphDelta.mockResolvedValue({
      data: {
        sinceVersion: 0,
        newVersion: 12,
        resyncRequired: true,
      },
    });
    await mounted();

    await tick();
    // The graph the user is looking at is never unmounted for a resync they did not ask for: no
    // loader flash, no viewport reset — the reseed happens underneath it.
    expect(screen.getByTestId('attack-path-flow')).toBeTruthy();
    await mounted();

    // Resync is the full snapshot read, re-seeding the store from scratch.
    expect(mocks.fetchAttackPathGraph).toHaveBeenCalledTimes(2);
    expect(screen.getByTestId('attack-path-flow')).toBeTruthy();
  });

  it('preserves the open drawer, its search and the selection across a delta', async () => {
    setup();
    await mounted();

    // Open the credentials drawer and narrow it, then select an endpoint on the map.
    fireEvent.click(screen.getByRole('button', { name: /Credentials/ }));
    await mounted();
    const search = screen.getByPlaceholderText('Search');
    fireEvent.change(search, { target: { value: 'admin' } });
    await act(async () => {
      mocks.flowProps.current?.onEndpointClick?.(ENDPOINT_NODE, 'host-x', 'CORP-HOST');
    });

    // A live update lands.
    mocks.fetchAttackPathGraphDelta.mockResolvedValue({ data: deltaWithNewEndpoint });
    await tick();

    // Everything the user owns survived it: the drawer is still open on the same query, and the
    // endpoint stays selected with its feed loaded (this is a silent refresh, never a reload).
    expect(screen.getByTestId('shared-drawer')).toBeTruthy();
    expect((screen.getByPlaceholderText('Search') as HTMLInputElement).value).toBe('admin');
    expect(screen.getAllByText(/CORP-HOST/).length).toBeGreaterThan(0);
  });

  it('stops polling once the run is terminal and says the attack path is final', async () => {
    setup();
    // The run's status comes from the picker metadata, which resolves after the snapshot.
    mocks.fetchSimulationsMetaById.mockResolvedValue({
      data: [{
        exercise_id: 'sim-1',
        exercise_name: 'Run',
        exercise_status: 'FINISHED',
      }],
    });
    await mounted();
    await mounted();
    await mounted();

    // One last read to catch anything committed at the very end, then nothing more.
    expect(mocks.fetchAttackPathGraphDelta).toHaveBeenCalledTimes(1);
    await tick(30000);
    expect(mocks.fetchAttackPathGraphDelta).toHaveBeenCalledTimes(1);
    expect(screen.getByText('Run finished')).toBeTruthy();
  });

  it('pauses while the tab is hidden and catches up when it comes back', async () => {
    setup();
    await mounted();
    await tick();
    const polledWhileVisible = mocks.fetchAttackPathGraphDelta.mock.calls.length;

    // Hidden tab: the interval is cleared, so the cadence stops entirely.
    const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden');
    await act(async () => {
      document.dispatchEvent(new Event('visibilitychange'));
    });
    await tick(30000);
    expect(mocks.fetchAttackPathGraphDelta).toHaveBeenCalledTimes(polledWhileVisible);

    // Back in the foreground: one immediate catch-up read, then the cadence resumes.
    visibility.mockReturnValue('visible');
    await act(async () => {
      document.dispatchEvent(new Event('visibilitychange'));
    });
    expect(mocks.fetchAttackPathGraphDelta).toHaveBeenCalledTimes(polledWhileVisible + 1);
    await tick();
    expect(mocks.fetchAttackPathGraphDelta).toHaveBeenCalledTimes(polledWhileVisible + 2);
    visibility.mockRestore();
  });

  it('keeps the last good graph and reports reconnecting when a tick fails', async () => {
    setup();
    await mounted();
    expect(screen.getByText('Live')).toBeTruthy();

    mocks.fetchAttackPathGraphDelta.mockRejectedValue(new Error('network'));
    await tick();

    // The graph is untouched and the freshness chip says the updates are degraded, not that the data
    // is gone.
    expect(screen.getByText('Reconnecting…')).toBeTruthy();
    expect(screen.getByTestId('attack-path-flow')).toBeTruthy();
  });
});
