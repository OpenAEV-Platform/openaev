import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { fetchXtmConnections, lookupCounterparts } from '../../../actions/xtm/counterpart-actions';
import { getXtmConnections, resetCounterpartCache, resolveCounterparts, toAnswer } from '../../../components/xtm/counterpartStore';

vi.mock('../../../actions/xtm/counterpart-actions', () => ({
  fetchXtmConnections: vi.fn(),
  lookupCounterparts: vi.fn(),
}));

const connected = [{
  xtm_connection_product: 'opencti',
  xtm_connection_connected: true,
  xtm_connection_url: 'https://octi.example',
}];

describe('counterpartStore', () => {
  beforeEach(() => {
    resetCounterpartCache();
    vi.mocked(lookupCounterparts).mockReset();
    vi.mocked(fetchXtmConnections).mockReset();
  });
  afterEach(() => vi.useRealTimers());

  it('given keys asked in the same tick should send one batch and cache the matches', async () => {
    // Arrange
    vi.mocked(lookupCounterparts).mockResolvedValue([
      {
        counterpart_key: 'host-1',
        counterpart_product: 'opencti',
        counterpart_status: 'match',
        counterpart_url: 'https://octi.example/dashboard/id/x',
      },
      {
        counterpart_key: 'host-2',
        counterpart_product: 'opencti',
        counterpart_status: 'no_match',
      },
    ]);

    // Act
    const [first, second] = await Promise.all([
      resolveCounterparts('assets', 'host-1'),
      resolveCounterparts('assets', 'host-2'),
    ]);
    const again = await resolveCounterparts('assets', 'host-1');

    // Assert
    expect(lookupCounterparts).toHaveBeenCalledTimes(1);
    expect(lookupCounterparts).toHaveBeenCalledWith('assets', ['host-1', 'host-2']);
    expect(first[0].counterpart_status).toBe('match');
    expect(second[0].counterpart_status).toBe('no_match');
    expect(again[0].counterpart_status).toBe('match');
  });

  it('given an unavailable product should not cache the answer', async () => {
    // Arrange
    vi.mocked(lookupCounterparts).mockResolvedValue([{
      counterpart_key: 'T1059',
      counterpart_product: 'opencti',
      counterpart_status: 'unavailable',
    }]);

    // Act
    await resolveCounterparts('attack-patterns', 'T1059');
    await resolveCounterparts('attack-patterns', 'T1059');

    // Assert
    expect(lookupCounterparts).toHaveBeenCalledTimes(2);
  });

  it('given a failed lookup should resolve empty, never throw', async () => {
    // Arrange
    vi.mocked(lookupCounterparts).mockRejectedValue(new Error('down'));

    // Act
    const answers = await resolveCounterparts('vulnerabilities', 'CVE-2024-1234');

    // Assert
    expect(answers).toEqual([]);
  });

  it('given a failed connection read should answer no connection and retry next time', async () => {
    // Arrange
    vi.mocked(fetchXtmConnections).mockRejectedValueOnce(new Error('down')).mockResolvedValueOnce(connected);

    // Act
    const first = await getXtmConnections();
    const second = await getXtmConnections();

    // Assert
    expect(first).toEqual([]);
    expect(second).toEqual(connected);
  });

  describe('toAnswer', () => {
    it.each([
      ['match', 'match'],
      ['no_match', 'no_match'],
      ['unavailable', 'unavailable'],
      ['not_connected', 'not_connected'],
    ] as const)('given status %s should answer %s', (status, state) => {
      const answer = toAnswer('opencti', connected, [{
        counterpart_key: 'k',
        counterpart_product: 'opencti',
        counterpart_status: status,
      }]);
      expect(answer.state).toBe(state);
    });

    it('given a product not connected should answer not_connected whatever the lookup', () => {
      expect(toAnswer('opencti', [], null).state).toBe('not_connected');
    });

    it('given a connected product and no answer yet should answer loading', () => {
      expect(toAnswer('opencti', connected, null).state).toBe('loading');
    });
  });
});
