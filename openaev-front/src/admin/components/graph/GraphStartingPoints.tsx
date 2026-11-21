import { IconButton, Paper, Text } from '@filigran/design-system';
import { ExpandLessOutlined } from '@mui/icons-material';
import { Box } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../components/i18n';
import { type GraphStartingPointOutput } from '../../../utils/api-types';
import NODE_ICON from './graph-icons';
import { groupStartingPoints, STARTING_POINT_KIND_LABEL } from './graph-landing';

interface GraphStartingPointsProps {
  points: GraphStartingPointOutput[];
  /** The point the graph is centred on, as "<type>:<id>", highlighted in the rail. */
  current?: string | null;
  onSelect: (point: GraphStartingPointOutput) => void;
  /** Folds the panel to its pill (the floating Start here panel of the landing, #9572 L3). */
  onCollapse?: () => void;
}

/**
 * The starting-points rail of the Graph landing (#9447): the top exposed assets, failing asset
 * groups, attack paths, findings, exposed persons and the latest simulation, five each at most.
 * A click recentres the graph on that entity. It replaces the three "Start from" buttons, which
 * sent the user away from the graph to a list. Since #9572 (landing L3) it floats over the canvas as
 * the Start here panel instead of taking a 300 px column beside it, and folds to a pill.
 */
const GraphStartingPoints = ({ points, current, onSelect, onCollapse }: GraphStartingPointsProps) => {
  const theme = useTheme();
  const { t, n } = useFormatter();
  const sections = groupStartingPoints(points);
  return (
    <Paper
      elevation={1}
      padding={16}
      style={{
        maxHeight: '100%',
        overflow: 'auto',
      }}
    >
      <div className="flex items-center gap-2">
        <Text variant="content-base-bold" className="flex-1">{t('Start here')}</Text>
        {onCollapse && (
          <IconButton priority="tertiary" icon={<ExpandLessOutlined fontSize="small" />} aria-label={t('Fold the Start here panel')} aria-expanded data-testid="graph-start-here-fold" onClick={onCollapse} />
        )}
      </div>
      {sections.length === 0 && (
        <Text variant="content-base" className="mt-2 block text-default-secondary">
          {t('Nothing to start from yet: run a simulation to see where the estate is exposed.')}
        </Text>
      )}
      {sections.map(section => (
        <section key={section.kind} className="mt-4" aria-label={t(STARTING_POINT_KIND_LABEL[section.kind])}>
          <Text variant="content-caption" className="text-default-secondary">{t(STARTING_POINT_KIND_LABEL[section.kind])}</Text>
          <ul
            className="mt-1 flex flex-col gap-1"
            style={{
              listStyle: 'none',
              marginBottom: 0,
              paddingLeft: 0,
            }}
          >
            {section.points.map((point) => {
              const Icon = NODE_ICON[point.point_node_type];
              const key = `${point.point_node_type.toLowerCase()}:${point.point_entity_id}`;
              const active = current === key;
              return (
                <li key={`${section.kind}-${point.point_entity_id}`}>
                  <Box
                    component="button"
                    type="button"
                    data-testid={`graph-starting-point-${section.kind}-${point.point_entity_id}`}
                    aria-current={active ? 'true' : undefined}
                    title={point.point_label}
                    onClick={() => onSelect(point)}
                    sx={{
                      'width': '100%',
                      'display': 'flex',
                      'alignItems': 'center',
                      'gap': 1,
                      'padding': '4px 8px',
                      'border': 'none',
                      'borderRadius': 1,
                      'cursor': 'pointer',
                      'textAlign': 'left',
                      'color': theme.palette.text.primary,
                      'backgroundColor': active ? theme.palette.action.selected : 'transparent',
                      '&:hover, &:focus-visible': {
                        backgroundColor: theme.palette.action.hover,
                        outline: 'none',
                      },
                    }}
                  >
                    <Icon />
                    <Text variant="content-base" className="min-w-0 flex-1 truncate">{point.point_label}</Text>
                    {(point.point_score ?? 0) > 0 && section.kind !== 'LATEST_SIMULATION' && (
                      <Text variant="content-caption" className="text-default-secondary">{n(point.point_score ?? 0)}</Text>
                    )}
                  </Box>
                </li>
              );
            })}
          </ul>
        </section>
      ))}
    </Paper>
  );
};

export default GraphStartingPoints;
