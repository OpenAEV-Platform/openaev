import { beforeEach, describe, expect, it, vi } from 'vitest';

// The tenant prefix is applied centrally by Action.buildUri, so the actions call the
// plain /api/attack-path/... paths and we assert exactly those. Reads pass `defaultErrorBehavior`
// = false so the component owns error UX (no global toast that could leak backend detail).
const { simpleCall, simplePostCall } = vi.hoisted(() => ({
  simpleCall: vi.fn((_uri?: string) => Promise.resolve({ data: {} })),
  simplePostCall: vi.fn((_uri?: string) => Promise.resolve({ data: [] })),
}));
vi.mock('../../../utils/Action', () => ({
  simpleCall,
  simplePostCall,
}));

const importActions = async () => import('../../../actions/attack-path/attack-path-actions');

describe('attack path POC actions', () => {
  beforeEach(() => {
    simpleCall.mockClear();
    simplePostCall.mockClear();
  });

  it('fetchAttackPathGraph hits the graph endpoint without a mode by default', async () => {
    const { fetchAttackPathGraph } = await importActions();
    await fetchAttackPathGraph('SIM-1');
    expect(simpleCall).toHaveBeenCalledWith('/api/attack-path/simulations/SIM-1/graph', undefined, false);
  });

  it('fetchAttackPathGraph forwards the mode as a query parameter', async () => {
    const { fetchAttackPathGraph } = await importActions();
    await fetchAttackPathGraph('SIM-1', 'collapsed');
    expect(simpleCall).toHaveBeenCalledWith('/api/attack-path/simulations/SIM-1/graph?mode=collapsed', undefined, false);
  });

  it('fetchEndpointFindings encodes the endpoint ref', async () => {
    const { fetchEndpointFindings } = await importActions();
    await fetchEndpointFindings('SIM-1', 'asset/01');
    expect(simpleCall).toHaveBeenCalledWith('/api/attack-path/simulations/SIM-1/endpoint/findings?ref=asset%2F01', undefined, false);
  });

  it('fetchEndpointRelations encodes the endpoint ref', async () => {
    const { fetchEndpointRelations } = await importActions();
    await fetchEndpointRelations('SIM-1', '10.0.0.1');
    expect(simpleCall).toHaveBeenCalledWith('/api/attack-path/simulations/SIM-1/endpoint/relations?ref=10.0.0.1', undefined, false);
  });

  it('fetchAttackPathSimulations hits the simulations list endpoint', async () => {
    const { fetchAttackPathSimulations } = await importActions();
    await fetchAttackPathSimulations();
    expect(simpleCall).toHaveBeenCalledWith('/api/attack-path/simulations');
  });

  it('fetchSimulationsMetaById posts a GetExercisesInput body ({ exercise_ids })', async () => {
    const { fetchSimulationsMetaById } = await importActions();
    await fetchSimulationsMetaById(['SIM-1', 'SIM-2']);
    expect(simplePostCall).toHaveBeenCalledWith(
      '/api/exercises/search-by-id',
      { exercise_ids: ['SIM-1', 'SIM-2'] },
      undefined,
      false,
    );
  });

  it('fetchFindingsByCategory builds the paginated findings query with defaults', async () => {
    const { fetchFindingsByCategory } = await importActions();
    await fetchFindingsByCategory('SIM-1', 'credentials');
    expect(simpleCall).toHaveBeenCalledWith('/api/attack-path/simulations/SIM-1/findings?category=credentials&page=0&size=50');
  });

  it('fetchFindingsByCategory forwards the page and size and encodes the category', async () => {
    const { fetchFindingsByCategory } = await importActions();
    await fetchFindingsByCategory('SIM-1', 'cves', 2, 25);
    expect(simpleCall).toHaveBeenCalledWith('/api/attack-path/simulations/SIM-1/findings?category=cves&page=2&size=25');
  });

  it('fetchExecutionDetail hits the execution detail endpoint and encodes the id', async () => {
    const { fetchExecutionDetail } = await importActions();
    await fetchExecutionDetail('SIM-1', 'exec/1');
    expect(simpleCall).toHaveBeenCalledWith('/api/attack-path/simulations/SIM-1/executions/exec%2F1');
  });
});
