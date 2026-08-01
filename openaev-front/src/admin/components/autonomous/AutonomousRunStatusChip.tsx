import { Chip } from '@mui/material';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type AutonomousRunStatus } from '../../../actions/autonomous/autonomous-types';
import colorStyles from '../../../components/Color';
import { useFormatter } from '../../../components/i18n';

// Mirrors ExerciseStatus's chip look exactly (filled colorStyles, uppercase, same radius/height) so
// an autonomous scenario shows the SAME status chip a simulation shows - the simulation chip is the
// single reference. The run status is the source of truth (it is hard-linked to the simulation), it
// just has a few states an ExerciseStatus cannot express (created, waiting-input, completed/failed).
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
    width: 120,
  },
}));

// Map each run status onto the same colour vocabulary the simulation chip uses, so the two are
// visually consistent: running=green, paused=orange, canceled=canceled, a terminal completed matches
// the simulation's grey FINISHED, failed is red, and the AI-specific waiting-input uses the feature's
// purple accent.
const STATUS_COLOR: Record<AutonomousRunStatus, keyof typeof colorStyles> = {
  CREATED: 'blue',
  RUNNING: 'green',
  PAUSED: 'orange',
  WAITING_INPUT: 'purple',
  COMPLETED: 'grey',
  FAILED: 'red',
  CANCELED: 'canceled',
};

interface Props {
  status: AutonomousRunStatus;
  variant?: 'list';
}

const AutonomousRunStatusChip: FunctionComponent<Props> = ({ status, variant }) => {
  const { t } = useFormatter();
  const { classes } = useStyles();
  const style = variant === 'list' ? classes.chipInList : classes.chip;
  return (
    <Chip
      classes={{ root: style }}
      style={colorStyles[STATUS_COLOR[status] ?? 'blue']}
      label={t(status)}
    />
  );
};

export default AutonomousRunStatusChip;
