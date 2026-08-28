import { DevicesOtherOutlined, Groups3Outlined, PersonOutlined } from '@mui/icons-material';
import { Chip, Tooltip } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { SelectGroup } from 'mdi-material-ui';
import { type ComponentType } from 'react';

import { useFormatter } from '../../../../../../../../components/i18n';
import { type EsBase, type EsInjectExpectation } from '../../../../../../../../utils/api-types';
import getTargetTypeFromInjectExpectation from './injectExpectationTarget';
import useInjectExpectationTargetLabel from './useInjectExpectationTargetLabel';

// Icon + accent per expectation target kind, so the source reads at a glance.
const SOURCE_VISUALS: Record<string, {
  icon: ComponentType<{ style?: object }>;
  color: string;
}> = {
  PLAYERS: {
    icon: PersonOutlined,
    color: '#0fbcff',
  },
  TEAMS: {
    icon: Groups3Outlined,
    color: '#26a96c',
  },
  ASSETS: {
    icon: DevicesOtherOutlined,
    color: '#9575cd',
  },
  ASSETS_GROUPS: {
    icon: SelectGroup,
    color: '#ffb300',
  },
};

/**
 * Renders the source of an inject expectation as a chip: the icon and accent
 * encode the target kind (endpoint / asset group / team / player), the label
 * is the actual target name so two expectations on different assets no longer
 * look duplicated. The ES document only carries the target id, so the name is
 * resolved through the shared batched options cache; while loading (or when
 * the target is deleted / not readable) the generic kind label is shown.
 * Expectations without a resolvable target render a dash instead of crashing
 * formatjs with an empty translation.
 */
const InjectExpectationSourceFragment = ({ element }: { element: EsBase }) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const target = getTargetTypeFromInjectExpectation(element as EsInjectExpectation);
  const targetName = useInjectExpectationTargetLabel(target.type, target.id);
  if (!target.label) {
    return <span>-</span>;
  }
  const visual = SOURCE_VISUALS[target.type] ?? {
    icon: DevicesOtherOutlined,
    color: theme.palette.primary.main,
  };
  const Icon = visual.icon;
  const kindLabel = t(target.label);
  return (
    <Tooltip title={targetName ? `${targetName} (${kindLabel})` : kindLabel}>
      <Chip
        icon={(
          <Icon style={{
            fontSize: 14,
            color: visual.color,
          }}
          />
        )}
        label={targetName ?? kindLabel}
        size="small"
        variant="outlined"
        sx={{
          'height': 22,
          'maxWidth': '100%',
          'fontSize': 11,
          'fontWeight': 600,
          'borderRadius': 1,
          // Kind fallbacks are lowercase i18n keys ("asset group"); real names
          // must render verbatim (hostnames, emails...).
          'textTransform': targetName ? 'none' : 'capitalize',
          'color': visual.color,
          'borderColor': alpha(visual.color, 0.4),
          'backgroundColor': alpha(visual.color, 0.08),
          '& .MuiChip-icon': { marginLeft: 0.5 },
        }}
      />
    </Tooltip>
  );
};

export default InjectExpectationSourceFragment;
