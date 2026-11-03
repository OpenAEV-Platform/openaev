import '@testing-library/jest-dom/vitest';

import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import { type ReactElement, type ReactNode } from 'react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import Endpoints from '../../../../../admin/components/assets/endpoints/Endpoints';
import { type Page } from '../../../../../components/common/queryable/Page';
import { type QueryableHelpers } from '../../../../../components/common/queryable/QueryableHelpers';
import { DATA_DELETE_SUCCESS, DATA_FETCH_SUCCESS, DATA_UPDATE_SUCCESS } from '../../../../../constants/ActionTypes';
import { type AgentOutput, type Endpoint, type EndpointOutput, type SearchPaginationInput } from '../../../../../utils/api-types';
import AppQueryProvider from '../../../../../utils/query/AppQueryProvider';
import { startStream, stopStream } from '../../../../../utils/stream/streamClient';
import FakeEventSource from '../../../../utils/stream/fakeEventSource';

const mocks = vi.hoisted(() => ({
  searchAssets: vi.fn(),
  bulkDeleteAssets: vi.fn(),
  onCreate: undefined as ((result: Endpoint) => void) | undefined,
  onAiTargetCreate: undefined as ((aiTargetId: string) => void) | undefined,
  onUpdate: new Map<string, (result: EndpointOutput) => void>(),
  onDelete: new Map<string, (assetId: string) => void>(),
  handleBulkDelete: undefined as (() => void) | undefined,
  queryableHelpers: undefined as QueryableHelpers | undefined,
}));

// The page's own query, through the real `asset-queries.ts` and `usePaginatedQuery`.
vi.mock('../../../../../actions/assets/endpoint-actions', () => ({
  searchAssets: mocks.searchAssets,
  bulkDeleteAssets: mocks.bulkDeleteAssets,
  getAssetOverview: vi.fn(),
}));
vi.mock('../../../../../actions/executors/executor-action', () => ({ fetchExecutors: vi.fn() }));
vi.mock('../../../../../utils/Action', () => ({
  buildUri: (path: string) => path,
  simpleCall: vi.fn(),
}));
vi.mock('../../../../../utils/url-helper', async (importOriginal) => {
  // eslint-disable-next-line @typescript-eslint/consistent-type-imports
  const original = await importOriginal<typeof import('../../../../../utils/url-helper')>();
  return {
    ...original,
    getCurrentTenantId: () => 'tenant-a',
  };
});
vi.mock('../../../../../utils/hooks', () => ({ useAppDispatch: () => vi.fn() }));
vi.mock('../../../../../utils/hooks/useDataLoader', () => ({ default: vi.fn() }));
vi.mock('../../../../../utils/hooks/useMarkingDefinitions', () => ({ default: () => [] }));
vi.mock('../../../../../utils/error/errorHandlerUtil', () => ({ notifyErrorHandler: vi.fn() }));
vi.mock('../../../../../utils/utils', () => ({ isFeatureEnabled: () => false }));
vi.mock('../../../../../utils/permissions/permissionsContext', () => ({
  useAbility: () => ({ can: () => true }),
  Can: ({ children }: { children: ReactNode }) => <>{children}</>,
}));
vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (value: string) => value }) }));
vi.mock('../../../../../components/Breadcrumbs', () => ({ default: () => null }));
vi.mock('../../../../../components/PaginatedListLoader', () => ({ default: () => <div data-testid="loader" /> }));
vi.mock('../../../../../components/ItemTags', () => ({ default: () => null }));
vi.mock('../../../../../components/common/ExportButton', () => ({ default: () => null }));
vi.mock('../../../../../components/common/queryable/QueryableToolbar', () => ({
  default: ({ topBarButtons, queryableHelpers }: {
    topBarButtons?: ReactElement;
    queryableHelpers: QueryableHelpers;
  }) => {
    mocks.queryableHelpers = queryableHelpers;
    return <div>{topBarButtons}</div>;
  },
}));
vi.mock('../../../../../components/common/queryable/sort/SortHeadersComponentV2', () => ({ default: () => null }));
vi.mock('../../../../../admin/components/assets/usePostureScores', () => ({
  default: () => ({
    loading: false,
    scores: {},
  }),
}));
vi.mock('../../../../../admin/components/assets/PostureScoreCell', () => ({ default: () => null }));
vi.mock('../../../../../admin/components/common/endpoints/fragments/EndpointAgentsExecutorsFragment', () => ({ default: () => null }));
vi.mock('../../../../../admin/components/assets/endpoints/ImportUploaderEndpoints', () => ({ default: () => null }));
vi.mock('../../../../../admin/components/assets/endpoints/EndpointCreation', () => ({
  default: ({ onCreate, onAiTargetCreate }: {
    onCreate?: (result: Endpoint) => void;
    onAiTargetCreate?: (aiTargetId: string) => void;
  }) => {
    mocks.onCreate = onCreate;
    mocks.onAiTargetCreate = onAiTargetCreate;
    return null;
  },
}));
vi.mock('../../../../../admin/components/assets/endpoints/AssetPopover', () => ({
  default: ({ endpoint, onUpdate, onDelete }: {
    endpoint: EndpointOutput;
    onUpdate?: (result: EndpointOutput) => void;
    onDelete?: (assetId: string) => void;
  }) => {
    if (onUpdate) mocks.onUpdate.set(endpoint.asset_id, onUpdate);
    if (onDelete) mocks.onDelete.set(endpoint.asset_id, onDelete);
    return null;
  },
}));
vi.mock('../../../../../admin/components/common/ToolBar', () => ({
  default: ({ handleBulkDelete }: { handleBulkDelete?: () => void }) => {
    mocks.handleBulkDelete = handleBulkDelete;
    return <div data-testid="toolbar" />;
  },
}));
vi.mock('@tanstack/react-query-devtools', () => ({ ReactQueryDevtools: () => null }));

