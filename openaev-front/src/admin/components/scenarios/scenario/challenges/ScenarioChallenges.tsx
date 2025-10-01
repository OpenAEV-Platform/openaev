import { useParams } from 'react-router';

import { fetchScenarioChallenges } from '../../../../../actions/challenge-action';
import { type ChallengeHelper } from '../../../../../actions/helper';
import { useHelper } from '../../../../../store';
import { type Scenario } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import ContextualChallenges from '../../../common/challenges/ContextualChallenges';
import { ChallengeContext, type ChallengeContextType } from '../../../common/Context';

const ScenarioChallenges = () => {
  // Standard hooks
  const dispatch = useAppDispatch();
  // Fetching data
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };
  const { challenges } = useHelper((helper: ChallengeHelper) => ({ challenges: helper.getScenarioChallenges(scenarioId) }));
  useDataLoader(() => {
    dispatch(fetchScenarioChallenges(scenarioId));
  });
  const context: ChallengeContextType = { previewChallengeUrl: () => `/admin/scenarios/${scenarioId}/challenges` };
  return (
    <ChallengeContext.Provider value={context}>
      <ContextualChallenges challenges={challenges} linkToInjects={`/admin/scenarios/${scenarioId}/injects`} />
    </ChallengeContext.Provider>
  );
};

export default ScenarioChallenges;
