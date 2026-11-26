import { useNavigate } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import { SIMULATION_BASE_URL } from '../../../../constants/BaseUrls';
import { MESSAGING$ } from '../../../../utils/Environment';
import { SCENARIO_TYPE_CHAINED, SCENARIO_TYPE_TIME_BASED, type ScenarioTypeValue } from '../scenario/ScenarioType';

// A chained simulation opens on its live execution view, a time-based one on its overview.
const LANDING_TAB: Record<ScenarioTypeValue, string> = {
  [SCENARIO_TYPE_TIME_BASED]: '',
  [SCENARIO_TYPE_CHAINED]: '/attack-path',
};

const useOpenLaunchedSimulation = () => {
  const { t } = useFormatter();
  const navigate = useNavigate();
  return (simulationId: string, type: ScenarioTypeValue) => {
    MESSAGING$.notifySuccess(t('New simulation successfully created and started'));
    navigate(`${SIMULATION_BASE_URL}/${simulationId}${LANDING_TAB[type]}`);
  };
};

export default useOpenLaunchedSimulation;