// The list query's throttle (`assetQueries.list`).
const LIST_THROTTLE_MS = 10_000;

const deferred = <T, >() => {
  let resolve: (value: T) => void = () => {};
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return {
    promise,
    resolve,
  };
};

const SORT = {
  empty: true,
  sorted: false,
  unsorted: true,
};

const asset = (id: string, name: string, agents: string[] = []) => ({
  asset_id: id,
  asset_name: name,
  asset_category: 'HOST',
  asset_agents: agents.map(agentId => ({ agent_id: agentId }) as AgentOutput),
  asset_tags: [],
}) as unknown as EndpointOutput;

const pageOf = (content: EndpointOutput[], input?: SearchPaginationInput): Page<EndpointOutput> => ({
  content,
  empty: content.length === 0,
  first: true,
  last: true,
  number: input?.page ?? 0,
  numberOfElements: content.length,
  pageable: {
    offset: 0,
    pageNumber: input?.page ?? 0,
    pageSize: input?.size ?? 20,
    paged: true,
    sort: SORT,
    unpaged: false,
  },
  size: input?.size ?? 20,
  sort: SORT,
  totalElements: content.length,
  totalPages: content.length === 0 ? 0 : 1,
});

const WEB = asset('asset-1', 'Web server', ['agent-1']);
const DB = asset('asset-2', 'Database');

const renderPage = () => render(
  <ThemeProvider theme={createTheme()}>
    <TooltipProvider>
      <AppQueryProvider>
        <MemoryRouter initialEntries={['/admin/assets']}>
          <Endpoints />
        </MemoryRouter>
      </AppQueryProvider>
    </TooltipProvider>
  </ThemeProvider>,
);

const streamMessage = (message: {
  event_type: string;
  attribute_id: string;
  attribute_schema: string;
  instance: Record<string, unknown>;
}) => FakeEventSource.last.emit('message', {
  ...message,
  listened: true,
});

const assetMessage = (eventType: string, assetId: string) => streamMessage({
  event_type: eventType,
  attribute_id: 'asset_id',
  attribute_schema: 'assets',
  instance: { asset_id: assetId },
});

