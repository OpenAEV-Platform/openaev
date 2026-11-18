import { Alert, Button } from '@filigran/design-system';
import { Box, Stack } from '@mui/material';
import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';

import { fetchGraphHotPaths, fetchGraphNeighbourhood } from '../../../actions/graph/graph-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import { useFormatter } from '../../../components/i18n';
import Loader from '../../../components/Loader';
import { type GraphLayout } from './graph-layout';
import { type GraphOutput, parseNodeType } from './graph-model';
import { encodeFocus } from './graph-state';
import GraphExplorer from './GraphExplorer';

const LAYOUTS: GraphLayout[] = ['force', 'hierarchical', 'radial'];

/**
 * The Graph page (#9388). Without a focus it opens on "exposure hot paths": the assets with the
 * most failed expectations, their asset groups, the injects that failed on them and their
 * findings, because the first question on this screen is "where am I exposed", not "show me
 * everything". With ?focus=<type>:<id> it opens on that entity's first ring, which is what every
 * "Explore in graph" button links to. The URL carries the focus and the layout, so a view is
 * shareable.
 */
const Graph = () => {
  const { t } = useFormatter();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const focusParam = params.get('focus');
  const layoutParam = params.get('layout') as GraphLayout | null;
  const [slice, setSlice] = useState<GraphOutput | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    setSlice(null);
    setError(false);
    const [rawType, ...rest] = (focusParam ?? '').split(':');
    const type = parseNodeType(rawType);
    const id = rest.join(':');
    const load = type && id ? fetchGraphNeighbourhood(type, decodeURIComponent(id)) : fetchGraphHotPaths();
    load.then(setSlice).catch(() => setError(true));
  }, [focusParam]);

  const onFocusChange = (focus: string | null) => {
    const next = new URLSearchParams(params);
    const encoded = encodeFocus(focus);
    if (encoded) {
      next.set('focus', encoded);
    } else {
      next.delete('focus');
    }
    setParams(next, { replace: true });
  };

  const onLayoutChange = (layout: GraphLayout) => {
    const next = new URLSearchParams(params);
    next.set('layout', layout);
    setParams(next, { replace: true });
  };

  const isLanding = !focusParam;

  return (
    <Stack spacing={1.5} sx={{ height: 'calc(100dvh - 140px)' }}>
      <Breadcrumbs
        variant="list"
        elements={[{
          label: t('Graph'),
          current: true,
        }]}
      />
      {isLanding && (
        <Alert
          severity="info"
          title={t('Exposure hot paths')}
          description={(
            <>
              {t('The assets with the most failed expectations, the injects that failed on them, their asset groups and their findings. Double-click a node to expand it, or start from another view.')}
              <span className="mt-2 flex flex-wrap gap-2">
                <Button type="button" priority="secondary" size="sm" onClick={() => navigate('/admin/simulations')}>{t('Start from a simulation')}</Button>
                <Button type="button" priority="secondary" size="sm" onClick={() => navigate('/admin/asset_groups')}>{t('Start from an asset group')}</Button>
                <Button type="button" priority="secondary" size="sm" onClick={() => navigate('/admin/findings')}>{t('Start from a finding')}</Button>
              </span>
            </>
          )}
        />
      )}
      {!isLanding && (
        <Box>
          <Button type="button" priority="tertiary" onClick={() => navigate('/admin/graph')}>{t('Back to exposure hot paths')}</Button>
        </Box>
      )}
      {error && <Alert severity="error" title={t('The graph could not be loaded')} />}
      {!error && !slice && <Loader variant="inElement" />}
      {slice && (
        <Box sx={{
          flexGrow: 1,
          minHeight: 0,
        }}
        >
          <GraphExplorer
            initial={slice}
            variant="page"
            onFocusChange={onFocusChange}
            initialLayout={layoutParam && LAYOUTS.includes(layoutParam) ? layoutParam : 'force'}
            onLayoutChange={onLayoutChange}
          />
        </Box>
      )}
    </Stack>
  );
};

export default Graph;
