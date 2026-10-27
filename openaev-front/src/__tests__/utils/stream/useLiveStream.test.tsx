import { renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type * as StreamClientModule from '../../../utils/stream/streamClient';
import type * as UseLiveStreamModule from '../../../utils/stream/useLiveStream';
import FakeEventSource from './fakeEventSource';

vi.mock('../../../utils/Action', () => ({
  buildUri: (path: string) => path,
  simpleCall: vi.fn(),
}));

type StreamClient = typeof StreamClientModule;
type UseLiveStream = (typeof UseLiveStreamModule)['default'];

describe('useLiveStream', () => {
  let client: StreamClient;
  let useLiveStream: UseLiveStream;

  beforeEach(async () => {
    vi.useFakeTimers();
    FakeEventSource.reset();
    vi.stubGlobal('EventSource', FakeEventSource);
    vi.resetModules();
    client = await import('../../../utils/stream/streamClient');
    useLiveStream = (await import('../../../utils/stream/useLiveStream')).default;
  });

  afterEach(() => {
    client.stopStream();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('given_aMount_should_startTheStreamWithASingleEventSource', () => {
    // Arrange / Act
    renderHook(() => useLiveStream());

    // Assert
    expect(client.isStreamStarted()).toBe(true);
    expect(FakeEventSource.instances).toHaveLength(1);
    expect(FakeEventSource.last.closed).toBe(false);
  });

  it('given_aRerender_should_keepTheSameConnection', () => {
    // Arrange
    const { rerender } = renderHook(() => useLiveStream());

    // Act
    rerender();

    // Assert
    expect(FakeEventSource.instances).toHaveLength(1);
    expect(FakeEventSource.last.closed).toBe(false);
  });

  it('given_anUnmount_should_closeTheStream', () => {
    // Arrange
    const { unmount } = renderHook(() => useLiveStream());
    const source = FakeEventSource.last;

    // Act
    unmount();

    // Assert
    expect(source.closed).toBe(true);
    expect(client.isStreamStarted()).toBe(false);
  });

  it('given_anUnmountThenAMount_should_openAFreshConnection', () => {
    // Arrange
    const first = renderHook(() => useLiveStream());
    const firstSource = FakeEventSource.last;
    first.unmount();

    // Act
    renderHook(() => useLiveStream());

    // Assert
    expect(FakeEventSource.instances).toHaveLength(2);
    expect(firstSource.closed).toBe(true);
    expect(FakeEventSource.last).not.toBe(firstSource);
    expect(FakeEventSource.last.closed).toBe(false);
    expect(client.isStreamStarted()).toBe(true);
  });
});
