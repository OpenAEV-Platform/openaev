import { useMemo } from 'react';
import { useParams } from 'react-router';

import { addScenarioEvaluation, fetchScenarioEvaluations, updateScenarioEvaluation } from '../../../../../actions/evaluation';
import { addScenarioObjective, deleteScenarioObjective, fetchScenarioObjectives, updateScenarioObjective } from '../../../../../actions/objective';
import {
  addLessonsCategory,
  addLessonsQuestion,
  applyLessonsTemplate,
  deleteLessonsCategory,
  deleteLessonsQuestion,
  emptyLessonsCategories,
  fetchLessonsCategories,
  fetchLessonsQuestions, fetchPlayersByScenario,
  fetchScenarioTeams,
  updateLessonsCategory,
  updateLessonsCategoryTeams,
  updateLessonsQuestion,
  updateScenarioLessons,
} from '../../../../../actions/scenarios/scenario-actions';
import { getLessonsTemplatesSelector, getScenarioLessonsCategoriesSelector, getScenarioLessonsQuestionsSelector, getScenarioObjectivesSelector, getScenarioSelector, getScenarioTeamsSelector, getTeamsMapSelector } from '../../../../../actions/selectors';
import { fetchTeams } from '../../../../../actions/teams/team-actions';
import Loader from '../../../../../components/Loader';
import { useSelectorHelper } from '../../../../../store';
import {
  type EvaluationInput,
  type LessonsCategoryCreateInput,
  type LessonsCategoryTeamsInput,
  type LessonsCategoryUpdateInput,
  type LessonsQuestionCreateInput,
  type LessonsQuestionUpdateInput,
  type ObjectiveInput, type Scenario,
} from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import useScenarioPermissions from '../../../../../utils/permissions/useScenarioPermissions';
import { LessonContext, type LessonContextType } from '../../../common/Context';
import Lessons from '../../../lessons/scenarios/Lessons';

const ScenarioLessons = () => {
  const dispatch = useAppDispatch();

  // Fetching data
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };

  const processToGenericSource = (scenario: Scenario) => {
    return {
      id: scenario.scenario_id,
      type: 'scenario',
      name: scenario.scenario_name,
      lessons_anonymized: scenario.scenario_lessons_anonymized ?? false,
    };
  };

  const scenario = useSelectorHelper(state => getScenarioSelector(scenarioId, state));
  const objectives = useSelectorHelper(state => getScenarioObjectivesSelector(scenarioId, state));
  const teams = useSelectorHelper(state => getScenarioTeamsSelector(scenarioId, state));
  const teamsMap = useSelectorHelper(getTeamsMapSelector);
  const lessonsCategories = useSelectorHelper(state => getScenarioLessonsCategoriesSelector(scenarioId, state));
  const lessonsQuestions = useSelectorHelper(state => getScenarioLessonsQuestionsSelector(scenarioId, state));
  const lessonsTemplates = useSelectorHelper(getLessonsTemplatesSelector);
  useDataLoader(() => {
    dispatch(fetchTeams());
    dispatch(fetchPlayersByScenario(scenarioId));
    dispatch(fetchLessonsCategories(scenarioId));
    dispatch(fetchLessonsQuestions(scenarioId));
    dispatch(fetchScenarioObjectives(scenarioId));
    dispatch(fetchScenarioTeams(scenarioId));
  });

  const source = useMemo(
    () => scenario && processToGenericSource(scenario),
    [scenario],
  );

  const permissions = useScenarioPermissions(scenarioId);

  const context: LessonContextType = {
    onApplyLessonsTemplate: (data: string) => dispatch(applyLessonsTemplate(scenarioId, data)),
    onEmptyLessonsCategories: () => dispatch(emptyLessonsCategories(scenarioId)),
    onUpdateSourceLessons: (data: boolean) => dispatch(updateScenarioLessons(scenarioId, { lessons_anonymized: data })),
    // Categories
    onAddLessonsCategory: (data: LessonsCategoryCreateInput) => dispatch(addLessonsCategory(scenarioId, data)),
    onDeleteLessonsCategory: (data: string) => dispatch(deleteLessonsCategory(scenarioId, data)),
    onUpdateLessonsCategory: (lessonCategoryId: string, data: LessonsCategoryUpdateInput) => dispatch(updateLessonsCategory(scenarioId, lessonCategoryId, data)),
    onUpdateLessonsCategoryTeams: (lessonCategoryId: string, data: LessonsCategoryTeamsInput) => dispatch(updateLessonsCategoryTeams(scenarioId, lessonCategoryId, data)),
    // Questions
    onDeleteLessonsQuestion: (lessonsCategoryId: string, lessonsQuestionId: string) => dispatch(
      deleteLessonsQuestion(
        scenarioId,
        lessonsCategoryId,
        lessonsQuestionId,
      ),
    ),
    onUpdateLessonsQuestion: (lessonsCategoryId: string, lessonsQuestionId: string, data: LessonsQuestionUpdateInput) => dispatch(
      updateLessonsQuestion(
        scenarioId,
        lessonsCategoryId,
        lessonsQuestionId,
        data,
      ),
    ),
    onAddLessonsQuestion: (lessonsCategoryId: string, data: LessonsQuestionCreateInput) => dispatch(addLessonsQuestion(scenarioId, lessonsCategoryId, data)),
    // Objectives
    onAddObjective: (data: ObjectiveInput) => dispatch(addScenarioObjective(scenarioId, data)),
    onUpdateObjective: (objectiveId: string, data: ObjectiveInput) => dispatch(updateScenarioObjective(scenarioId, objectiveId, data)),
    onDeleteObjective: (objectiveId: string) => dispatch(deleteScenarioObjective(scenarioId, objectiveId)),
    // Evaluation
    onAddEvaluation: (objectiveId: string, data: EvaluationInput) => dispatch(addScenarioEvaluation(scenarioId, objectiveId, data)),
    onUpdateEvaluation: (objectiveId: string, evaluationId: string, data: EvaluationInput) => dispatch(updateScenarioEvaluation(scenarioId, objectiveId, evaluationId, data)),
    onFetchEvaluation: (objectiveId: string) => dispatch(fetchScenarioEvaluations(scenarioId, objectiveId)),
  };

  if (!source) {
    return <Loader variant="inElement" />;
  }

  return (
    <LessonContext.Provider value={context}>
      <Lessons
        source={{
          ...source,
          isReadOnly: permissions.readOnly,
          isUpdatable: permissions.canManage,
        }}
        objectives={objectives}
        teamsMap={teamsMap}
        teams={teams}
        lessonsCategories={lessonsCategories}
        lessonsQuestions={lessonsQuestions}
        lessonsTemplates={lessonsTemplates}
      >
      </Lessons>
    </LessonContext.Provider>
  );
};

export default ScenarioLessons;
