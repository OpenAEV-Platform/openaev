import { Chip, Paper, Text, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { InfoOutlined } from '@mui/icons-material';
import { Box } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type KeyboardEvent, useEffect, useMemo, useRef, useState } from 'react';

import { useFormatter } from '../../../components/i18n';
import { type GraphOverviewGroupOutput, type GraphOverviewOutput } from '../../../utils/api-types';
import NODE_ICON from './graph-icons';
import {
  failurePercent,
  groupResult,
  linksByGroup,
  sortOverviewGroups,
} from './graph-landing';
import { type GraphResult } from './graph-model';
import { packBubbles } from './graph-pack';

interface GraphOverviewProps {
  overview: GraphOverviewOutput;
  /** A real asset group was double-clicked (or Enter): unfold it in the graph. */
  onUnfoldGroup: (group: GraphOverviewGroupOutput) => void;
  /** The ungrouped bucket was opened: it has no graph node, the host decides where it goes. */
  onOpenUngrouped: () => void;
  onOpenSimulation: (simulationId: string) => void;
}

/** Below this radius a bubble shows its count only; its name is in the tooltip and the accessible label. */
const LABEL_MIN_RADIUS = 52;

/**
 * The estate overview of the Graph landing (#9447), reworked as the Exposure map (#9572): the canvas
 * at zoom 0, the asset groups packed as bubbles that fill the whole canvas, area by asset count,
 * coloured by coverage (failure rate, or grey when never tested). A click selects a group and lists
 * the simulations that target it in a floating card; a double-click (or Enter) unfolds its assets in
 * the graph. A few dozen bubbles whatever the estate size (the API caps at 40 groups plus the
 * ungrouped bucket): counts, not nodes, so 100K assets read as clearly as 100.
 */
