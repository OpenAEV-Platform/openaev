import { renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { getXtmConnections, resolveCounterparts } from '../../../components/xtm/counterpartStore';
import useCounterpartMatches, { type CounterpartRef } from '../../../components/xtm/useCounterpartMatches';

vi.mock('../../../components/xtm/counterpartStore', () => ({
  getXtmConnections: vi.fn(),
  resolveCounterparts: vi.fn(),
}));

const connected = (value: boolean) => [{
  xtm_connection_product: 'opencti',
  xtm_connection_connected: value,
  xtm_connection_url: 'https://octi.example',
}];

const REFS: CounterpartRef[] = [
  {
    kind: 'vulnerabilities',
    key: 'CVE-2024-0001',
  },
  {
    kind: 'vulnerabilities',
    key: 'CVE-2024-0002',
  },
];

describe('useCounterpartMatches', () => {
  beforeEach(() => {
    vi.mocked(getXtmConnections).mockReset();
    vi.mocked(resolveCounterparts).mockReset();
  });

  it('given OpenCTI connected should mark only the objects with a match', async () => {
    vi.mocked(getXtmConnections).mockResolvedValue(connected(true) as never);
    vi.mocked(resolveCounterparts).mockImplementation((_kind, key) => Promise.resolve([{
      counterpart_key: key,
      counterpart_product: 'opencti',
      counterpart_status: key === 'CVE-2024-0001' ? 'match' : 'no_match',
    }] as never));
    const { result } = renderHook(() => useCounterpartMatches(REFS));
    await waitFor(() => expect(result.current.size).toBe(1));
    expect(result.current.has('vulnerabilities|CVE-2024-0001')).toBe(true);
    expect(result.current.has('vulnerabilities|CVE-2024-0002')).toBe(false);
    expect(resolveCounterparts).toHaveBeenCalledTimes(2);
  });

  it('given OpenCTI not connected should look nothing up and mark nothing', async () => {
    vi.mocked(getXtmConnections).mockResolvedValue(connected(false) as never);
    const { result } = renderHook(() => useCounterpartMatches(REFS));
    await waitFor(() => expect(getXtmConnections).toHaveBeenCalled());
    expect(resolveCounterparts).not.toHaveBeenCalled();
    expect(result.current.size).toBe(0);
  });

  it('given an unavailable OpenCTI should mark nothing', async () => {
    vi.mocked(getXtmConnections).mockResolvedValue(connected(true) as never);
    vi.mocked(resolveCounterparts).mockResolvedValue([] as never);
    const { result } = renderHook(() => useCounterpartMatches(REFS));
    await waitFor(() => expect(resolveCounterparts).toHaveBeenCalledTimes(2));
    expect(result.current.size).toBe(0);
  });

  it('given no object to look up should not read the connections', () => {
    const { result } = renderHook(() => useCounterpartMatches([]));
    expect(getXtmConnections).not.toHaveBeenCalled();
    expect(result.current.size).toBe(0);
  });
});
