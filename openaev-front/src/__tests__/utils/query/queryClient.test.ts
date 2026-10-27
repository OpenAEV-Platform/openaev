import { type Query } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { createAppQueryClient, shouldRetry, staleTimeOf } from '../../../utils/query/queryClient';

const healthy = vi.hoisted(() => ({ value: true }));
const started = vi.hoisted(() => ({ value: true }));

vi.mock('../../../utils/stream/streamClient', () => ({
  isStreamHealthy: () => healthy.value,
  isStreamStarted: () => started.value,
}));

const notify = vi.hoisted(() => vi.fn());

vi.mock('../../../utils/error/errorHandlerUtil', () => ({ notifyErrorHandler: notify }));

const queryWith = (meta?: object) => ({ meta } as unknown as Query);

// The shape network.ts rejects with when the server answered.
const axiosError = (status: number) => ({ status });

describe('createAppQueryClient', () => {
  beforeEach(() => {
    healthy.value = true;
    started.value = true;
  });

  it('given_aNewClient_should_applyTheDefaults', () => {
    // Act
    const queries = createAppQueryClient().getDefaultOptions().queries;

    // Assert
    expect(queries?.refetchOnWindowFocus).toBe(true);
    expect(queries?.staleTime).toBe(staleTimeOf);
  });

  const intervalOf = (query: Query) => {
    const { refetchInterval } = createAppQueryClient().getDefaultOptions().queries!;
    return (refetchInterval as (query: Query) => number | false)(query);
  };

  it('given_aHealthyStream_should_notPoll', () => {
    // Act
    const interval = intervalOf(queryWith({ live: {} }));

    // Assert
    expect(interval).toBe(false);
  });

  it('given_anUnhealthyStream_should_pollTheLiveQueriesEvery30To40Seconds', () => {
    // Arrange
    healthy.value = false;

    // Act
    const interval = intervalOf(queryWith({ live: {} }));

    // Assert
    expect(interval).toBeGreaterThanOrEqual(30_000);
    expect(interval).toBeLessThan(40_000);
  });

  it('given_anUnhealthyStream_should_notPollAQueryTheStreamDoesNotRefresh', () => {
    // Arrange
    healthy.value = false;

    // Act
    const interval = intervalOf(queryWith());

    // Assert
    expect(interval).toBe(false);
  });

  it('given_theDefaults_should_useTheRetryPolicy', () => {
    expect(createAppQueryClient().getDefaultOptions().queries?.retry).toBe(shouldRetry);
  });
});

describe('shouldRetry', () => {
  beforeEach(() => {
    healthy.value = true;
    started.value = true;
  });

  it.each([400, 401, 403, 404, 422])('given_a%s_should_neverRetry', (status) => {
    expect(shouldRetry(0, axiosError(status))).toBe(false);
  });

  it.each([500, 503])('given_a%s_should_neverRetry', (status) => {
    expect(shouldRetry(0, axiosError(status))).toBe(false);
  });

  it('given_aNetworkError_should_retryTwiceAtMost', () => {
    // network.ts rejects with false when no answer came back.
    expect(shouldRetry(1, false)).toBe(true);
    expect(shouldRetry(2, false)).toBe(false);
  });

  it('given_aNetworkErrorWhileTheStreamIsDown_should_leaveItToThePolling', () => {
    healthy.value = false;
    expect(shouldRetry(0, false)).toBe(false);
  });

  it('given_aNetworkErrorOnAPageWithoutStream_should_retry', () => {
    healthy.value = false;
    started.value = false;
    expect(shouldRetry(0, false)).toBe(true);
  });
});

describe('staleTimeOf', () => {
  beforeEach(() => {
    healthy.value = true;
  });

  it('given_aLiveQueryAndAHealthyStream_should_neverGoStaleByTime', () => {
    expect(staleTimeOf(queryWith({ live: {} }))).toBe(Infinity);
  });

  it('given_aLiveQueryAndAStreamDown_should_ageAfter30Seconds', () => {
    healthy.value = false;
    expect(staleTimeOf(queryWith({ live: {} }))).toBe(30_000);
  });

  it('given_aPlainQuery_should_ageAfter30Seconds', () => {
    expect(staleTimeOf(queryWith())).toBe(30_000);
  });
});

describe('query error notifications', () => {
  beforeEach(() => {
    healthy.value = false;
    notify.mockClear();
  });

  const failingFetch = async (client: ReturnType<typeof createAppQueryClient>, error: unknown, key = ['k']) => {
    await client.fetchQuery({
      queryKey: key,
      queryFn: () => Promise.reject(error),
      retry: false,
    }).catch(() => undefined);
  };

  it('given_repeatedFailuresWithoutData_should_notifyOnce', async () => {
    // Arrange
    const client = createAppQueryClient();

    // Act
    await failingFetch(client, { status: 500 });
    await failingFetch(client, { status: 500 });

    // Assert
    expect(notify).toHaveBeenCalledTimes(1);
  });

  it('given_aFailedRefetchOfShownData_should_notNotify', async () => {
    // Arrange
    const client = createAppQueryClient();
    await client.fetchQuery({
      queryKey: ['k'],
      queryFn: () => Promise.resolve('data'),
    });

    // Act
    await client.fetchQuery({
      queryKey: ['k'],
      // network.ts rejects with false when no answer came back.
      // eslint-disable-next-line prefer-promise-reject-errors
      queryFn: () => Promise.reject(false),
      staleTime: 0,
      retry: false,
    }).catch(() => undefined);

    // Assert
    expect(notify).not.toHaveBeenCalled();
  });

  it('given_a404_should_notNotify', async () => {
    // Arrange
    const client = createAppQueryClient();

    // Act
    await failingFetch(client, { status: 404 });

    // Assert
    expect(notify).not.toHaveBeenCalled();
  });
});
