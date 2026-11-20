import { Alert, Button } from '@filigran/design-system';
import { Box, Stack } from '@mui/material';
import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';

import {
  fetchGraphNeighbourhood,
  fetchGraphOverview,
  fetchGraphStartingPoints,
  fetchGraphStory,
} from '../../../actions/graph/graph-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import { useFormatter } from '../../../components/i18n';
import Loader from '../../../components/Loader';
import { type GraphOverviewGroupOutput, type GraphOverviewOutput, type GraphStartingPointOutput } from '../../../utils/api-types';
import { type LandingView, landingView, parseLandingView, storyHasContent } from './graph-landing';
import { type GraphLayout } from './graph-layout';
import { type GraphNodeType, type GraphOutput, nodeTypeParam, parseNodeType } from './graph-model';
import { encodeFocus } from './graph-state';
import GraphExplorer from './GraphExplorer';
import GraphOverview from './GraphOverview';
import GraphStartingPoints from './GraphStartingPoints';

const LAYOUTS: GraphLayout[] = ['force', 'hierarchical', 'radial'];

/** The landing story never fits below this zoom: its labels stay legible at the first frame. */
const STORY_MIN_ZOOM = 0.75;

const RAIL_WIDTH = 300;

/**
 * The Graph page (#9388, landing redesigned in #9447). Without a focus it opens on ONE of two views,
 * chosen by the data and kept in the URL (?view=story|overview):
 * - the top exposure story: the single most critical chain of the estate (the attack path with the
 *   most failed steps, else the most exposed asset with its failed injects, findings, groups and
 *   identity), one connected subgraph of about 30 nodes, never a scatter of unrelated clusters;
 * - the estate overview: asset groups as bubbles sized by asset count and coloured by failure rate,
 *   a few dozen whatever the estate size; the default when nothing failed yet.
 * A starting-points rail beside the canvas recentres the graph on a top entity in one click. With
 * ?focus=<type>:<id> it opens on that entity's first ring, which every "Explore in graph" button links to.
 */
