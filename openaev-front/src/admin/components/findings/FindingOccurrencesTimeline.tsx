import { Box, Paper, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { Fragment, useContext, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router';

import { initSorting, type Page } from '../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import Empty from '../../../components/Empty';
import { useFormatter } from '../../../components/i18n';
import Loader from '../../../components/Loader';
import { ATOMIC_BASE_URL, SIMULATION_BASE_URL } from '../../../constants/BaseUrls';
import type { Finding, RelatedFindingOutput, SearchPaginationInput } from '../../../utils/api-types';
import { AbilityContext } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { buildOccurrencesFilter, occurrenceTargets } from './FindingOccurrencesList';

// Maximum markers stacked per time bucket before collapsing into a "+N" badge.
const MAX_DOTS_PER_GROUP = 4;
// Horizontal padding (in %) so the first / last markers never touch the edges.
const EDGE_PADDING = 6;
// Bucket granularity (in % of the axis) used to group near-simultaneous detections.
const BUCKET_SIZE = 2.5;
// Timeline is a visualization, not a pager: fetch a generous fixed window and
// say so explicitly when a pathological finding has even more occurrences.
const TIMELINE_FETCH_SIZE = 500;

interface Props {
  searchFindings: (input: SearchPaginationInput) => Promise<{ data: Page<RelatedFindingOutput> }>;
  finding: Pick<Finding, 'finding_type' | 'finding_value'>;
}

// Horizontal occurrence timeline: every detection of this finding is plotted on a single time
// axis at the moment it was last seen in its inject, so re-detections (e.g. a recurring atomic
// testing) read as a dense progression and one-shot findings as a single milestone. Mirrors the
// simulation ExecutionFlowStrip geometry so the two strips feel like the same product.
const FindingOccurrencesTimeline = ({ searchFindings, finding }: Props) => {
  const theme = useTheme();
  const { t, fndt } = useFormatter();
  const ability = useContext(AbilityContext);

  const [loading, setLoading] = useState<boolean>(true);
  const [occurrences, setOccurrences] = useState<RelatedFindingOutput[]>([]);
  const [total, setTotal] = useState<number>(0);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    // DESC so that, when the window overflows, it keeps the MOST RECENT occurrences
    // (the markers are re-sorted ascending client-side for plotting).
    searchFindings(buildSearchPagination({
      page: 0,
      size: TIMELINE_FETCH_SIZE,
      sorts: initSorting('finding_updated_at', 'DESC'),
      filterGroup: buildOccurrencesFilter(finding),
    }))
      .then((res) => {
        if (cancelled) return;
        setOccurrences(res.data.content ?? []);
        setTotal(res.data.totalElements ?? 0);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [finding.finding_type, finding.finding_value]);

  const seenAt = (occurrence: RelatedFindingOutput) => new Date(occurrence.finding_updated_at).getTime();

  const sorted = useMemo(
    () => [...occurrences].sort((a, b) => seenAt(a) - seenAt(b)),
    [occurrences],
  );

  const start = sorted.length ? seenAt(sorted[0]) : 0;
  const end = sorted.length ? seenAt(sorted[sorted.length - 1]) : 0;
  const span = end - start;
  const positionFor = (timestamp: number) => (span === 0
    ? 50
    : EDGE_PADDING + ((timestamp - start) / span) * (100 - 2 * EDGE_PADDING));

  // Group near-simultaneous detections into a single stacked marker.
  const groups = useMemo(() => {
    const map = new Map<number, RelatedFindingOutput[]>();
    sorted.forEach((occurrence) => {
      const bucket = Math.round(positionFor(seenAt(occurrence)) / BUCKET_SIZE) * BUCKET_SIZE;
      map.set(bucket, [...(map.get(bucket) ?? []), occurrence]);
    });
    return [...map.entries()];
    // positionFor derives from sorted; re-grouping on sorted covers it.
  }, [sorted]);

  // Same pivot rules as FindingContextLink: atomic injects link to the atomic testing page,
  // simulation injects to the inject inside its simulation - only when the user may access it.
  const occurrenceLink = (occurrence: RelatedFindingOutput): string | undefined => {
    const injectId = occurrence.finding_inject?.inject_id;
    if (!injectId) return undefined;
    const simulationId = occurrence.finding_simulation?.exercise_id;
    if (!simulationId) {
      const allowed = ability.can(ACTIONS.ACCESS, SUBJECTS.ASSESSMENT)
        || ability.can(ACTIONS.ACCESS, SUBJECTS.RESOURCE, injectId);
      return allowed ? `${ATOMIC_BASE_URL}/${injectId}` : undefined;
    }
    const allowed = ability.can(ACTIONS.ACCESS, SUBJECTS.RESOURCE, simulationId);
    return allowed ? `${SIMULATION_BASE_URL}/${simulationId}/injects/${injectId}` : undefined;
  };

  if (loading) {
    return (
      <Paper
        variant="outlined"
        sx={{
          borderRadius: 1,
          height: 180,
        }}
      >
        <Loader variant="inElement" />
      </Paper>
    );
  }

  if (sorted.length === 0) {
    return (
      <Paper
        variant="outlined"
        sx={{
          borderRadius: 1,
          padding: 2,
        }}
      >
        <Empty message={t('No occurrence of this finding yet.')} />
      </Paper>
    );
  }

  const axisColor = alpha(theme.palette.text.primary, 0.15);
  const labelSx = {
    position: 'absolute' as const,
    bottom: 0,
    transform: 'translateX(-50%)',
    fontFamily: 'Consolas, monaco, monospace',
    fontSize: 11,
    color: 'text.secondary',
    whiteSpace: 'nowrap' as const,
  };
  // Only render the middle label when it will not repeat the edge labels.
  const showMidLabel = span >= 10 * 60_000;

  const dot = (occurrence: RelatedFindingOutput) => {
    const link = occurrenceLink(occurrence);
    const targetNames = occurrenceTargets(occurrence)
      .map(target => target.target_name)
      .filter(Boolean)
      .slice(0, 5);
    const marker = (
      <Box
        sx={{
          width: 10,
          height: 10,
          borderRadius: '50%',
          backgroundColor: alpha(theme.palette.primary.main, 0.85),
          border: `2px solid ${theme.palette.background.paper}`,
          boxShadow: `0 0 0 1px ${alpha(theme.palette.primary.main, 0.4)}`,
          ...(link
            ? {
                'cursor': 'pointer',
                'transition': 'transform 120ms',
                '&:hover': { transform: 'scale(1.35)' },
              }
            : {}),
        }}
      />
    );
    return (
      <Tooltip
        key={occurrence.finding_id}
        title={(
          <Fragment>
            {occurrence.finding_inject?.inject_title ?? '-'}
            <br />
            <span style={{
              display: 'block',
              textAlign: 'center',
              fontWeight: 'bold',
            }}
            >
              {fndt(occurrence.finding_updated_at)}
            </span>
            {targetNames.length > 0 && (
              <span style={{
                display: 'block',
                textAlign: 'center',
                opacity: 0.8,
              }}
              >
                {targetNames.join(', ')}
              </span>
            )}
          </Fragment>
        )}
      >
        {link
          ? <Link to={link} style={{ display: 'flex' }}>{marker}</Link>
          : marker}
      </Tooltip>
    );
  };

  return (
    <Paper
      variant="outlined"
      sx={{
        borderRadius: 1,
        padding: theme.spacing(2, 2, 1, 2),
      }}
    >
      <Box sx={{
        position: 'relative',
        height: 132,
      }}
      >
        {/* Time axis */}
        <Box sx={{
          position: 'absolute',
          left: 0,
          right: 0,
          bottom: 30,
          height: '1px',
          backgroundColor: axisColor,
        }}
        />
        {/* Occurrence markers */}
        {groups.map(([position, groupOccurrences]) => {
          const visible = groupOccurrences.slice(0, MAX_DOTS_PER_GROUP);
          const overflow = groupOccurrences.length - visible.length;
          return (
            <Box
              key={position}
              sx={{
                position: 'absolute',
                bottom: 26,
                left: `${position}%`,
                transform: 'translateX(-50%)',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: '3px',
              }}
            >
              {overflow > 0 && (
                <Tooltip title={t('{count} more occurrences in this time bucket', { count: overflow })}>
                  <Typography sx={{
                    fontSize: 10,
                    fontWeight: 600,
                    color: 'text.secondary',
                  }}
                  >
                    {`+${overflow}`}
                  </Typography>
                </Tooltip>
              )}
              {visible.map(occurrence => dot(occurrence))}
              {/* Tick crossing the axis */}
              <Box sx={{
                width: '2px',
                height: 10,
                borderRadius: 1,
                backgroundColor: theme.palette.primary.main,
              }}
              />
            </Box>
          );
        })}
        {/* Time labels */}
        {span === 0 ? (
          <Typography sx={{
            ...labelSx,
            left: '50%',
          }}
          >
            {fndt(new Date(start))}
          </Typography>
        ) : (
          <>
            <Typography sx={{
              ...labelSx,
              left: `${EDGE_PADDING}%`,
            }}
            >
              {fndt(new Date(start))}
            </Typography>
            {showMidLabel && (
              <Typography sx={{
                ...labelSx,
                left: '50%',
              }}
              >
                {fndt(new Date(start + span / 2))}
              </Typography>
            )}
            <Typography sx={{
              ...labelSx,
              left: `${100 - EDGE_PADDING}%`,
            }}
            >
              {fndt(new Date(end))}
            </Typography>
          </>
        )}
      </Box>
      {total > sorted.length && (
        <Typography
          variant="caption"
          color="text.secondary"
          sx={{
            display: 'block',
            marginTop: 1,
          }}
        >
          {t('Showing the {shown} most recent of {total} occurrences.', {
            shown: sorted.length,
            total,
          })}
        </Typography>
      )}
    </Paper>
  );
};

export default FindingOccurrencesTimeline;
