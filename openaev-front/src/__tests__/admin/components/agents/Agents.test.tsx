import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import Agents from '../../../../admin/components/agents/Agents';
import { DATA_FETCH_SUCCESS } from '../../../../constants/ActionTypes';
import { type ExecutorOutput } from '../../../../utils/api-types';
import AppQueryProvider from '../../../../utils/query/AppQueryProvider';
import { startStream, stopStream } from '../../../../utils/stream/streamClient';
import FakeEventSource from '../../../utils/stream/fakeEventSource';

const mocks = vi.hoisted(() => ({ listExecutors: vi.fn() }));

vi.mock('../../../../actions/executors/executor-action', () => ({ listExecutors: mocks.listExecutors }));
vi.mock('../../../../utils/Action', () => ({
  buildUri: (path: string) => path,
  simpleCall: vi.fn(),
}));
vi.mock('../../../../store', () => ({ useHelper: (selector: (helper: { getPlatformSettings: () => unknown }) => unknown) => selector({ getPlatformSettings: () => ({ platform_license: { license_is_validated: true } }) }) }));
vi.mock('../../../../components/i18n', () => ({ useFormatter: () => ({ t: (value: string) => value }) }));
vi.mock('../../../../components/Breadcrumbs', () => ({ default: () => null }));
vi.mock('../../../../admin/components/agents/ExecutorSelector', () => ({ default: ({ executor }: { executor: ExecutorOutput }) => <div data-testid="executor">{executor.executor_name}</div> }));
vi.mock('@tanstack/react-query-devtools', () => ({ ReactQueryDevtools: () => null }));

const executor = (id: string, type: string, existing = true) => ({
  executor_id: id,
  executor_name: id,
  executor_type: type,
  existing_executor: existing,
}) as ExecutorOutput;

const renderPage = () => render(
  <AppQueryProvider rightsKey="rights">
    <Agents />
  </AppQueryProvider>,
);

const shownExecutors = () => screen.queryAllByTestId('executor').map(node => node.textContent);

describe('Agents', () => {
  beforeEach(() => {
    mocks.listExecutors.mockReset();
    FakeEventSource.reset();
    vi.stubGlobal('EventSource', FakeEventSource);
  });

  afterEach(() => {
    cleanup();
    stopStream();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('given_existingAndCatalogExecutors_should_listOnlyTheExistingOnesInTheirOrder', async () => {
    // Arrange
    mocks.listExecutors.mockResolvedValue({
      data: [
        executor('caldera', 'openaev_caldera'),
        executor('catalog-only', 'openaev_tanium', false),
        executor('agent', 'openaev_agent'),
      ],
    });

    // Act
    renderPage();

    // Assert
    await waitFor(() => expect(shownExecutors()).toEqual(['agent', 'caldera']));
    expect(mocks.listExecutors).toHaveBeenCalledWith(false, false);
  });

  it('given_anExecutorCreatedOnTheStream_should_showItWithoutReload', async () => {
    // Arrange: the page is loaded with the stream open
    vi.useFakeTimers({ shouldAdvanceTime: true });
    mocks.listExecutors.mockResolvedValue({ data: [executor('agent', 'openaev_agent')] });
    startStream();
    renderPage();
    FakeEventSource.last.emit('open');
    await waitFor(() => expect(shownExecutors()).toEqual(['agent']));
    // Past the batch window and the throttle of the open's own invalidation.
    await act(() => vi.advanceTimersByTimeAsync(2000));
    const callsBefore = mocks.listExecutors.mock.calls.length;
    mocks.listExecutors.mockResolvedValue({ data: [executor('agent', 'openaev_agent'), executor('caldera', 'openaev_caldera')] });

    // Act
    FakeEventSource.last.emit('message', {
      event_type: DATA_FETCH_SUCCESS,
      attribute_id: 'executor_id',
      attribute_schema: 'executors',
      instance: { executor_id: 'caldera' },
      listened: true,
    });

    await act(() => vi.advanceTimersByTimeAsync(250));

    // Assert
    await waitFor(() => expect(shownExecutors()).toEqual(['agent', 'caldera']));
    expect(mocks.listExecutors).toHaveBeenCalledTimes(callsBefore + 1);
  });

  it('given_aChangeOfAnotherType_should_notRefetch', async () => {
    // Arrange
    vi.useFakeTimers({ shouldAdvanceTime: true });
    mocks.listExecutors.mockResolvedValue({ data: [executor('agent', 'openaev_agent')] });
    startStream();
    renderPage();
    FakeEventSource.last.emit('open');
    await waitFor(() => expect(shownExecutors()).toEqual(['agent']));
    // Past the batch window and the throttle of the open's own invalidation.
    await act(() => vi.advanceTimersByTimeAsync(2000));
    const callsBefore = mocks.listExecutors.mock.calls.length;

    // Act
    FakeEventSource.last.emit('message', {
      event_type: DATA_FETCH_SUCCESS,
      attribute_id: 'tag_id',
      attribute_schema: 'tags',
      instance: { tag_id: 't1' },
      listened: true,
    });
    await act(() => vi.advanceTimersByTimeAsync(2000));

    // Assert
    expect(mocks.listExecutors).toHaveBeenCalledTimes(callsBefore);
  });
});
