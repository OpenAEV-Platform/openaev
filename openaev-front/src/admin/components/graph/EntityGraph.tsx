import { Alert, Box } from '@mui/material';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';

import { fetchGraphNeighbourhood } from '../../../actions/graph/graph-actions';
import { useFormatter } from '../../../components/i18n';
import Loader from '../../../components/Loader';
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

  const resolvedHeight = height ?? (variant === 'mini' ? 240 : 'calc(100dvh - 360px)');

  return (
    <Box ref={anchor} sx={{ height: resolvedHeight }}>
      {error && <Alert severity="error">{t('The graph could not be loaded')}</Alert>}
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
