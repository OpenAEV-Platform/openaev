import { Chip, Paper, Text, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { Box } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type KeyboardEvent, useMemo } from 'react';

import { useFormatter } from '../../../components/i18n';
import { type GraphOverviewGroupOutput, type GraphOverviewOutput } from '../../../utils/api-types';
import NODE_ICON from './graph-icons';
import {
  bubbleDiameter,
  failurePercent,
  groupResult,
  linksByGroup,
  sortOverviewGroups,
} from './graph-landing';
import { type GraphResult } from './graph-model';

interface GraphOverviewProps {
  overview: GraphOverviewOutput;
  /** A real asset group was double-clicked (or Enter): unfold it in the graph. */
  onUnfoldGroup: (group: GraphOverviewGroupOutput) => void;
  /** The ungrouped bucket was opened: it has no graph node, the host decides where it goes. */
  onOpenUngrouped: () => void;
  onOpenSimulation: (simulationId: string) => void;
}

/**
 * The estate overview of the Graph landing (#9447): asset groups as bubbles sized by asset count and
 * coloured by failure rate, the simulations that target each group under it. A few dozen bubbles
 * whatever the estate size (the API caps at 40 groups plus the ungrouped bucket): counts, not nodes,
 * so 100K assets read as clearly as 100. Double-click a bubble to unfold its assets in the graph,
 * where the first ring is capped and "Load more" pages the rest.
 */
const GraphOverview = ({ overview, onUnfoldGroup, onOpenUngrouped, onOpenSimulation }: GraphOverviewProps) => {
  const theme = useTheme();
  const { t, n } = useFormatter();
  const groups = useMemo(() => sortOverviewGroups(overview.overview_groups), [overview]);
  const links = useMemo(() => linksByGroup(overview.overview_links), [overview]);
  const maxCount = useMemo(() => Math.max(0, ...groups.map(g => g.group_asset_count ?? 0)), [groups]);
  const GroupIcon = NODE_ICON.ASSET_GROUP;

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

  return (
    <Paper
      elevation={1}
      padding={16}
      style={{
        height: '100%',
        overflow: 'auto',
      }}
    >
      <div className="flex flex-wrap items-center gap-4">
        <Text variant="content-base-bold">{`${n(overview.overview_asset_count ?? 0)} ${t('Assets')}`}</Text>
        <span className="flex flex-wrap items-center gap-2" aria-label={t('Legend')}>
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
            </span>
          ))}
        </span>
        {overview.overview_truncated && (
          <Text variant="content-caption" className="text-default-secondary">{t('Only the largest asset groups are shown')}</Text>
        )}
      </div>
      <div className="mt-4 flex flex-wrap items-start justify-center gap-6" data-testid="graph-overview">
        {groups.map((group) => {
          const result = groupResult(group);
          const ring = colour(result);
          const size = bubbleDiameter(group.group_asset_count ?? 0, maxCount);
          const label = group.group_ungrouped ? t('Ungrouped assets') : group.group_name;
          const groupLinks = links.get(group.group_id) ?? [];
          return (
            <div key={group.group_id} className="flex flex-col items-center gap-2" style={{ width: Math.max(size, 140) }}>
              <Tooltip>
                <TooltipTrigger asChild>
                  <Box
                    role="button"
                    tabIndex={0}
                    aria-label={label}
                    data-testid={`graph-overview-group-${group.group_id}`}
                    onDoubleClick={() => open(group)}
                    onKeyDown={onKey(group)}
                    sx={{
                      'width': size,
                      'height': size,
                      'borderRadius': '50%',
                      'display': 'flex',
                      'flexDirection': 'column',
                      'alignItems': 'center',
                      'justifyContent': 'center',
                      'gap': 0.5,
                      'padding': 1,
                      'cursor': 'pointer',
                      'color': theme.palette.text.primary,
                      'backgroundColor': alpha(ring, 0.12),
                      'border': `2px ${group.group_dynamic ? 'dashed' : 'solid'} ${ring}`,
                      'transition': 'transform 0.2s, box-shadow 0.2s',
                      '&:hover, &:focus-visible': {
                        transform: 'scale(1.04)',
                        boxShadow: `0 0 12px ${alpha(ring, 0.5)}`,
                        outline: 'none',
                      },
                      '@media (prefers-reduced-motion: reduce)': {
                        'transition': 'none',
                        '&:hover, &:focus-visible': { transform: 'none' },
                      },
                    }}
                  >
                    <GroupIcon />
                    <Text variant="content-base-bold">{n(group.group_asset_count ?? 0)}</Text>
                    {result !== null && (
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
              <Text
                variant="content-base"
                style={{
                  maxWidth: '100%',
                  textAlign: 'center',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
              >
                {label}
              </Text>
              {groupLinks.length > 0 && (
                <div className="flex flex-col items-center gap-1" style={{ maxWidth: '100%' }}>
                  {groupLinks.map(link => (
                    <Chip
                      key={link.link_simulation_id}
                      severity={link.link_result === 'FAILED' ? 'critical' : 'neutral'}
                      label={link.link_simulation_name}
                      onClick={() => onOpenSimulation(link.link_simulation_id)}
                    />
                  ))}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </Paper>
  );
};

export default GraphOverview;
