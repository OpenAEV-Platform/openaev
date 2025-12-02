import { useEffect } from 'react';
import { useParams } from 'react-router';

import { fetchMe } from '../../../actions/Application';
import { fetchLessonsCategories, fetchLessonsQuestions, fetchScenario } from '../../../actions/scenarios/scenario-actions';
import {
  getMeSelector,
  getScenarioLessonsCategoriesSelector,
  getScenarioLessonsQuestionsSelector,
  getScenarioSelector,
} from '../../../actions/selectors';
import { ViewLessonContext, type ViewLessonContextType } from '../../../admin/components/common/Context';
import { useSelectorHelper } from '../../../store';
import { type Scenario } from '../../../utils/api-types';
import { useQueryParameter } from '../../../utils/Environment';
import { useAppDispatch } from '../../../utils/hooks';
import useScenarioPermissions from '../../../utils/permissions/useScenarioPermissions';
import LessonsPreview from './LessonsPreview';

const ScenarioViewLessons = () => {
  const dispatch = useAppDispatch();
  const [preview] = useQueryParameter(['preview']);
  const [userId] = useQueryParameter(['user']);
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };
  const isPreview = preview === 'true';

  const processToGenericSource = (scenario: Scenario | undefined) => {
    if (!scenario) return undefined;
    return {
      id: scenarioId,
      type: 'scenario',
      name: scenario.scenario_name,
      subtitle: scenario.scenario_subtitle,
      userId,
      isPlayerViewAvailable: false,
    };
  };

  const me = useSelectorHelper(getMeSelector);
  const scenario = useSelectorHelper(state => getScenarioSelector(scenarioId, state));
  const source = processToGenericSource(scenario);
  const lessonsCategories = useSelectorHelper(state => getScenarioLessonsCategoriesSelector(scenarioId, state));
  const lessonsQuestions = useSelectorHelper(state => getScenarioLessonsQuestionsSelector(scenarioId, state));

  const finalUserId = userId && userId !== 'null' ? userId : me?.user_id;

  useEffect(() => {
    dispatch(fetchMe());
    if (isPreview) {
      dispatch(fetchScenario(scenarioId));
      dispatch(fetchLessonsCategories(scenarioId));
      dispatch(fetchLessonsQuestions(scenarioId));
    }
  }, [dispatch, scenarioId, userId, finalUserId]);

  // Pass the full scenario because the scenario is never loaded in the store at this point
  const permissions = useScenarioPermissions(scenarioId);

  const context: ViewLessonContextType = {};

  return (
    <ViewLessonContext.Provider value={context}>
      {isPreview && (
        <LessonsPreview
          source={{
            ...source,
            finalUserId,
          }}
          lessonsCategories={lessonsCategories}
          lessonsQuestions={lessonsQuestions}
          permissions={permissions}
        />
      )}
    </ViewLessonContext.Provider>
  );
};

export default ScenarioViewLessons;
