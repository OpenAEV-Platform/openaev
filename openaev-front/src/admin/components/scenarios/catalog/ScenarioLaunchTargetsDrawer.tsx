import { type FunctionComponent } from 'react';
import { useNavigate } from 'react-router';

import { completeScenarioTargets, fetchScenarioTargets } from '../../../../actions/catalog/scenario-catalog-actions';
import { createRunningExerciseFromScenario } from '../../../../actions/scenarios/scenario-actions';
import { useFormatter } from '../../../../components/i18n';
import { type Exercise } from '../../../../utils/api-types';
import ScenarioTargetsDrawer from './ScenarioTargetsDrawer';

interface Props {
  scenarioId: string;
  scenarioName: string;
  onClose: () => void;
}

// A launch with an inject still missing its target asks for one first (F12).
const ScenarioLaunchTargetsDrawer: FunctionComponent<Props> = ({ scenarioId, scenarioName, onClose }) => {
  const { t } = useFormatter();
  const navigate = useNavigate();
  return (
    <ScenarioTargetsDrawer
      title={`${t('Launch')} ${scenarioName}`}
      submitLabel={t('Launch')}
      load={() => fetchScenarioTargets(scenarioId).then(({ data }) => ({ injects: data.injects }))}
      onSubmit={input => completeScenarioTargets(scenarioId, input)
        .then(() => createRunningExerciseFromScenario(scenarioId))
        .then(({ data }: { data: Exercise }) => navigate(`/admin/simulations/${data.exercise_id}`))}
      onClose={onClose}
    />
  );
};

export default ScenarioLaunchTargetsDrawer;
