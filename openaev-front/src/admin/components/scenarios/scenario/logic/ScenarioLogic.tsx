import { Typography } from '@mui/material';
import { useParams } from 'react-router';

import { useFormatter } from '../../../../../components/i18n';
import { type Scenario } from '../../../../../utils/api-types';

const ScenarioLogic = () => {
  const { t } = useFormatter();
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };

  return (
    <div>
      <Typography variant="h4">{t('Logic')}</Typography>
    </div>
  );
};

export default ScenarioLogic;
