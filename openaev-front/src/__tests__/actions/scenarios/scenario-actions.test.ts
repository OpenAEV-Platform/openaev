import { beforeEach, describe, expect, it, vi } from 'vitest';

const { simplePostCall } = vi.hoisted(() => ({ simplePostCall: vi.fn((_uri?: string, _data?: unknown) => Promise.resolve({ data: { simulation_id: 'simulation-id' } })) }));
vi.mock('../../../utils/Action', async importOriginal => ({
  ...(await importOriginal<Record<string, unknown>>()),
  simplePostCall,
}));

const { launchScenario } = await import('../../../actions/scenarios/scenario-actions');

describe('launchScenario', () => {
  beforeEach(() => {
    simplePostCall.mockClear();
  });

  it('posts the targets to the scenario launch route and answers the simulation started', async () => {
    // Arrange
    const targets = { asset_ids: ['asset-id'] };

    // Act
    const output = await launchScenario('scenario-id', targets);

    // Assert
    expect(simplePostCall).toHaveBeenCalledWith('/api/scenarios/scenario-id/exercise/running', targets);
    expect(output).toEqual({ simulation_id: 'simulation-id' });
  });

  it('posts no body when the launch has no target', async () => {
    // Act
    await launchScenario('scenario-id');

    // Assert
    expect(simplePostCall).toHaveBeenCalledWith('/api/scenarios/scenario-id/exercise/running', undefined);
  });
});
