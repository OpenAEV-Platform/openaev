import { DevicesOtherOutlined, Groups3Outlined, PersonOutlined } from '@mui/icons-material';
import { Chip } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { SelectGroup } from 'mdi-material-ui';
import { type ComponentType } from 'react';

import { useFormatter } from '../../../../../../../../components/i18n';
import { type EsBase, type EsInjectExpectation } from '../../../../../../../../utils/api-types';
import getTargetTypeFromInjectExpectation from './injectExpectationTarget';

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
 * Renders the target kind of an inject expectation as a small icon tile
 * (endpoint / asset group / team / player). Expectations without a resolvable
 * target render a dash instead of crashing formatjs with an empty translation.
 */
const InjectExpectationSourceFragment = ({ element }: { element: EsBase }) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const target = getTargetTypeFromInjectExpectation(element as EsInjectExpectation);
  if (!target.label) {
    return <span>-</span>;
  }
  const visual = SOURCE_VISUALS[target.type] ?? {
    icon: DevicesOtherOutlined,
    color: theme.palette.primary.main,
  };
  const Icon = visual.icon;
  return (
    <Chip
      icon={(
        <Icon style={{
          fontSize: 14,
          color: visual.color,
        }}
        />
      )}
      label={t(target.label)}
      size="small"
      variant="outlined"
      sx={{
        'height': 22,
        'fontSize': 11,
        'fontWeight': 600,
        'borderRadius': 1,
        'textTransform': 'capitalize',
        'color': visual.color,
        'borderColor': alpha(visual.color, 0.4),
        'backgroundColor': alpha(visual.color, 0.08),
        '& .MuiChip-icon': { marginLeft: 0.5 },
      }}
    />
  );
};

export default InjectExpectationSourceFragment;
