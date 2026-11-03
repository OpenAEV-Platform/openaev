import '@testing-library/jest-dom/vitest';

import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { type AssetOverview } from '../../../../../actions/assets/endpoint-actions';
import AssetDetail from '../../../../../admin/components/assets/asset/AssetDetail';
import { DATA_DELETE_SUCCESS, DATA_FETCH_SUCCESS, DATA_UPDATE_SUCCESS } from '../../../../../constants/ActionTypes';
import { type AgentOutput, type EndpointOverviewOutput } from '../../../../../utils/api-types';
import AppQueryProvider from '../../../../../utils/query/AppQueryProvider';
import { startStream, stopStream } from '../../../../../utils/stream/streamClient';
import FakeEventSource from '../../../../utils/stream/fakeEventSource';

const ASSET_ID = 'asset-1';

const mocks = vi.hoisted(() => ({
  getAssetOverview: vi.fn(),
  onUpdate: undefined as ((result: EndpointOverviewOutput) => void) | undefined,
}));

// The page's own query, through the real `asset-queries.ts`.
vi.mock('../../../../../actions/assets/endpoint-actions', () => ({
  getAssetOverview: mocks.getAssetOverview,
  searchInjectsForAsset: vi.fn(),
}));
vi.mock('../../../../../actions/findings/finding-actions', () => ({ searchDistinctFindingsOnEndpoint: vi.fn() }));
vi.mock('../../../../../actions/users/User', () => ({ fetchPlayers: vi.fn() }));
vi.mock('../../../../../utils/Action', () => ({
  buildUri: (path: string) => path,
  simpleCall: vi.fn(),
}));
vi.mock('../../../../../store', () => ({
  useHelper: (selector: (helper: object) => unknown) => selector({
    getUsersMap: () => ({}),
    getPlatformSettings: () => ({ enabled_dev_features: [] }),
  }),
}));
vi.mock('../../../../../utils/hooks', () => ({ useAppDispatch: () => vi.fn() }));
vi.mock('../../../../../utils/hooks/useDataLoader', () => ({ default: vi.fn() }));
vi.mock('../../../../../utils/hooks/useMarkingDefinitions', () => ({ default: () => [] }));
vi.mock('../../../../../utils/hooks/useSearchTotal', () => ({ default: () => 0 }));
vi.mock('../../../../../utils/error/errorHandlerUtil', () => ({ notifyErrorHandler: vi.fn() }));
vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (value: string) => value }) }));
vi.mock('../../../../../components/Breadcrumbs', () => ({ default: () => null }));
vi.mock('../../../../../components/Loader', () => ({ default: () => <div data-testid="loader" /> }));
vi.mock('../../../../../components/ItemTags', () => ({ default: () => null }));
vi.mock('../../../../../components/common/queryable/useQueryableWithLocalStorage', () => ({
  useQueryableWithLocalStorage: () => ({
    queryableHelpers: {},
    searchPaginationInput: {},
  }),
}));
vi.mock('../../../../../admin/components/assets/useExpectationPosture', () => ({
  default: () => ({
    loading: false,
    success: 0,
    failed: 0,
    tested: 0,
    breakdown: [],
  }),
}));
vi.mock('../../../../../admin/components/findings/FindingList', () => ({ default: () => null }));
vi.mock('../../../../../admin/components/assets/ExpectationList', () => ({ default: () => null }));
vi.mock('../../../../../admin/components/atomic_testings/InjectResultList', () => ({ default: () => null }));
vi.mock('../../../../../admin/components/assets/statistics/InjectsPlayedOverTimeChart', () => ({ default: () => null }));
vi.mock('../../../../../admin/components/assets/statistics/PostureScoreOverTimeChart', () => ({ default: () => null }));
vi.mock('../../../../../admin/components/assets/PostureScore', () => ({ default: () => null }));
vi.mock('../../../../../admin/components/reporting/EntityReportsPanel', () => ({ default: () => null }));
vi.mock('../../../../../admin/components/assets/endpoints/endpoint/AgentList', () => ({ default: ({ agents }: { agents: AgentOutput[] }) => <div data-testid="agents">{agents.map(agent => agent.agent_id).join(',')}</div> }));
vi.mock('../../../../../admin/components/assets/endpoints/AssetPopover', () => ({
  default: ({ onUpdate }: { onUpdate?: (result: EndpointOverviewOutput) => void }) => {
    mocks.onUpdate = onUpdate;
    return null;
  },
}));
vi.mock('@tanstack/react-query-devtools', () => ({ ReactQueryDevtools: () => null }));

const agent = (id: string) => ({ agent_id: id }) as AgentOutput;

const asset = (overrides: Partial<AssetOverview> = {}): AssetOverview => ({
  asset_id: ASSET_ID,
  asset_name: 'Web server',
  asset_hostname: 'web-01.corp',
  asset_category: 'HOST',
  asset_agents: [agent('agent-1')],
  asset_asset_groups: [],
  ...overrides,
});

const renderPage = () => render(
  <ThemeProvider theme={createTheme()}>
    <TooltipProvider>
      <AppQueryProvider>
        <MemoryRouter initialEntries={[`/admin/assets/${ASSET_ID}`]}>
          <Routes>
            <Route path="/admin/assets/:assetId/*" element={<AssetDetail />} />
          </Routes>
        </MemoryRouter>
      </AppQueryProvider>
    </TooltipProvider>
  </ThemeProvider>,
);

const title = (name: string) => screen.getByRole('heading', {
  level: 1,
  name,
});

