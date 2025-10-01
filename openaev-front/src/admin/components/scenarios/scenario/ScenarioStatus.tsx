import { Chip } from '@mui/material';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import colorStyles from '../../../../components/Color';
import { useFormatter } from '../../../../components/i18n';
import { type Scenario } from '../../../../utils/api-types';

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
    lineHeight: '12px',
    height: 20,
    float: 'left',
    textTransform: 'uppercase',
    borderRadius: 4,
    width: 120,
  },
}));

interface Props {
  scenario: Scenario;
  variant?: 'list';
}

export const SCENARIO_SCHEDULED_STATUS = 'Scheduled';
export const SCENARIO_NOT_SCHEDULED_STATUS = 'Not planned';

const scenarioStatus: FunctionComponent<Props> = ({
  scenario,
  variant,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const { classes } = useStyles();

  const style = variant === 'list' ? classes.chipInList : classes.chip;
  if (scenario.scenario_recurrence) {
    return (
      <Chip
        classes={{ root: style }}
        style={colorStyles.green}
        label={t(SCENARIO_SCHEDULED_STATUS)}
      />
    );
  }
  return (
    <Chip
      classes={{ root: style }}
      style={colorStyles.grey}
      label={t(SCENARIO_NOT_SCHEDULED_STATUS)}
    />
  );
};
export default scenarioStatus;
