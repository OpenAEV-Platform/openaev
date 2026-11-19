import { Alert } from '@filigran/design-system';
import { Box } from '@mui/material';
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';

import { fetchGraphNeighbourhood } from '../../../actions/graph/graph-actions';
import { useFormatter } from '../../../components/i18n';
import Loader from '../../../components/Loader';
import { EMBEDDED_GRAPH_MIN_HEIGHT, embeddedGraphHeight } from './entity-graph-height';
import { MINI_CARD_MIN_HEIGHT } from './graph-layout';
import { graphHref, type GraphNodeType, type GraphOutput } from './graph-model';
import GraphExplorer from './GraphExplorer';

interface Props {
  type: GraphNodeType;
  entityId: string;
  /** embedded: the entity's Graph tab; mini: the read-only card of its Overview. */
  variant: 'embedded' | 'mini';
  height?: number | string;
}

/**
 * The shared graph component (#9388) scoped to one entity: the Graph tab of an entity page and the
 * mini graph card of its Overview. It fetches the entity's first ring only, and only once the
 * component is visible (a card below the fold costs nothing until it scrolls in). The full Graph
 * page is one click away and keeps the focus.
 */
const EntityGraph = ({ type, entityId, variant, height }: Props) => {
  const { t } = useFormatter();
  const navigate = useNavigate();
  const anchor = useRef<HTMLDivElement>(null);
  const [visible, setVisible] = useState(false);
  const [slice, setSlice] = useState<GraphOutput | null>(null);
  const [error, setError] = useState(false);
  const [fillHeight, setFillHeight] = useState<number | null>(null);

  useLayoutEffect(() => {
    if (variant !== 'embedded' || height !== undefined) {
      return undefined;
    }
    const measure = () => {
      const element = anchor.current;
      if (!element) {
        return;
      }
      const top = element.getBoundingClientRect().top + window.scrollY;
      setFillHeight(embeddedGraphHeight(top, window.innerHeight));
    };
    measure();
    window.addEventListener('resize', measure);
    return () => window.removeEventListener('resize', measure);
  }, [variant, height]);

  useEffect(() => {
    const element = anchor.current;
    if (!element || typeof IntersectionObserver === 'undefined') {
      setVisible(true);
      return undefined;
    }
    const observer = new IntersectionObserver((entries) => {
      if (entries.some(entry => entry.isIntersecting)) {
        setVisible(true);
        observer.disconnect();
      }
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (!visible) {
      return;
    }
    setSlice(null);
    setError(false);
    fetchGraphNeighbourhood(type, entityId, [], variant === 'mini' ? 20 : 50)
      .then(setSlice)
      .catch(() => setError(true));
  }, [visible, type, entityId, variant]);

  // The mini card takes the height its layout needs (at least MINI_CARD_MIN_HEIGHT), so its top and
  // bottom rows are never cut (#9448, UX round 2 B3); the Graph tab fills the window.
  const resolvedHeight = height ?? (variant === 'mini' ? 'auto' : (fillHeight ?? EMBEDDED_GRAPH_MIN_HEIGHT));

  return (
    <Box
      ref={anchor}
      sx={{
        height: resolvedHeight,
        minHeight: variant === 'mini' ? MINI_CARD_MIN_HEIGHT : undefined,
      }}
    >
      {error && <Alert severity="error" title={t('The graph could not be loaded')} />}
      {!error && !slice && <Loader variant="inElement" />}
      {slice && (
        <GraphExplorer
          initial={slice}
          variant={variant}
          height="100%"
          onOpen={() => navigate(graphHref(type, entityId))}
        />
      )}
    </Box>
  );
};

export default EntityGraph;
