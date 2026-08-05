import { AccountTreeOutlined, AutoAwesome, ScheduleOutlined } from '@mui/icons-material';
import { Chip } from '@mui/material';
import { type CSSProperties, type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import colorStyles from '../../../../components/Color';
import { useFormatter } from '../../../../components/i18n';

// Ids MUST match the backend ScenarioUtils engine-type values and the frontend ScenarioTypeFilter
// options (Time-based / Chained / Autonomous).
export const SCENARIO_TYPE_TIME_BASED = 'Time-based';
export const SCENARIO_TYPE_CHAINED = 'Chained';
export const SCENARIO_TYPE_AUTONOMOUS = 'Autonomous';

export type ScenarioTypeValue = typeof SCENARIO_TYPE_TIME_BASED | typeof SCENARIO_TYPE_CHAINED | typeof SCENARIO_TYPE_AUTONOMOUS;

const useStyles = makeStyles()(() => ({
  chip: {
    marginTop: 2,
    fontSize: 14,
    fontWeight: 800,
    textTransform: 'uppercase',
    borderRadius: 4,
    height: 25,
  },
  chipInList: {
    fontSize: 12,
    height: 20,
    float: 'left',
    textTransform: 'uppercase',
    borderRadius: 4,
    width: 130,
  },
}));

// One color + icon per engine type, chosen to read at a glance:
// - Time-based: a clock (classic scheduled scenario)
// - Chained: a workflow tree (inject-chaining logic map)
// - Autonomous: the AI sparkle in AI-purple, matching the Autonomous attack entry point
const TYPE_STYLES: Record<ScenarioTypeValue, {
  color: CSSProperties;
  Icon: typeof ScheduleOutlined;
}> = {
  [SCENARIO_TYPE_TIME_BASED]: {
    color: colorStyles.blue,
    Icon: ScheduleOutlined,
  },
  [SCENARIO_TYPE_CHAINED]: {
    color: colorStyles.green,
    Icon: AccountTreeOutlined,
  },
  [SCENARIO_TYPE_AUTONOMOUS]: {
    color: colorStyles.purple,
    Icon: AutoAwesome,
  },
};

interface Props {
  type: ScenarioTypeValue;
  variant?: 'list';
}

const ScenarioType: FunctionComponent<Props> = ({ type, variant }) => {
  const { t } = useFormatter();
  const { classes } = useStyles();

  const style = variant === 'list' ? classes.chipInList : classes.chip;
  const { color, Icon } = TYPE_STYLES[type];

  return (
    <Chip
      classes={{ root: style }}
      style={color}
      sx={{
        '& .MuiChip-icon': {
          color: 'inherit',
          fontSize: variant === 'list' ? 14 : 18,
        },
      }}
      icon={<Icon />}
      label={t(type)}
    />
  );
};

export default ScenarioType;
