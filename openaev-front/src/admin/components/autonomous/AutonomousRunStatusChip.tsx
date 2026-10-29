import { Chip } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { type AutonomousRunStatus } from '../../../actions/autonomous/autonomous-types';
import type colorStyles from '../../../components/Color';
import { useFormatter } from '../../../components/i18n';
import { colorStyleSeverity } from '../../../utils/statusUtils';

// Mirrors ExerciseStatus's chip look exactly (filled colorStyles, uppercase, same radius/height) so
// an autonomous scenario shows the SAME status chip a simulation shows - the simulation chip is the
// single reference. The run status is the source of truth (it is hard-linked to the simulation), it
// just has a few states an ExerciseStatus cannot express (created, waiting-input, completed/failed).

// Map each run status onto the same colour vocabulary the simulation chip uses, so the two are
// visually consistent: running=green, paused=orange, canceled=canceled, a terminal completed matches
// the simulation's grey FINISHED, failed is red, and the AI-specific waiting-input uses the feature's
// purple accent.
const STATUS_COLOR: Record<AutonomousRunStatus, keyof typeof colorStyles> = {
  CREATED: 'blue',
  // Build states (the AI authoring the scenario's logic) use OCTI draft orange so not-yet-run
  // logic reads as "not executed yet" at a glance.
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

const AutonomousRunStatusChip: FunctionComponent<Props> = ({ status }) => {
  const { t } = useFormatter();
  return (
    <Chip
      severity={colorStyleSeverity[STATUS_COLOR[status] ?? 'blue']}
      label={t(STATUS_LABEL[status] ?? status)}
    />
  );
};

export default AutonomousRunStatusChip;
