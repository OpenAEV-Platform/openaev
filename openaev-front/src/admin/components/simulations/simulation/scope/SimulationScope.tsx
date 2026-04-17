import { Typography } from '@mui/material';
import { useParams } from 'react-router';

import { useFormatter } from '../../../../../components/i18n';
import { type Exercise } from '../../../../../utils/api-types';

const SimulationScope = () => {
  const { t } = useFormatter();
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };

  return (
    <div>
      <Typography variant="h4">{t('Scope')}</Typography>
    </div>
  );
};

export default SimulationScope;
