import { type FunctionComponent } from 'react';
import { useNavigate } from 'react-router';

import { createRunningExerciseFromScenario } from '../../../../actions/scenarios/scenario-actions';
import { useFormatter } from '../../../../components/i18n';
import { SIMULATION_BASE_URL } from '../../../../constants/BaseUrls';
import { type Exercise } from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import { SCENARIO_TYPE_CHAINED } from '../scenario/ScenarioType';
import { type ScenarioRow, scenarioTypeOf } from '../scenarioRow';
import { type ScenarioTargets, toTargetsInput } from '../targets/scenarioTargets';
import ScenarioTargetsDrawer from '../targets/ScenarioTargetsDrawer';

interface Props {
  scenario: ScenarioRow;
  onClose: () => void;
}

// A simulation starts from the scenario, the chosen targets added to it only.
const ScenarioLaunchDrawer: FunctionComponent<Props> = ({ scenario, onClose }) => {
  const { t } = useFormatter();
  const navigate = useNavigate();
  const chained = scenarioTypeOf(scenario) === SCENARIO_TYPE_CHAINED;

  const launch = (targets: ScenarioTargets) => createRunningExerciseFromScenario(scenario.scenario_id, toTargetsInput(targets))
    .then(({ data }: { data: Exercise }) => {
      MESSAGING$.notifySuccess(t('New simulation successfully created and started'));
      // A chained simulation opens on its live execution view.
      navigate(`${SIMULATION_BASE_URL}/${data.exercise_id}${chained ? '/attack-path' : ''}`);
    });

  return (
    <ScenarioTargetsDrawer
      title={`${t('Launch')} ${scenario.scenario_name}`}
      description={chained
        ? t('A simulation starts from this scenario now, inside its scope.')
        : t('Add targets if needed: a simulation starts from this scenario now.')}
      withTargets={!chained}
      submitLabel={t('Launch')}
      onSubmit={launch}
      onClose={onClose}
    />
  );
};

export default ScenarioLaunchDrawer;
