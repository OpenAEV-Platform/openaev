import { beforeEach, describe, expect, it, vi } from 'vitest';

// The tenant prefix is applied centrally by Action.buildUri, so the actions call the
// plain /api/poc/attack-path/... paths and we assert exactly those.
const { simpleCall } = vi.hoisted(() => ({ simpleCall: vi.fn((_uri?: string) => Promise.resolve({ data: {} })) }));
vi.mock('../../../utils/Action', () => ({ simpleCall }));

const importActions = async () => import('../../../actions/attack-path-poc/attack-path-poc-actions');

describe('attack path POC actions', () => {
  beforeEach(() => {
    simpleCall.mockClear();
  });

  it('fetchAttackPathGraph hits the graph endpoint without a mode by default', async () => {
    const { fetchAttackPathGraph } = await importActions();
    await fetchAttackPathGraph('SIM-1');
    expect(simpleCall).toHaveBeenCalledWith('/api/poc/attack-path/simulations/SIM-1/graph');
  });

  it('fetchAttackPathGraph forwards the mode as a query parameter', async () => {
    const { fetchAttackPathGraph } = await importActions();
    await fetchAttackPathGraph('SIM-1', 'collapsed');
    expect(simpleCall).toHaveBeenCalledWith('/api/poc/attack-path/simulations/SIM-1/graph?mode=collapsed');
  });

  it('fetchEndpointFindings encodes the endpoint ref', async () => {
    const { fetchEndpointFindings } = await importActions();
    await fetchEndpointFindings('SIM-1', 'asset/01');
    expect(simpleCall).toHaveBeenCalledWith('/api/poc/attack-path/simulations/SIM-1/endpoint/findings?ref=asset%2F01');
  });

  it('fetchEndpointRelations encodes the endpoint ref', async () => {
    const { fetchEndpointRelations } = await importActions();
    await fetchEndpointRelations('SIM-1', '10.0.0.1');
    expect(simpleCall).toHaveBeenCalledWith('/api/poc/attack-path/simulations/SIM-1/endpoint/relations?ref=10.0.0.1');
  });

  it('fetchAttackPathSimulations hits the simulations list endpoint', async () => {
    const { fetchAttackPathSimulations } = await importActions();
    await fetchAttackPathSimulations();
    expect(simpleCall).toHaveBeenCalledWith('/api/poc/attack-path/simulations');
  });
});
