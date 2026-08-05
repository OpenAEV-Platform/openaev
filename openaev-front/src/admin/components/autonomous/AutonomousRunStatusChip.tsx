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
  // Dry-run states use OCTI draft orange so a plan reads as "not executed yet" at a glance.
  PLANNING: 'orange',
  PLANNED: 'orange',
  RUNNING: 'green',
  PAUSED: 'orange',
  WAITING_INPUT: 'purple',
  COMPLETED: 'grey',
  FAILED: 'red',
  CANCELED: 'canceled',
};

// Clean labels: the raw enum values are SCREAMING_SNAKE_CASE, so t('WAITING_INPUT') renders the
// untranslated fallback with the underscore intact (then uppercased by CSS to "WAITING_INPUT"). Map
// to plain English keys the translation layer can localize.
const STATUS_LABEL: Record<AutonomousRunStatus, string> = {
  CREATED: 'Created',
  PLANNING: 'Planning',
  PLANNED: 'Plan ready',
  RUNNING: 'Running',
  PAUSED: 'Paused',
  WAITING_INPUT: 'Waiting for input',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
  CANCELED: 'Canceled',
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
      label={t(STATUS_LABEL[status] ?? status)}
    />
  );
};

export default AutonomousRunStatusChip;
