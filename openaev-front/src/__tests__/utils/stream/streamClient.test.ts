import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { DATA_DELETE_SUCCESS, DATA_FETCH_SUCCESS, DATA_UPDATE_SUCCESS } from '../../../constants/ActionTypes';
import { type EntityChange } from '../../../utils/stream/entityChange';
import type * as StreamClientModule from '../../../utils/stream/streamClient';
import FakeEventSource from './fakeEventSource';

vi.mock('../../../utils/Action', () => ({
  buildUri: (path: string) => path,
  simpleCall: vi.fn(),
}));

type StreamClient = typeof StreamClientModule;

const message = (overrides: Record<string, unknown> = {}) => ({
  event_type: DATA_UPDATE_SUCCESS,
  attribute_id: 'tag_id',
  attribute_schema: 'tags',
  instance: {
    tag_id: 't1',
    tag_name: 'secret',
  },
  listened: true,
  ...overrides,
});

const bulkOperation = (overrides: Record<string, unknown> = {}) => ({
  bulk_operation_id: 'b1',
  bulk_operation_action: 'DELETE',
  bulk_operation_entity: 'injects',
  bulk_operation_total: 10,
  bulk_operation_processed: 10,
  bulk_operation_status: 'COMPLETED',
  bulk_operation_started_at: '2026-01-01T00:00:00Z',
  ...overrides,
});

