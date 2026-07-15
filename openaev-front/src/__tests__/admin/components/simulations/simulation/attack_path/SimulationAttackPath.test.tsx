import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { type ReactNode } from 'react';
import type * as ReactRouter from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import SimulationAttackPath from '../../../../../../admin/components/simulations/simulation/attack_path/SimulationAttackPath';

// Capture the props AttackPathFlow receives (mocked so ReactFlow is not instantiated), and stub the
// action layer + i18n + router so the test drives only the drawer and the cross-focus wiring.
const mocks = vi.hoisted(() => ({
  fetchAttackPathGraph: vi.fn(),
  fetchAttackPathSimulations: vi.fn(),
  fetchEndpointFindings: vi.fn(),
  fetchEndpointRelations: vi.fn(),
  fetchFindingsByCategory: vi.fn(),
  flowProps: { current: null as { focusRequest?: { nodeId: string } } | null },
}));

vi.mock('../../../../../../actions/attack-path/attack-path-actions', () => ({
  fetchAttackPathGraph: mocks.fetchAttackPathGraph,
  fetchAttackPathSimulations: mocks.fetchAttackPathSimulations,
  fetchEndpointFindings: mocks.fetchEndpointFindings,
  fetchEndpointRelations: mocks.fetchEndpointRelations,
  fetchFindingsByCategory: mocks.fetchFindingsByCategory,
}));

vi.mock('../../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (s: string) => s }) }));

vi.mock('../../../../../../admin/components/simulations/simulation/attack_path/AttackPathFlow', () => ({
  default: (props: { focusRequest?: { nodeId: string } }) => {
    mocks.flowProps.current = props;
    return <div data-testid="attack-path-flow" />;
  },
}));

vi.mock('react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof ReactRouter>();
  return {
    ...actual,
    useParams: () => ({ exerciseId: 'sim-1' }),
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
    id: ENDPOINT_NODE,
    type: 'ASSET',
    label: 'CORP-HOST',
    hostname: 'CORP-HOST',
    ref: 'host-x',
  }],
  attackPathEdges: [],
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
        edges: [],
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
    return render(<SimulationAttackPath />, { wrapper });
  };

  it('opens a category drawer, lists the fetched findings, and focuses the endpoint on item click', async () => {
    setup();

    // Widgets appear once the graph counters are loaded; open the credentials drawer.
    const credentialsWidget = await screen.findByRole('button', { name: /Credentials/ });
    fireEvent.click(credentialsWidget);
    expect(mocks.fetchFindingsByCategory).toHaveBeenCalledWith('sim-1', 'credentials');

    // The drawer renders the fetched finding (credential value masked by the server).
    const item = await screen.findByText('admin:••••');
    fireEvent.click(item);

    // Cross-focus: the endpoint's feed is loaded and the map receives a focus request on it.
    await waitFor(() => {
      expect(mocks.fetchEndpointRelations).toHaveBeenCalledWith('sim-1', 'host-x');
      expect(mocks.flowProps.current?.focusRequest?.nodeId).toBe(ENDPOINT_NODE);
    });

    // The feed panel is titled with the endpoint's friendly hostname, not the raw key.
    expect(await screen.findByText(/CORP-HOST/)).toBeTruthy();
  });
});