const GraphOverview = ({ overview, onUnfoldGroup, onOpenUngrouped, onOpenSimulation }: GraphOverviewProps) => {
  const theme = useTheme();
  const { t, n } = useFormatter();
  const groups = useMemo(() => sortOverviewGroups(overview.overview_groups), [overview]);
  const links = useMemo(() => linksByGroup(overview.overview_links), [overview]);
  const GroupIcon = NODE_ICON.ASSET_GROUP;
  const canvasRef = useRef<HTMLDivElement>(null);
  const [size, setSize] = useState({
    width: 0,
    height: 0,
  });
  const [selectedId, setSelectedId] = useState<string | null>(null);

  useEffect(() => {
    const element = canvasRef.current;
    if (!element) {
      return undefined;
    }
    const measure = () => setSize(previous => (
      previous.width === element.clientWidth && previous.height === element.clientHeight
        ? previous
        : {
            width: element.clientWidth,
            height: element.clientHeight,
          }
    ));
    measure();
    if (typeof ResizeObserver === 'undefined') {
      return undefined;
    }
    const observer = new ResizeObserver(measure);
    observer.observe(element);
    return () => observer.disconnect();
  }, [groups.length]);

  const bubbles = useMemo(() => {
    const packed = packBubbles(groups.map(g => ({
      id: g.group_id,
      weight: g.group_asset_count ?? 0,
    })), size.width, size.height);
    return new Map(packed.map(b => [b.id, b]));
  }, [groups, size]);

  const colour = (result: GraphResult | null) => {
    switch (result) {
      case 'FAILED':
        return theme.palette.error.main;
      case 'PENDING':
        return theme.palette.warning.main;
      case 'SUCCESS':
        return theme.palette.success.main;
      default:
        return theme.palette.text.disabled;
    }
  };

  const open = (group: GraphOverviewGroupOutput) => (group.group_ungrouped ? onOpenUngrouped() : onUnfoldGroup(group));
  const onKey = (group: GraphOverviewGroupOutput) => (event: KeyboardEvent) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      open(group);
    }
  };

  if (groups.length === 0) {
    return (
      <Paper elevation={1} padding={24}>
        <Text variant="content-base">{t('No asset yet: add assets or asset groups to see the estate overview.')}</Text>
      </Paper>
    );
  }

  const selected = groups.find(g => g.group_id === selectedId) ?? null;
  const selectedLinks = selected ? links.get(selected.group_id) ?? [] : [];
  const notTestedHelp = t('Grey means never tested: no simulation has scored an expectation on these assets yet. It does not mean safe.');

  return (
    <Paper
      elevation={1}
      padding={0}
      style={{
        height: '100%',
        position: 'relative',
        overflow: 'hidden',
      }}
    >
      {/* The legend floats over the canvas, top left, so the bubbles get the whole height. */}
      <div
        className="flex flex-wrap items-center gap-3"
        style={{
          position: 'absolute',
          top: 8,
          left: 12,
          right: 12,
          zIndex: 2,
          pointerEvents: 'none',
        }}
      >
        <Text variant="content-base-bold">{`${n(overview.overview_asset_count ?? 0)} ${(overview.overview_asset_count ?? 0) === 1 ? t('Asset') : t('Assets')}`}</Text>
        <span className="flex flex-wrap items-center gap-2" aria-label={t('Legend')} style={{ pointerEvents: 'auto' }}>
          {([['FAILED', 'Failure rate 20% or more'], ['PENDING', 'Some failures'], ['SUCCESS', 'No failure'], [null, 'Not tested yet']] as [GraphResult | null, string][]).map(([result, label]) => (
            <span key={label} className="flex items-center gap-1">
              <Box
                component="span"
                sx={{
                  width: 10,
                  height: 10,
                  borderRadius: '50%',
                  backgroundColor: colour(result),
                }}
              />
              <Text variant="content-caption">{t(label)}</Text>
              {result === null && (
                <Tooltip>
                  <TooltipTrigger asChild>
                    <span
                      tabIndex={0}
                      role="img"
                      aria-label={notTestedHelp}
                      data-testid="graph-overview-not-tested-help"
                      className="flex items-center"
                    >
                      <InfoOutlined sx={{ fontSize: 14 }} />
                    </span>
                  </TooltipTrigger>
                  <TooltipContent>{notTestedHelp}</TooltipContent>
                </Tooltip>
              )}
            </span>
          ))}
        </span>
        {overview.overview_truncated && (
          <Text variant="content-caption" className="text-default-secondary">{t('Only the largest asset groups are shown')}</Text>
        )}
      </div>
      <div
        ref={canvasRef}
        data-testid="graph-overview"
        role="group"
        aria-label={t('Estate overview')}
        onClick={(event) => {
          if (event.target === event.currentTarget) {
            setSelectedId(null);
          }
        }}
        style={{
          position: 'absolute',
          top: 36,
          left: 0,
          right: 0,
          bottom: 0,
        }}
      >
        {groups.map((group) => {
          const bubble = bubbles.get(group.group_id);
          if (!bubble) {
            return null;
          }
          const result = groupResult(group);
          const ring = colour(result);
          const label = group.group_ungrouped ? t('Ungrouped assets') : group.group_name;
          const isSelected = group.group_id === selectedId;
          const diameter = bubble.r * 2;
          return (
            <Tooltip key={group.group_id}>
              <TooltipTrigger asChild>
                <Box
                  role="button"
                  tabIndex={0}
                  aria-label={label}
                  aria-pressed={isSelected}
                  data-testid={`graph-overview-group-${group.group_id}`}
                  onClick={() => setSelectedId(group.group_id)}
                  onFocus={() => setSelectedId(group.group_id)}
                  onDoubleClick={() => open(group)}
                  onKeyDown={onKey(group)}
                  sx={{
                    'position': 'absolute',
                    'left': bubble.x - bubble.r,
                    'top': bubble.y - bubble.r,
                    'width': diameter,
                    'height': diameter,
                    'borderRadius': '50%',
                    'display': 'flex',
                    'flexDirection': 'column',
                    'alignItems': 'center',
                    'justifyContent': 'center',
                    'gap': 0.25,
                    'padding': 1,
                    'overflow': 'hidden',
                    'cursor': 'pointer',
                    'color': theme.palette.text.primary,
                    'backgroundColor': alpha(ring, isSelected ? 0.24 : 0.12),
                    'border': `${isSelected ? 3 : 2}px ${group.group_dynamic ? 'dashed' : 'solid'} ${ring}`,
                    'transition': 'background-color 0.2s',
                    '&:hover, &:focus-visible': {
                      outline: `2px solid ${ring}`,
                      outlineOffset: 2,
                    },
                    '@media (prefers-reduced-motion: reduce)': { transition: 'none' },
                  }}
                >
                  {bubble.r >= LABEL_MIN_RADIUS && <GroupIcon />}
                  <Text variant="content-base-bold">{n(group.group_asset_count ?? 0)}</Text>
                  {bubble.r >= LABEL_MIN_RADIUS && (
                    <Text
                      variant="content-caption"
                      style={{
                        maxWidth: diameter * 0.7,
                        textAlign: 'center',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {label}
                    </Text>
                  )}
                  {result !== null && bubble.r >= LABEL_MIN_RADIUS && (
                    <Text variant="content-caption">{`${failurePercent(group.group_failure_rate)}%`}</Text>
                  )}
                </Box>
              </TooltipTrigger>
              <TooltipContent>
                {group.group_ungrouped
                  ? t('Assets in no asset group. Open the asset list to start from one of them.')
                  : `${label}. ${t('Failed expectations')}: ${n(group.group_failed_count ?? 0)} / ${n(group.group_scored_count ?? 0)}. ${t('Double-click to unfold its assets')}`}
              </TooltipContent>
            </Tooltip>
          );
        })}
      </div>
      {selected && selectedLinks.length > 0 && (
        <div
          data-testid="graph-overview-selection"
          style={{
            position: 'absolute',
            left: 12,
            bottom: 12,
            maxWidth: 320,
            zIndex: 2,
          }}
        >
          <Paper elevation={1} padding={12}>
            <Text variant="content-base-bold">{selected.group_ungrouped ? t('Ungrouped assets') : selected.group_name}</Text>
            <div className="mt-2 flex flex-wrap gap-1">
              {selectedLinks.map(link => (
                <Chip
                  key={link.link_simulation_id}
                  severity={link.link_result === 'FAILED' ? 'critical' : 'neutral'}
                  label={link.link_simulation_name}
                  onClick={() => onOpenSimulation(link.link_simulation_id)}
                />
              ))}
            </div>
          </Paper>
        </div>
      )}
    </Paper>
  );
};

export default GraphOverview;