describe('Endpoints', () => {
  const calls = () => mocks.searchAssets.mock.calls.length;

  // The list loaded with the stream open, once the open's own invalidation has settled.
  const renderLivePage = async () => {
    startStream();
    renderPage();
    FakeEventSource.last.emit('open');
    await waitFor(() => expect(screen.getByText('Web server')).toBeInTheDocument());
    // Past the throttle of the open's invalidation: the next change refetches at once.
    await act(() => vi.advanceTimersByTimeAsync(LIST_THROTTLE_MS + 1000));
    return calls();
  };

  // Calls `trigger` (a callback the page handed to a child) and returns what it resolves to.
  const fire = (trigger: () => unknown) => {
    let refreshed: unknown;
    act(() => {
      refreshed = trigger();
    });
    return refreshed as Promise<void>;
  };

  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    localStorage.clear();
    mocks.searchAssets.mockReset();
    mocks.searchAssets.mockImplementation(({ input }: { input: SearchPaginationInput }) => Promise.resolve(pageOf([WEB, DB], input)));
    mocks.bulkDeleteAssets.mockReset();
    mocks.onCreate = undefined;
    mocks.onAiTargetCreate = undefined;
    mocks.onUpdate.clear();
    mocks.onDelete.clear();
    mocks.handleBulkDelete = undefined;
    mocks.queryableHelpers = undefined;
    FakeEventSource.reset();
    vi.stubGlobal('EventSource', FakeEventSource);
  });

  afterEach(() => {
    cleanup();
    stopStream();
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  describe('loading', () => {
    it('given_theFirstPageLoading_should_showTheLoaderThenTheAssets', async () => {
      // Act
      renderPage();

      // Assert
      expect(screen.getByTestId('loader')).toBeInTheDocument();
      await waitFor(() => expect(screen.getByText('Web server')).toBeInTheDocument());
      expect(screen.getByText('Database')).toBeInTheDocument();
      expect(screen.queryByTestId('loader')).not.toBeInTheDocument();
      expect(mocks.searchAssets).toHaveBeenCalledWith(expect.objectContaining({
        input: expect.objectContaining({
          page: 0,
          sorts: [expect.objectContaining({ property: 'asset_name' })],
        }),
      }));
    });

    it('given_anotherPage_should_showTheLoaderUntilItsRowsArrive', async () => {
      // Arrange
      renderPage();
      await waitFor(() => expect(screen.getByText('Web server')).toBeInTheDocument());
      const secondPage = deferred<Page<EndpointOutput>>();
      mocks.searchAssets.mockReturnValue(secondPage.promise);

      // Act
      act(() => mocks.queryableHelpers?.paginationHelpers.handleChangePage(1));

      // Assert
      await waitFor(() => expect(screen.getByTestId('loader')).toBeInTheDocument());
      expect(screen.queryByText('Web server')).not.toBeInTheDocument();
      act(() => secondPage.resolve(pageOf([asset('asset-21', 'Print server')])));
      await waitFor(() => expect(screen.getByText('Print server')).toBeInTheDocument());
      expect(screen.queryByTestId('loader')).not.toBeInTheDocument();
    });
  });

  describe('live', () => {
    it('given_anAssetCreatedOnTheStream_should_refetchThePage', async () => {
      // Arrange
      const callsBefore = await renderLivePage();
      mocks.searchAssets.mockResolvedValue(pageOf([WEB, DB, asset('asset-3', 'Mail server')]));

      // Act
      assetMessage(DATA_FETCH_SUCCESS, 'asset-3');
      await act(() => vi.advanceTimersByTimeAsync(250));

      // Assert
      await waitFor(() => expect(screen.getByText('Mail server')).toBeInTheDocument());
      expect(calls()).toBe(callsBefore + 1);
    });

    it('given_aListedAssetUpdatedOnTheStream_should_keepTheRowsWhileItRefetches', async () => {
      // Arrange
      const callsBefore = await renderLivePage();
      const refetched = deferred<Page<EndpointOutput>>();
      mocks.searchAssets.mockReturnValue(refetched.promise);

      // Act
      assetMessage(DATA_UPDATE_SUCCESS, 'asset-1');
      await act(() => vi.advanceTimersByTimeAsync(250));

      // Assert: the rows stay, without the loader, until the new ones replace them
      await waitFor(() => expect(calls()).toBe(callsBefore + 1));
      expect(screen.getByText('Web server')).toBeInTheDocument();
      expect(screen.queryByTestId('loader')).not.toBeInTheDocument();
      act(() => refetched.resolve(pageOf([asset('asset-1', 'Renamed server', ['agent-1']), DB])));
      await waitFor(() => expect(screen.getByText('Renamed server')).toBeInTheDocument());
    });

    it('given_agentHeartbeatsOfListedAssets_should_refetchAtMostOncePerThrottle', async () => {
      // Arrange: every heartbeat streams an update of the endpoint
      const callsBefore = await renderLivePage();

      // Act
      assetMessage(DATA_UPDATE_SUCCESS, 'asset-1');
      await act(() => vi.advanceTimersByTimeAsync(1000));
      const callsAfterTheFirst = calls();
      assetMessage(DATA_UPDATE_SUCCESS, 'asset-1');
      await act(() => vi.advanceTimersByTimeAsync(1000));
      const callsAfterTheSecond = calls();
      await act(() => vi.advanceTimersByTimeAsync(LIST_THROTTLE_MS));

      // Assert: the second one waits for the end of the throttle
      expect(callsAfterTheFirst).toBe(callsBefore + 1);
      expect(callsAfterTheSecond).toBe(callsBefore + 1);
      expect(calls()).toBe(callsBefore + 2);
    });

    it('given_anUpdateOfAnAssetNotListed_should_notRefetch', async () => {
      // Arrange
      const callsBefore = await renderLivePage();

      // Act
      assetMessage(DATA_UPDATE_SUCCESS, 'asset-elsewhere');
      await act(() => vi.advanceTimersByTimeAsync(2000));

      // Assert
      expect(calls()).toBe(callsBefore);
    });

    it('given_aChangeOfAListedAssetsAgent_should_refetchThePage', async () => {
      // Arrange
      const callsBefore = await renderLivePage();

      // Act: no asset on the instance, so only the agent shown in the row can match
      streamMessage({
        event_type: DATA_DELETE_SUCCESS,
        attribute_id: 'agent_id',
        attribute_schema: 'agents',
        instance: { agent_id: 'agent-1' },
      });
      await act(() => vi.advanceTimersByTimeAsync(250));

      // Assert
      await waitFor(() => expect(calls()).toBe(callsBefore + 1));
    });
  });

  describe('changes made on the page', () => {
    it('given_anAssetDeletedFromItsRowMenu_should_refetchAtOnce', async () => {
      // Arrange
      const callsBefore = await renderLivePage();
      mocks.searchAssets.mockResolvedValue(pageOf([DB]));

      // Act: no wait for the batch window
      const refreshed = fire(() => mocks.onDelete.get('asset-1')?.('asset-1'));
      const callsRightAfter = calls();

      // Assert
      expect(callsRightAfter).toBe(callsBefore + 1);
      await act(() => refreshed);
      await waitFor(() => expect(screen.queryByText('Web server')).not.toBeInTheDocument());
    });

    it('given_anAssetUpdatedFromItsRowMenu_should_refetchAtOnce', async () => {
      // Arrange
      const callsBefore = await renderLivePage();
      mocks.searchAssets.mockResolvedValue(pageOf([asset('asset-1', 'Renamed server', ['agent-1']), DB]));

      // Act
      const refreshed = fire(() => mocks.onUpdate.get('asset-1')?.(asset('asset-1', 'Renamed server')));
      const callsRightAfter = calls();

      // Assert
      expect(callsRightAfter).toBe(callsBefore + 1);
      await act(() => refreshed);
      await waitFor(() => expect(screen.getByText('Renamed server')).toBeInTheDocument());
    });

    it('given_anEndpointCreated_should_refetchAtOnce', async () => {
      // Arrange
      const callsBefore = await renderLivePage();
      mocks.searchAssets.mockResolvedValue(pageOf([WEB, DB, asset('asset-3', 'Mail server')]));

      // Act
      const refreshed = fire(() => mocks.onCreate?.({ asset_id: 'asset-3' } as Endpoint));
      const callsRightAfter = calls();

      // Assert
      expect(callsRightAfter).toBe(callsBefore + 1);
      await act(() => refreshed);
      await waitFor(() => expect(screen.getByText('Mail server')).toBeInTheDocument());
    });

    it('given_anAiTargetCreated_should_refetchAtOnce', async () => {
      // Arrange
      const callsBefore = await renderLivePage();
      mocks.searchAssets.mockResolvedValue(pageOf([WEB, DB, asset('asset-4', 'Chat model')]));

      // Act
      const refreshed = fire(() => mocks.onAiTargetCreate?.('asset-4'));
      const callsRightAfter = calls();

      // Assert
      expect(callsRightAfter).toBe(callsBefore + 1);
      await act(() => refreshed);
      await waitFor(() => expect(screen.getByText('Chat model')).toBeInTheDocument());
    });

    it('given_aBulkDelete_should_refetchOnceTheAssetsAreDeletedAndClearTheSelection', async () => {
      // Arrange: one asset selected
      const callsBefore = await renderLivePage();
      act(() => screen.getByRole('checkbox', { name: 'Web server' }).click());
      await waitFor(() => expect(screen.getByTestId('toolbar')).toBeInTheDocument());
      const deleted = deferred<{ data: string[] }>();
      mocks.bulkDeleteAssets.mockReturnValue(deleted.promise);
      mocks.searchAssets.mockResolvedValue(pageOf([DB]));

      // Act
      act(() => mocks.handleBulkDelete?.());
      const callsBeforeTheDeletion = calls();
      await act(async () => deleted.resolve({ data: ['asset-1'] }));

      // Assert: refetched once the server deleted them, not before
      expect(mocks.bulkDeleteAssets).toHaveBeenCalledWith(expect.objectContaining({ asset_ids_to_process: ['asset-1'] }));
      expect(callsBeforeTheDeletion).toBe(callsBefore);
      await waitFor(() => expect(screen.queryByText('Web server')).not.toBeInTheDocument());
      expect(calls()).toBe(callsBefore + 1);
      expect(screen.queryByTestId('toolbar')).not.toBeInTheDocument();
    });
  });
});