describe('streamClient', () => {
  let client: StreamClient;

  const setVisibility = (state: 'visible' | 'hidden') => {
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      value: state,
    });
  };

  beforeEach(async () => {
    vi.useFakeTimers();
    FakeEventSource.reset();
    setVisibility('visible');
    vi.stubGlobal('EventSource', FakeEventSource);
    // No jitter: the reconnection delays below are the nominal ones.
    vi.spyOn(Math, 'random').mockReturnValue(0.5);
    vi.resetModules();
    client = await import('../../../utils/stream/streamClient');
  });

  afterEach(() => {
    vi.restoreAllMocks();
    client.stopStream();
    vi.useRealTimers();
    vi.unstubAllGlobals();
    setVisibility('visible');
  });

  describe('lifecycle', () => {
    it('given_severalStarts_should_openASingleConnection', () => {
      // Arrange / Act
      client.startStream();
      client.startStream();

      // Assert
      expect(FakeEventSource.instances).toHaveLength(1);
      expect(FakeEventSource.last.url).toBe('/api/stream');
    });

    it('given_aStartedStream_should_closeTheConnectionOnStopAndStayIdempotent', () => {
      // Arrange
      client.startStream();
      const source = FakeEventSource.last;

      // Act
      client.stopStream();
      client.stopStream();

      // Assert
      expect(source.closed).toBe(true);
    });

    it('given_aStopThenAStart_should_openANewConnection', () => {
      // Arrange
      client.startStream();

      // Act
      client.stopStream();
      client.startStream();

      // Assert
      expect(FakeEventSource.instances).toHaveLength(2);
      expect(FakeEventSource.instances[0].closed).toBe(true);
      expect(FakeEventSource.instances[1].closed).toBe(false);
    });

    it('given_aStopAfterAnError_should_notReconnect', () => {
      // Arrange
      client.startStream();
      FakeEventSource.last.onerror?.();

      // Act
      client.stopStream();
      vi.advanceTimersByTime(60_000);

      // Assert
      expect(FakeEventSource.instances).toHaveLength(1);
    });

    it('given_noEventSource_should_doNothing', () => {
      // Arrange
      vi.stubGlobal('EventSource', undefined);

      // Act
      const start = () => client.startStream();

      // Assert
      expect(start).not.toThrow();
      expect(client.isStreamHealthy()).toBe(false);
    });
  });

  describe('reconnection', () => {
    it('given_anErrorWithRecentPings_should_reconnectAfterTwoSeconds', () => {
      // Arrange
      client.startStream();

      // Act
      FakeEventSource.last.onerror?.();

      // Assert
      expect(FakeEventSource.instances[0].closed).toBe(true);
      vi.advanceTimersByTime(1999);
      expect(FakeEventSource.instances).toHaveLength(1);
      vi.advanceTimersByTime(1);
      expect(FakeEventSource.instances).toHaveLength(2);
    });

    it('given_noPingOnAVisibleTab_should_reconnectWithTheBackoff', () => {
      // Arrange
      client.startStream();

      // Act
      for (let i = 0; i < 4; i += 1) {
        vi.advanceTimersByTime(1500);
      }

      // Assert
      expect(FakeEventSource.instances[0].closed).toBe(true);
      expect(FakeEventSource.instances).toHaveLength(1);
      vi.advanceTimersByTime(2000);
      expect(FakeEventSource.instances).toHaveLength(2);
    });

    it('given_aHiddenTab_should_notReconnectOnASilentConnection', () => {
      // Arrange
      client.startStream();
      setVisibility('hidden');

      // Act
      vi.advanceTimersByTime(60_000);

      // Assert
      expect(FakeEventSource.instances).toHaveLength(1);
      expect(FakeEventSource.instances[0].closed).toBe(false);
    });

    it('given_aTabBecomingVisibleAgain_should_resetThePingClock', () => {
      // Arrange: the tab stayed hidden for a long time without any ping
      client.startStream();
      setVisibility('hidden');
      vi.advanceTimersByTime(60_000);

      // Act
      setVisibility('visible');
      document.dispatchEvent(new Event('visibilitychange'));
      vi.advanceTimersByTime(4500);

      // Assert: no reconnection right after the refocus, the clock restarted at the refocus
      expect(FakeEventSource.instances).toHaveLength(1);
      expect(FakeEventSource.instances[0].closed).toBe(false);
      expect(client.isStreamHealthy()).toBe(true);
    });

    it('given_aJitterOfZero_should_reconnectAtSeventyPercentOfTheDelay', () => {
      // Arrange
      vi.mocked(Math.random).mockReturnValue(0);
      client.startStream();

      // Act
      FakeEventSource.last.onerror?.();

      // Assert
      vi.advanceTimersByTime(1399);
      expect(FakeEventSource.instances).toHaveLength(1);
      vi.advanceTimersByTime(1);
      expect(FakeEventSource.instances).toHaveLength(2);
    });

    it('given_noEventSource_should_notMarkTheStreamStarted', () => {
      // Arrange
      vi.unstubAllGlobals();
      vi.stubGlobal('EventSource', undefined);

      // Act
      client.startStream();

      // Assert
      expect(client.isStreamStarted()).toBe(false);
    });

    it('given_pingsKeepingArriving_should_stayConnected', () => {
      // Arrange
      client.startStream();

      // Act
      for (let i = 0; i < 20; i += 1) {
        FakeEventSource.last.emit('ping');
        vi.advanceTimersByTime(1500);
      }

      // Assert
      expect(FakeEventSource.instances).toHaveLength(1);
    });

    it('given_repeatedErrors_should_reportTheOutageOnceAndStayUnhealthyUntilTheNextOpen', () => {
      // Arrange
      const lost = vi.fn();
      client.onConnectionLost(lost);
      client.startStream();
      FakeEventSource.last.emit('open');

      // Act
      FakeEventSource.last.onerror?.();

      // Assert
      expect(client.isStreamHealthy()).toBe(false);
      vi.advanceTimersByTime(2000);
      FakeEventSource.last.onerror?.();
      expect(lost).toHaveBeenCalledTimes(1);
      vi.advanceTimersByTime(2000);
      FakeEventSource.last.emit('open');
      expect(client.isStreamHealthy()).toBe(true);
      FakeEventSource.last.onerror?.();
      expect(lost).toHaveBeenCalledTimes(2);
    });

    it('given_anOutageOverThirtySeconds_should_backOffToTenSeconds', () => {
      // Arrange
      client.startStream();
      FakeEventSource.last.emit('open');
      FakeEventSource.last.onerror?.();
      vi.advanceTimersByTime(2000);
      expect(FakeEventSource.instances).toHaveLength(2);

      // Act
      vi.setSystemTime(Date.now() + 31_000);
      FakeEventSource.last.onerror?.();

      // Assert
      vi.advanceTimersByTime(9999);
      expect(FakeEventSource.instances).toHaveLength(2);
      vi.advanceTimersByTime(1);
      expect(FakeEventSource.instances).toHaveLength(3);
    });

    it('given_aDeviceSleepThenABrokenConnection_should_reconnectWithinTwoSeconds', () => {
      // Arrange
      client.startStream();
      FakeEventSource.last.emit('open');

      // Act: the device sleeps for an hour (no timer runs), then the connection turns out broken on wake
      vi.setSystemTime(Date.now() + 3_600_000);
      FakeEventSource.last.onerror?.();

      // Assert: a sleep is not an outage
      vi.advanceTimersByTime(1999);
      expect(FakeEventSource.instances).toHaveLength(1);
      vi.advanceTimersByTime(1);
      expect(FakeEventSource.instances).toHaveLength(2);
    });

    it('given_theLastPing_should_reportTheStreamHealth', () => {
      // Arrange
      client.startStream();

      // Act / Assert
      expect(client.isStreamHealthy()).toBe(true);
      vi.advanceTimersByTime(5000);
      expect(client.isStreamHealthy()).toBe(false);
      FakeEventSource.last.emit('ping');
      expect(client.isStreamHealthy()).toBe(true);
    });
  });

  describe('onConnectionOpen', () => {
    it('given_severalOpens_should_flagOnlyTheFirstOneOfASession', () => {
      // Arrange
      const opens: boolean[] = [];
      client.onConnectionOpen(({ first }) => opens.push(first));
      client.startStream();

      // Act
      FakeEventSource.last.emit('open');
      FakeEventSource.last.onerror?.();
      vi.advanceTimersByTime(2000);
      FakeEventSource.last.emit('open');
      FakeEventSource.last.emit('open');

      // Assert
      expect(opens).toEqual([true, false, false]);
    });

    it('given_aStopAndStart_should_flagTheFirstOpenAgain', () => {
      // Arrange
      const opens: boolean[] = [];
      client.onConnectionOpen(({ first }) => opens.push(first));
      client.startStream();
      FakeEventSource.last.emit('open');

      // Act
      client.stopStream();
      client.startStream();
      FakeEventSource.last.emit('open');

      // Assert
      expect(opens).toEqual([true, true]);
    });

    it('given_aStartAndAStop_should_tellWhetherTheStreamOpened', () => {
      // Arrange
      client.startStream();

      // Act / Assert
      expect(client.hasStreamOpened()).toBe(false);
      FakeEventSource.last.emit('open');
      expect(client.hasStreamOpened()).toBe(true);
      client.stopStream();
      expect(client.hasStreamOpened()).toBe(false);
    });
  });

  describe('onEntityChange', () => {
    const collect = () => {
      const changes: EntityChange[] = [];
      client.onEntityChange(change => changes.push(change));
      client.startStream();
      return changes;
    };

    it('given_eachEventType_should_mapItToAnAction', () => {
      // Arrange
      const changes = collect();
      const source = FakeEventSource.last;

      // Act
      source.emit('message', message({ event_type: DATA_FETCH_SUCCESS }));
      source.emit('message', message({ event_type: DATA_UPDATE_SUCCESS }));
      source.emit('message', message({ event_type: DATA_DELETE_SUCCESS }));
      source.emit('message', message({ event_type: 'SOMETHING_ELSE' }));

      // Assert
      expect(changes.map(change => change.action)).toEqual(['create', 'update', 'delete', 'update']);
      expect(changes[0]).toEqual({
        type: 'tags',
        id: 't1',
        action: 'create',
      });
    });

    it('given_anAgentWithAnAsset_should_resolveTheAssetRoot', () => {
      // Arrange
      const changes = collect();

      // Act
      FakeEventSource.last.emit('message', message({
        attribute_id: 'agent_id',
        attribute_schema: 'agents',
        instance: {
          agent_id: 'a1',
          agent_asset: 'asset1',
        },
      }));

      // Assert
      expect(changes).toEqual([{
        type: 'agents',
        id: 'a1',
        action: 'update',
        root: {
          type: 'assets',
          id: 'asset1',
        },
      }]);
    });

    it('given_aSchemaWithoutRootOrAnAgentWithoutAsset_should_leaveTheRootOut', () => {
      // Arrange
      const changes = collect();

      // Act
      FakeEventSource.last.emit('message', message());
      FakeEventSource.last.emit('message', message({
        attribute_id: 'agent_id',
        attribute_schema: 'agents',
        instance: { agent_id: 'a1' },
      }));

      // Assert
      expect(changes.every(change => !('root' in change))).toBe(true);
    });

    it('given_aSensitiveInstance_should_neverExposeIt', () => {
      // Arrange
      const changes = collect();

      // Act
      FakeEventSource.last.emit('message', message({
        attribute_id: 'agent_id',
        attribute_schema: 'agents',
        instance: {
          agent_id: 'a1',
          agent_asset: 'asset1',
          agent_secret: 'secret',
        },
      }));
      FakeEventSource.last.emit('message', message());

      // Assert
      expect(JSON.stringify(changes)).not.toContain('secret');
      changes.forEach(change => expect(Object.keys(change).sort()).not.toContain('instance'));
    });

    it('given_eventsNotListenedMalformedOrUnparsable_should_skipThem', () => {
      // Arrange
      const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
      const changes = collect();
      const source = FakeEventSource.last;

      // Act
      source.emit('message', message({ listened: false }));
      source.emit('message', message({ attribute_schema: undefined }));
      source.emit('message', message({ attribute_id: undefined }));
      source.emit('message', message({ instance: {} }));
      source.emitRaw('message', 'not json');

      // Assert
      expect(changes).toEqual([]);
      source.emit('message', message());
      expect(changes).toHaveLength(1);
      warn.mockRestore();
    });

    it('given_aStopAndStart_should_keepTheSubscription', () => {
      // Arrange
      const changes = collect();

      // Act
      client.stopStream();
      client.startStream();
      FakeEventSource.last.emit('message', message());

      // Assert
      expect(changes).toHaveLength(1);
    });

    it('given_anUnsubscribedHandler_should_stopDelivering', () => {
      // Arrange
      const handler = vi.fn();
      const unsubscribe = client.onEntityChange(handler);
      client.startStream();

      // Act
      unsubscribe();
      FakeEventSource.last.emit('message', message());

      // Assert
      expect(handler).not.toHaveBeenCalled();
    });
  });

  describe('onStreamMessage', () => {
    it('given_listenedEvents_should_deliverTheRawPayloadWithTheInstance', () => {
      // Arrange
      const handler = vi.fn();
      client.onStreamMessage(handler);
      client.startStream();

      // Act
      FakeEventSource.last.emit('message', message());
      FakeEventSource.last.emit('message', message({ listened: false }));

      // Assert
      expect(handler).toHaveBeenCalledTimes(1);
      expect(handler.mock.calls[0][0].instance).toEqual({
        tag_id: 't1',
        tag_name: 'secret',
      });
    });
  });

  describe('onBulkOperationEnd', () => {
    it('given_anOperationReachingATerminalState_should_emitTheEntityOnce', () => {
      // Arrange
      const handler = vi.fn();
      client.onBulkOperationEnd(handler);
      client.startStream();

      // Act
      FakeEventSource.last.emit('bulk-operation', bulkOperation({
        bulk_operation_status: 'RUNNING',
        bulk_operation_processed: 3,
      }));

      // Assert
      expect(handler).not.toHaveBeenCalled();
      FakeEventSource.last.emit('bulk-operation', bulkOperation());
      FakeEventSource.last.emit('bulk-operation', bulkOperation());
      expect(handler).toHaveBeenCalledTimes(1);
      expect(handler).toHaveBeenCalledWith('injects');
    });

    it('given_malformedPayloads_should_ignoreThem', () => {
      // Arrange
      const handler = vi.fn();
      client.onBulkOperationEnd(handler);
      client.startStream();

      // Act
      FakeEventSource.last.emitRaw('bulk-operation', 'not json');
      FakeEventSource.last.emit('bulk-operation', { bulk_operation_status: 'COMPLETED' });

      // Assert
      expect(handler).not.toHaveBeenCalled();
    });
  });

  describe('subscribeStreamEvent', () => {
    it('given_severalSubscribersOfAType_should_attachOneDispatcherAndReattachItOnReconnection', () => {
      // Arrange
      const first = vi.fn();
      const second = vi.fn();
      client.subscribeStreamEvent('attack-path-version', first);
      client.startStream();
      client.subscribeStreamEvent('attack-path-version', second);

      // Act
      FakeEventSource.last.emit('attack-path-version', { v: 1 });

      // Assert
      expect(FakeEventSource.last.listenerCount('attack-path-version')).toBe(1);
      expect(first).toHaveBeenCalledTimes(1);
      expect(second).toHaveBeenCalledTimes(1);

      FakeEventSource.last.onerror?.();
      vi.advanceTimersByTime(2000);
      expect(FakeEventSource.instances).toHaveLength(2);
      FakeEventSource.last.emit('attack-path-version', { v: 2 });
      expect(first).toHaveBeenCalledTimes(2);
    });

    it('given_anUnsubscribe_should_stopDelivering', () => {
      // Arrange
      const handler = vi.fn();
      client.startStream();
      const unsubscribe = client.subscribeStreamEvent('custom', handler);

      // Act
      unsubscribe();
      FakeEventSource.last.emit('custom', {});

      // Assert
      expect(handler).not.toHaveBeenCalled();
    });
  });

  describe('handler isolation', () => {
    it('given_aThrowingHandler_should_keepDeliveringToTheOthers', () => {
      // Arrange
      const boom = () => {
        throw new Error('boom');
      };
      const messages = vi.fn();
      const changes = vi.fn();
      const bulk = vi.fn();
      const opens = vi.fn();
      const named = vi.fn();
      client.onStreamMessage(boom);
      client.onStreamMessage(messages);
      client.onEntityChange(boom);
      client.onEntityChange(changes);
      client.onBulkOperationEnd(boom);
      client.onBulkOperationEnd(bulk);
      client.onConnectionOpen(boom);
      client.onConnectionOpen(opens);
      client.subscribeStreamEvent('custom', boom);
      client.subscribeStreamEvent('custom', named);
      client.startStream();
      const source = FakeEventSource.last;

      // Act
      const emitAll = () => {
        source.emit('open');
        source.emit('message', message());
        source.emit('bulk-operation', bulkOperation());
        source.emit('custom', {});
      };

      // Assert
      expect(emitAll).not.toThrow();
      expect(messages).toHaveBeenCalledTimes(1);
      expect(changes).toHaveBeenCalledTimes(1);
      expect(bulk).toHaveBeenCalledTimes(1);
      expect(opens).toHaveBeenCalledTimes(1);
      expect(named).toHaveBeenCalledTimes(1);
    });
  });
});
