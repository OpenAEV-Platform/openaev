import { type QueryClient, QueryObserver } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { createAppQueryClient, shouldRetry } from '../../../utils/query/queryClient';

const notify = vi.hoisted(() => vi.fn());

vi.mock('../../../utils/error/errorHandlerUtil', () => ({ notifyErrorHandler: notify }));

// The shape network.ts rejects with when the server answered.
const answered = (status: number) => ({ status });

// network.ts rejects with false when no answer came back.
const NETWORK_ERROR = false;

describe('shouldRetry', () => {
  it.each([
    ['a network error', 0, NETWORK_ERROR, true],
    ['a 500', 0, answered(500), true],
    ['a 503', 2, answered(503), true],
    ['a 400', 0, answered(400), false],
    ['a 401', 0, answered(401), false],
    ['a 403', 0, answered(403), false],
    ['a 404', 0, answered(404), false],
    ['a network error after 3 failures', 3, NETWORK_ERROR, false],
    ['a 500 after 3 failures', 3, answered(500), false],
  ])('given_%s_failureCount_%s_should_retry_%s', (_label, failureCount, error, expected) => {
    // Act
    const retry = shouldRetry(failureCount, error);

    // Assert
    expect(retry).toBe(expected);
  });
});

describe('createAppQueryClient', () => {
  let client: QueryClient;

  beforeEach(() => {
    vi.useFakeTimers();
    notify.mockClear();
    client = createAppQueryClient();
  });

  afterEach(() => {
    client.clear();
    vi.useRealTimers();
  });

  describe('defaults', () => {
    it.each([
      [answered(500), 4],
      [answered(404), 1],
    ])('given_a_%o_should_fetch_%s_times', async (error, expected) => {
      // Arrange
      const queryFn = vi.fn(() => Promise.reject(error));

      // Act: past TanStack's exponential backoff (1 s, 2 s, 4 s)
      const fetch = client.fetchQuery({
        queryKey: ['k'],
        queryFn,
      }).catch(() => undefined);
      await vi.advanceTimersByTimeAsync(10_000);
      await fetch;

      // Assert
      expect(queryFn).toHaveBeenCalledTimes(expected);
    });

    it('given_aMountedQuery_should_neverPoll', async () => {
      // Arrange
      const queryFn = vi.fn(() => Promise.resolve('data'));
      const unsubscribe = new QueryObserver(client, {
        queryKey: ['k'],
        queryFn,
        meta: { live: {} },
      }).subscribe(() => {});

      // Act
      await vi.advanceTimersByTimeAsync(120_000);
      unsubscribe();

      // Assert
      expect(queryFn).toHaveBeenCalledTimes(1);
    });
  });

  describe('error notifications', () => {
    const fetchWith = (queryFn: () => Promise<unknown>) => client.fetchQuery({
      queryKey: ['k'],
      queryFn,
      staleTime: 0,
      retry: false,
    }).catch(() => undefined);

    const fail = (error: unknown) => fetchWith(() => Promise.reject(error));
    const succeed = () => fetchWith(() => Promise.resolve('data'));

    it('given_repeatedFailuresWithoutData_should_notifyOnce', async () => {
      // Act
      await fail(answered(500));
      await fail(answered(500));

      // Assert
      expect(notify).toHaveBeenCalledTimes(1);
      expect(notify).toHaveBeenCalledWith(answered(500));
    });

    it('given_repeatedFailedRefetchesOfShownData_should_notifyOnce', async () => {
      // Arrange
      await succeed();

      // Act
      await fail(NETWORK_ERROR);
      await fail(NETWORK_ERROR);

      // Assert
      expect(notify).toHaveBeenCalledTimes(1);
      expect(client.getQueryData(['k'])).toBe('data');
    });

    it('given_aFailureAfterASuccessThatEndedAStreak_should_notifyAgain', async () => {
      // Arrange
      await fail(answered(500));

      // Act
      await succeed();
      await fail(answered(500));

      // Assert
      expect(notify).toHaveBeenCalledTimes(2);
    });

    it.each([404, 401])('given_a%s_should_notNotify', async (status) => {
      // Act
      await fail(answered(status));

      // Assert
      expect(notify).not.toHaveBeenCalled();
    });

    it('given_twoQueriesFailing_should_notifyForEach', async () => {
      // Arrange
      await fail(answered(500));

      // Act
      await client.fetchQuery({
        queryKey: ['other'],
        queryFn: () => Promise.reject(answered(500)),
        retry: false,
      }).catch(() => undefined);

      // Assert
      expect(notify).toHaveBeenCalledTimes(2);
    });
  });
});