const streamMessage = (message: {
  event_type: string;
  attribute_id: string;
  attribute_schema: string;
  instance: Record<string, unknown>;
}) => FakeEventSource.last.emit('message', {
  ...message,
  listened: true,
});

const assetMessage = (eventType: string) => streamMessage({
  event_type: eventType,
  attribute_id: 'asset_id',
  attribute_schema: 'assets',
  instance: { asset_id: ASSET_ID },
});

describe('AssetDetail', () => {
  // The page loaded with the stream open, once the open's own invalidation has settled.
  const renderLivePage = async () => {
    startStream();
    renderPage();
    FakeEventSource.last.emit('open');
    await waitFor(() => expect(title('Web server')).toBeInTheDocument());
    // Past the batch window and the throttle of the open's invalidation.
    await act(() => vi.advanceTimersByTimeAsync(2000));
    return mocks.getAssetOverview.mock.calls.length;
  };

  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    mocks.getAssetOverview.mockReset();
    mocks.getAssetOverview.mockResolvedValue(asset());
    mocks.onUpdate = undefined;
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
    it('given_theAssetLoading_should_showTheLoaderThenTheTitleAndTheHostname', async () => {
      // Arrange
      let answer: (value: AssetOverview) => void = () => {};
      mocks.getAssetOverview.mockReturnValue(new Promise<AssetOverview>((resolve) => {
        answer = resolve;
      }));

      // Act
      renderPage();
      const loaderWhilePending = screen.queryByTestId('loader');
      answer(asset());

      // Assert
      expect(loaderWhilePending).toBeInTheDocument();
      await waitFor(() => expect(title('Web server')).toBeInTheDocument());
      expect(screen.getByText('web-01.corp')).toBeInTheDocument();
      expect(screen.queryByTestId('loader')).not.toBeInTheDocument();
      expect(mocks.getAssetOverview).toHaveBeenCalledWith(expect.objectContaining({ assetId: ASSET_ID }));
    });

    it('given_aFailureOtherThanNotFound_should_showTheUnavailableAlert', async () => {
      // Arrange: a 4xx, not retried
      mocks.getAssetOverview.mockRejectedValue({ status: 403 });

      // Act
      renderPage();

      // Assert
      await waitFor(() => expect(screen.getByText('Asset is currently unavailable or you do not have sufficient permissions to access it.')).toBeInTheDocument());
      expect(screen.queryByText('This item does not exist or you are not allowed to view it.')).not.toBeInTheDocument();
    });
  });

  describe('live', () => {
    it('given_anUpdateOfTheAssetOnTheStream_should_showTheNewNameWithoutRemounting', async () => {
      // Arrange
      const callsBefore = await renderLivePage();
      const heading = title('Web server');
      mocks.getAssetOverview.mockResolvedValue(asset({ asset_name: 'Renamed server' }));

      // Act
      assetMessage(DATA_UPDATE_SUCCESS);
      await act(() => vi.advanceTimersByTimeAsync(250));

      // Assert: the same heading, updated in place
      await waitFor(() => expect(heading).toHaveTextContent('Renamed server'));
      expect(heading).toBeInTheDocument();
      expect(mocks.getAssetOverview).toHaveBeenCalledTimes(callsBefore + 1);
      expect(screen.queryByTestId('loader')).not.toBeInTheDocument();
    });

    it('given_aNewAgentOfTheAssetOnTheStream_should_refetchTheAsset', async () => {
      // Arrange: the agent is not shown yet, its asset is
      const callsBefore = await renderLivePage();
      mocks.getAssetOverview.mockResolvedValue(asset({ asset_agents: [agent('agent-1'), agent('agent-2')] }));

      // Act
      streamMessage({
        event_type: DATA_FETCH_SUCCESS,
        attribute_id: 'agent_id',
        attribute_schema: 'agents',
        instance: {
          agent_id: 'agent-2',
          agent_asset: ASSET_ID,
        },
      });
      await act(() => vi.advanceTimersByTimeAsync(250));

      // Assert
      await waitFor(() => expect(screen.getByTestId('agents')).toHaveTextContent('agent-1,agent-2'));
      expect(mocks.getAssetOverview).toHaveBeenCalledTimes(callsBefore + 1);
    });

    it('given_aDeleteOfTheAssetOnTheStream_should_showTheDeletedState', async () => {
      // Arrange
      await renderLivePage();
      mocks.getAssetOverview.mockRejectedValue({ status: 404 });

      // Act
      assetMessage(DATA_DELETE_SUCCESS);
      await act(() => vi.advanceTimersByTimeAsync(250));

      // Assert
      await waitFor(() => expect(screen.getByText('This item does not exist or you are not allowed to view it.')).toBeInTheDocument());
      expect(screen.queryByRole('heading', { level: 1 })).not.toBeInTheDocument();
    });

    it('given_anUpdateFromThePopover_should_refetchAtOnce', async () => {
      // Arrange
      const callsBefore = await renderLivePage();
      mocks.getAssetOverview.mockResolvedValue(asset({ asset_name: 'Renamed server' }));

      // Act: no wait for the batch window
      let refreshed: unknown;
      act(() => {
        refreshed = mocks.onUpdate?.(asset({ asset_name: 'Renamed server' }));
      });
      const callsRightAfter = mocks.getAssetOverview.mock.calls.length;

      // Assert
      expect(callsRightAfter).toBe(callsBefore + 1);
      await act(() => refreshed as Promise<void>);
      await waitFor(() => expect(title('Renamed server')).toBeInTheDocument());
    });
  });
});