const Graph = () => {
  const { t } = useFormatter();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const focusParam = params.get('focus');
  const viewParam = parseLandingView(params.get('view'));
  const layoutParam = params.get('layout') as GraphLayout | null;
  const isLanding = !focusParam;

  const [slice, setSlice] = useState<GraphOutput | null>(null);
  const [story, setStory] = useState<GraphOutput | null>(null);
  const [overview, setOverview] = useState<GraphOverviewOutput | null>(null);
  const [points, setPoints] = useState<GraphStartingPointOutput[] | null>(null);
  const [error, setError] = useState(false);
  // The focus the explorer itself wrote in the URL (an expand in place): the page keeps the graph on
  // screen instead of loading that focus again, which would remount the explorer and lose the
  // investigation (Sam, 19 Nov 2026: pivoting is expanding, never a full re-render).
  const ownFocus = useRef<string | null>(null);
  // The explorer opened on the landing story keeps the story's framing once the user expands from it.
  const [fromStory, setFromStory] = useState(false);

  // The rail is the same on every view: loaded once per page visit.
  useEffect(() => {
    fetchGraphStartingPoints()
      .then(output => setPoints(output.starting_points))
      .catch(() => setPoints([]));
  }, []);

  // A focused view: the entity's first ring.
  useEffect(() => {
    if (!focusParam) {
      ownFocus.current = null;
      setFromStory(false);
      return;
    }
    if (focusParam === ownFocus.current) {
      return;
    }
    ownFocus.current = null;
    setFromStory(false);
    setSlice(null);
    setError(false);
    const [rawType, ...rest] = focusParam.split(':');
    const type = parseNodeType(rawType);
    const id = rest.join(':');
    if (!type || !id) {
      setError(true);
      return;
    }
    fetchGraphNeighbourhood(type, decodeURIComponent(id)).then(setSlice).catch(() => setError(true));
  }, [focusParam]);

  // The landing: the story first (it decides the default view), the overview when it is shown.
  useEffect(() => {
    if (!isLanding || story) {
      return;
    }
    setError(false);
    fetchGraphStory().then(setStory).catch(() => setError(true));
  }, [isLanding, story]);

  const view: LandingView | null = story ? landingView(viewParam, story) : null;

  useEffect(() => {
    if (!isLanding || view !== 'overview' || overview) {
      return;
    }
    fetchGraphOverview().then(setOverview).catch(() => setError(true));
  }, [isLanding, view, overview]);

  const setParam = (changes: Record<string, string | null>) => {
    const next = new URLSearchParams(params);
    Object.entries(changes).forEach(([key, value]) => {
      if (value) {
        next.set(key, value);
      } else {
        next.delete(key);
      }
    });
    setParams(next, { replace: true });
  };

  const onFocusChange = (focus: string | null) => {
    const encoded = encodeFocus(focus) ?? null;
    if (!encoded) {
      return;
    }
    ownFocus.current = encoded;
    // An expand from the landing story: the story becomes the explored slice, same props, no remount.
    if (isLanding && story) {
      setSlice(story);
      setFromStory(true);
    }
    setParam({ focus: encoded });
  };
  const onLayoutChange = (layout: GraphLayout) => setParam({ layout });
  const focusOn = (type: GraphNodeType, entityId: string) => setParams((current) => {
    const next = new URLSearchParams(current);
    next.set('focus', `${nodeTypeParam(type)}:${encodeURIComponent(entityId)}`);
    return next;
  });
  const showView = (next: LandingView) => setParam({
    view: next,
    focus: null,
  });
  const backToLanding = () => setParam({ focus: null });

  const onSelectPoint = (point: GraphStartingPointOutput) => focusOn(point.point_node_type, point.point_entity_id);
  const onUnfoldGroup = (group: GraphOverviewGroupOutput) => focusOn('ASSET_GROUP', group.group_id);

  const explorer = (initial: GraphOutput, fitMinZoom?: number) => (
    <GraphExplorer
      initial={initial}
      variant="page"
      onFocusChange={onFocusChange}
      initialLayout={layoutParam && LAYOUTS.includes(layoutParam) ? layoutParam : 'force'}
      onLayoutChange={onLayoutChange}
      fitMinZoom={fitMinZoom}
      selectFocus={fitMinZoom === undefined}
    />
  );

  let canvas = <Loader variant="inElement" />;
  if (error) {
    canvas = <Alert severity="error" title={t('The graph could not be loaded')} />;
  } else if (!isLanding && slice) {
    canvas = explorer(slice, fromStory ? STORY_MIN_ZOOM : undefined);
  } else if (isLanding && view === 'story' && story) {
    canvas = storyHasContent(story)
      ? explorer(story, STORY_MIN_ZOOM)
      : <Alert severity="info" title={t('No failed expectation yet: there is no exposure story to tell. The estate overview describes the estate.')} />;
  } else if (isLanding && view === 'overview' && overview) {
    canvas = (
      <GraphOverview
        overview={overview}
        onUnfoldGroup={onUnfoldGroup}
        onOpenUngrouped={() => navigate('/admin/assets')}
        onOpenSimulation={id => focusOn('SIMULATION', id)}
      />
    );
  }

  return (
    <Stack spacing={1.5} sx={{ height: 'calc(100dvh - 140px)' }}>
      <Breadcrumbs
        variant="list"
        elements={[{
          label: t('Graph'),
          current: true,
        }]}
      />
      <div className="flex flex-wrap items-center gap-2">
        {isLanding ? (
          <>
            <Button type="button" size="sm" priority={view === 'story' ? 'primary' : 'secondary'} aria-pressed={view === 'story'} onClick={() => showView('story')}>
              {t('Top exposure story')}
            </Button>
            <Button type="button" size="sm" priority={view === 'overview' ? 'primary' : 'secondary'} aria-pressed={view === 'overview'} onClick={() => showView('overview')}>
              {t('Estate overview')}
            </Button>
          </>
        ) : (
          <Button type="button" size="sm" priority="tertiary" onClick={backToLanding}>{t('Back to the landing')}</Button>
        )}
      </div>
      {isLanding && view && (
        <Alert
          severity="info"
          title={view === 'story' ? t('Top exposure story') : t('Estate overview')}
          description={view === 'story'
            ? t('The most critical chain of the estate: the attack path with the most failed steps, or the asset with the most failed expectations, with its findings, asset groups and identity. Double-click a node to expand it.')
            : t('Asset groups sized by asset count and coloured by failure rate, with the simulations that target them. Double-click a group to unfold its assets.')}
        />
      )}
      <Box sx={{
        flexGrow: 1,
        minHeight: 0,
        display: 'flex',
        gap: 1.5,
      }}
      >
        <Box sx={{
          flexGrow: 1,
          minWidth: 0,
        }}
        >
          {canvas}
        </Box>
        {/* Once the user explores, the canvas takes the whole width, as an OpenCTI investigation does;
            the starting points come back with Back to the landing. */}
        {isLanding && (
          <Box sx={{
            width: RAIL_WIDTH,
            flexShrink: 0,
            minHeight: 0,
          }}
          >
            {points ? <GraphStartingPoints points={points} current={focusParam} onSelect={onSelectPoint} /> : <Loader variant="inElement" />}
          </Box>
        )}
      </Box>
    </Stack>
  );
};

export default Graph;
