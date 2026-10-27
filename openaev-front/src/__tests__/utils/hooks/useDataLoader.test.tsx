import { renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { DATA_UPDATE_SUCCESS } from '../../../constants/ActionTypes';
import type * as UseDataLoaderModule from '../../../utils/hooks/useDataLoader';
import { MAX_PENDING_ENTITIES } from '../../../utils/sse/SseActionBatcher';
import type * as StreamClientModule from '../../../utils/stream/streamClient';
import FakeEventSource from '../stream/fakeEventSource';

const dispatch = vi.fn();

vi.mock('../../../store', () => ({ store: { dispatch: (action: unknown) => dispatch(action) } }));
vi.mock('../../../utils/Action', () => ({
  buildUri: (path: string) => path,
  simpleCall: vi.fn(),
}));

type StreamClient = typeof StreamClientModule;
type UseDataLoader = typeof UseDataLoaderModule;

const tagMessage = (id = 't1') => ({
  event_type: DATA_UPDATE_SUCCESS,
  attribute_id: 'tag_id',
  attribute_schema: 'tags',
  instance: {
    tag_id: id,
    tag_name: 'name',
  },
  listened: true,
});

const setVisibility = (state: 'visible' | 'hidden') => {
  Object.defineProperty(document, 'visibilityState', {
    configurable: true,
    value: state,
  });
};

const changeVisibility = (state: 'visible' | 'hidden') => {
  setVisibility(state);
  document.dispatchEvent(new Event('visibilitychange'));
};

describe('useDataLoader', () => {
  let client: StreamClient;
  let useDataLoader: UseDataLoader['default'];

  beforeEach(async () => {
    vi.useFakeTimers();
    dispatch.mockReset();
    FakeEventSource.reset();
    setVisibility('visible');
    vi.stubGlobal('EventSource', FakeEventSource);
    vi.resetModules();
    client = await import('../../../utils/stream/streamClient');
    useDataLoader = (await import('../../../utils/hooks/useDataLoader')).default;
  });

  afterEach(() => {
    client.stopStream();
    vi.useRealTimers();
    vi.unstubAllGlobals();
    setVisibility('visible');
  });

  describe('stream shared by Redux and entity-change consumers', () => {
    it('given_loadersAndEntityChangeHandlers_should_serveThemFromOneEventSource', () => {
      // Arrange
      const changes = vi.fn();
      client.onEntityChange(changes);
      const loader = vi.fn();
      renderHook(() => useDataLoader(loader, []));
      renderHook(() => useDataLoader(vi.fn(), []));
      client.startStream();

      // Act
      FakeEventSource.last.emit('message', tagMessage());
      vi.advanceTimersByTime(200);

      // Assert
      expect(FakeEventSource.instances).toHaveLength(1);
      expect(changes).toHaveBeenCalledWith({
        type: 'tags',
        id: 't1',
        action: 'update',
      });
      expect(dispatch).toHaveBeenCalledTimes(1);
      expect(dispatch.mock.calls[0][0].type).toBe(DATA_UPDATE_SUCCESS);
    });

    it('given_noLoaderMounted_should_feedEntityChangeHandlersWithoutTouchingTheStore', () => {
      // Arrange
      const changes = vi.fn();
      client.onEntityChange(changes);
      client.startStream();

      // Act
      FakeEventSource.last.emit('message', tagMessage());
      vi.advanceTimersByTime(1000);

      // Assert
      expect(changes).toHaveBeenCalledTimes(1);
      expect(dispatch).not.toHaveBeenCalled();
    });

    it('given_theLastLoaderUnmounting_should_keepTheStreamOpen', () => {
      // Arrange
      client.startStream();
      const { unmount } = renderHook(() => useDataLoader(vi.fn(), []));
      const source = FakeEventSource.last;

      // Act
      unmount();

      // Assert
      expect(source.closed).toBe(false);
      expect(FakeEventSource.instances).toHaveLength(1);
    });
  });

  describe('loading', () => {
    it('given_aLoaderMountedBeforeTheFirstOpen_should_loadOnceOnTheOpenNotOnMount', () => {
      // Arrange
      client.startStream();
      const loader = vi.fn();

      // Act
      renderHook(() => useDataLoader(loader, []));

      // Assert
      expect(loader).not.toHaveBeenCalled();
      FakeEventSource.last.emit('open');
      expect(loader).toHaveBeenCalledTimes(1);
    });

    it('given_aLoaderMountedAfterTheFirstOpen_should_loadAtOnce', () => {
      // Arrange
      client.startStream();
      FakeEventSource.last.emit('open');
      const loader = vi.fn();

      // Act
      renderHook(() => useDataLoader(loader, []));

      // Assert
      expect(loader).toHaveBeenCalledTimes(1);
    });

    it('given_loadersMountedBeforeAndAfterTheFirstOpen_should_loadEachOnceInTheRightOrder', () => {
      // Arrange
      client.startStream();
      const before = vi.fn();
      const after = vi.fn();
      renderHook(() => useDataLoader(before, []));

      // Act
      const beforeOpen = before.mock.calls.length;
      FakeEventSource.last.emit('open');
      const afterOpen = before.mock.calls.length;
      renderHook(() => useDataLoader(after, []));

      // Assert
      expect(beforeOpen).toBe(0);
      expect(afterOpen).toBe(1);
      expect(after).toHaveBeenCalledTimes(1);
      expect(before).toHaveBeenCalledTimes(1);
    });

    it('given_aReconnection_should_reloadEveryLoader', () => {
      // Arrange
      client.startStream();
      FakeEventSource.last.emit('open');
      const loader = vi.fn();
      renderHook(() => useDataLoader(loader, []));
      expect(loader).toHaveBeenCalledTimes(1);

      // Act
      FakeEventSource.last.onerror?.();
      vi.advanceTimersByTime(2000);
      FakeEventSource.last.emit('open');

      // Assert
      expect(loader).toHaveBeenCalledTimes(2);
    });

    it('given_noSession_should_loadAtOnceBecauseNoStreamWillReloadIt', () => {
      // Arrange
      const loader = vi.fn();

      // Act
      renderHook(() => useDataLoader(loader, []));

      // Assert
      expect(loader).toHaveBeenCalledTimes(1);
      expect(FakeEventSource.instances).toHaveLength(0);
    });

    it('given_aBulkOperationEnding_should_reloadTheMountedLoadersOnce', () => {
      // Arrange
      client.startStream();
      const loader = vi.fn();
      renderHook(() => useDataLoader(loader, []));
      FakeEventSource.last.emit('open');
      loader.mockClear();

      // Act
      FakeEventSource.last.emit('bulk-operation', {
        bulk_operation_id: 'b1',
        bulk_operation_action: 'DELETE',
        bulk_operation_entity: 'injects',
        bulk_operation_total: 1,
        bulk_operation_processed: 1,
        bulk_operation_status: 'COMPLETED',
        bulk_operation_started_at: '2026-01-01T00:00:00Z',
      });

      // Assert
      expect(loader).toHaveBeenCalledTimes(1);
    });
  });

  describe('hidden tab', () => {
    it('given_aReconnectionWhileHidden_should_reloadOnceWhenTheTabBecomesVisible', () => {
      // Arrange
      client.startStream();
      const loader = vi.fn();
      renderHook(() => useDataLoader(loader, []));
      FakeEventSource.last.emit('open');
      loader.mockClear();
      FakeEventSource.last.onerror?.();
      vi.advanceTimersByTime(2000);
      setVisibility('hidden');

      // Act
      FakeEventSource.last.emit('open');

      // Assert
      expect(loader).not.toHaveBeenCalled();

      // Act
      changeVisibility('visible');

      // Assert
      expect(loader).toHaveBeenCalledTimes(1);
      changeVisibility('visible');
      expect(loader).toHaveBeenCalledTimes(1);
    });

    it('given_aBulkOperationEndingWhileHidden_should_reloadOnceWhenTheTabBecomesVisible', () => {
      // Arrange
      client.startStream();
      const loader = vi.fn();
      renderHook(() => useDataLoader(loader, []));
      FakeEventSource.last.emit('open');
      loader.mockClear();
      setVisibility('hidden');

      // Act
      FakeEventSource.last.emit('bulk-operation', {
        bulk_operation_id: 'b1',
        bulk_operation_action: 'DELETE',
        bulk_operation_entity: 'injects',
        bulk_operation_total: 1,
        bulk_operation_processed: 1,
        bulk_operation_status: 'COMPLETED',
        bulk_operation_started_at: '2026-01-01T00:00:00Z',
      });

      // Assert
      expect(loader).not.toHaveBeenCalled();
      changeVisibility('visible');
      expect(loader).toHaveBeenCalledTimes(1);
    });

    it('given_aBacklogWithinTheCapWhileHidden_should_dispatchItWhenTheTabBecomesVisible', () => {
      // Arrange
      client.startStream();
      const loader = vi.fn();
      renderHook(() => useDataLoader(loader, []));
      FakeEventSource.last.emit('open');
      loader.mockClear();
      setVisibility('hidden');
      FakeEventSource.last.emit('message', tagMessage('t1'));
      FakeEventSource.last.emit('message', tagMessage('t2'));

      // Act
      changeVisibility('visible');

      // Assert
      expect(dispatch).toHaveBeenCalledTimes(1);
      expect(loader).not.toHaveBeenCalled();
    });
  });

  describe('backlog overflow', () => {
    it('given_aBacklogOverTheCapWhileVisible_should_resyncByReloadingTheLoadersInsteadOfDispatching', () => {
      // Arrange
      client.startStream();
      const loader = vi.fn();
      renderHook(() => useDataLoader(loader, []));
      FakeEventSource.last.emit('open');
      loader.mockClear();

      // Act
      for (let i = 0; i <= MAX_PENDING_ENTITIES; i += 1) {
        FakeEventSource.last.emit('message', tagMessage(`t${i}`));
      }
      vi.advanceTimersByTime(2000);

      // Assert
      expect(loader).toHaveBeenCalledTimes(1);
      expect(dispatch).not.toHaveBeenCalled();
    });

    it('given_aBacklogOverTheCapWhileHidden_should_resyncOnceWhenTheTabBecomesVisible', () => {
      // Arrange
      client.startStream();
      const loader = vi.fn();
      renderHook(() => useDataLoader(loader, []));
      FakeEventSource.last.emit('open');
      loader.mockClear();
      setVisibility('hidden');

      // Act
      for (let i = 0; i <= MAX_PENDING_ENTITIES; i += 1) {
        FakeEventSource.last.emit('message', tagMessage(`t${i}`));
      }
      vi.advanceTimersByTime(2000);

      // Assert
      expect(loader).not.toHaveBeenCalled();
      changeVisibility('visible');
      expect(loader).toHaveBeenCalledTimes(1);
      expect(dispatch).not.toHaveBeenCalled();
    });
  });
});
