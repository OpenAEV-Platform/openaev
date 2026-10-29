import { Chip } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type Exercise } from '../../../../utils/api-types';

interface Props {
  exerciseStatus: Exercise['exercise_status'] | undefined;
  exerciseStartDate?: string;
  variant?: 'list';
}

const ExerciseStatus: FunctionComponent<Props> = ({
  exerciseStatus,
  exerciseStartDate,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  switch (exerciseStatus) {
    case 'SCHEDULED':
      return (
        <Chip label={exerciseStartDate ? t('Scheduled') : t('Draft')} severity="info" />
      );
    case 'RUNNING':
      return (
        <Chip label={t('Running')} severity="low" />
      );
    case 'PAUSED':
      return (
        <Chip label={t('Paused')} severity="medium" />
      );
    case 'CANCELED':
      return (
        <Chip label={t('Canceled')} severity="neutral" />
      );
    case 'FINISHED':
      return (
        <Chip label={t('Finished')} severity="neutral" />
      );
    default:
      return (
        <Chip label={t('Scheduled')} severity="info" />
      );
  }
};
export default ExerciseStatus;
