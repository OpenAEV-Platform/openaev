import { createTheme, ThemeProvider } from '@mui/material/styles';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { type ReactNode } from 'react';
import type * as ReactRouter from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

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
  fetchAttackPathSimulations: vi.fn(),
  fetchSimulationsMetaById: vi.fn(),
  fetchEndpointFindings: vi.fn(),
  fetchEndpointRelations: vi.fn(),
  fetchFindingsByCategory: vi.fn(),
  fetchExecutionDetail: vi.fn(),
  flowProps: { current: null as FlowProps | null },
}));

vi.mock('../../../../../../actions/attack-path/attack-path-actions', () => ({
  fetchAttackPathGraph: mocks.fetchAttackPathGraph,
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

const wrapper = ({ children }: { children: ReactNode }) => (
  <ThemeProvider theme={createTheme()}>{children}</ThemeProvider>
);

describe('SimulationAttackPath findings drawer + cross-focus', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    mocks.flowProps.current = null;
  });

  const setup = () => {
    mocks.fetchAttackPathGraph.mockResolvedValue({ data: graphDto });
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
    // security platforms (detection succeeded in the mock, so CrowdStrike & Splunk appear).
    expect(await screen.findByText('agent-x · user')).toBeTruthy();
    expect(screen.getByRole('tab', { name: 'Result' })).toBeTruthy();
    expect(screen.getByRole('tab', { name: 'Terminal view' })).toBeTruthy();
    expect(screen.getByText('Prevented by')).toBeTruthy();
    expect(screen.getByText('Detected by')).toBeTruthy();
    expect(screen.getByText('CrowdStrike')).toBeTruthy();

    // The Terminal tab shows the masked command and output via the shared Terminal.
    fireEvent.click(screen.getByRole('tab', { name: 'Terminal view' }));
    expect(await screen.findByText('$ nmap -p 445 host-x -u admin -p ••••')).toBeTruthy();
    expect(screen.getByText('open')).toBeTruthy();
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
