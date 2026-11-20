import { useNavigate } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import { SIMULATION_BASE_URL } from '../../../../constants/BaseUrls';
import { MESSAGING$ } from '../../../../utils/Environment';

// A chained simulation opens on its live execution view, a time-based one on its overview.
const useOpenLaunchedSimulation = () => {
  const { t } = useFormatter();
  const navigate = useNavigate();
  return (simulationId: string, chained: boolean) => {
    MESSAGING$.notifySuccess(t('New simulation successfully created and started'));
    navigate(`${SIMULATION_BASE_URL}/${simulationId}${chained ? '/attack-path' : ''}`);
  };
};

export default useOpenLaunchedSimulation;
