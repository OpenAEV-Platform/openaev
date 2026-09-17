import { Chip, type ChipSeverity } from '@filigran/design-system';
import { AccountTreeOutlined, ScheduleOutlined } from '@mui/icons-material';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';

// Ids MUST match the backend ScenarioUtils engine-type values and the frontend ScenarioTypeFilter
// options (Time-based / Chained). Autonomy is a launch-time MODE, not a scenario type.
export const SCENARIO_TYPE_TIME_BASED = 'Time-based';
export const SCENARIO_TYPE_CHAINED = 'Chained';

export type ScenarioTypeValue = typeof SCENARIO_TYPE_TIME_BASED | typeof SCENARIO_TYPE_CHAINED;

// One color + icon per engine type, chosen to read at a glance:
// - Time-based: a clock (classic scheduled scenario)
// - Chained: a workflow tree (inject-chaining logic map)
const TYPE_STYLES: Record<ScenarioTypeValue, {
  severity: ChipSeverity;
  Icon: typeof ScheduleOutlined;
}> = {
  [SCENARIO_TYPE_TIME_BASED]: {
    severity: 'info',
    Icon: ScheduleOutlined,
  },
  [SCENARIO_TYPE_CHAINED]: {
    severity: 'low',
    Icon: AccountTreeOutlined,
  },
};

interface Props {
  type: ScenarioTypeValue;
  variant?: 'list';
}

const ScenarioType: FunctionComponent<Props> = ({ type }) => {
  const { t } = useFormatter();

  const { severity, Icon } = TYPE_STYLES[type];

  return (
    <Chip severity={severity} startIcon={<Icon />} label={t(type)} />
  );
};

export default ScenarioType;
