import { useEffect } from 'react';
import { useParams } from 'react-router';

import { fetchMe } from '../../../../../actions/Application';
import { fetchScenarioObserverChallenges } from '../../../../../actions/challenge-action';
import { fetchScenarioPlayerDocuments } from '../../../../../actions/Document';
import { fetchScenario } from '../../../../../actions/scenarios/scenario-actions';
import { getScenarioChallengesReaderSelector } from '../../../../../actions/selectors';
import { useSelectorHelper } from '../../../../../store';
import { type Scenario as ScenarioType } from '../../../../../utils/api-types';
import { useQueryParameter } from '../../../../../utils/Environment';
import { useAppDispatch } from '../../../../../utils/hooks';
import useScenarioPermissions from '../../../../../utils/permissions/useScenarioPermissions';
import ChallengesPreview from '../../../common/challenges/ChallengesPreview';
import { PreviewChallengeContext } from '../../../common/Context';

const ScenarioChallengesPreview = () => {
  const dispatch = useAppDispatch();

  const { scenarioId } = useParams() as { scenarioId: ScenarioType['scenario_id'] };
  const challengesReader = useSelectorHelper(state => getScenarioChallengesReaderSelector(scenarioId, state));

  const { scenario_information: scenario, scenario_challenges: challenges } = challengesReader ?? {};

  const permissions = useScenarioPermissions(scenarioId);
  const [userId] = useQueryParameter(['user']);

  useEffect(() => {
    dispatch(fetchMe());
    if (scenarioId) {
      dispatch(fetchScenario(scenarioId));
      dispatch(fetchScenarioObserverChallenges(scenarioId, userId));
      dispatch(fetchScenarioPlayerDocuments(scenarioId, userId));
    }
  }, [dispatch, scenarioId, userId]);

  return (
    <PreviewChallengeContext.Provider value={{
      linkToPlayerMode: '',
      linkToAdministrationMode: `/admin/scenarios/${scenarioId}/definition`,
      scenarioOrExercise: scenario,
    }}
    >
      <ChallengesPreview challenges={challenges} permissions={permissions} />
    </PreviewChallengeContext.Provider>
  );
};

export default ScenarioChallengesPreview;
