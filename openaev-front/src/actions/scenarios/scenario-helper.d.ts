import { type LessonsCategory, type LessonsQuestion, type Objective, type Scenario, type Team } from '../../utils/api-types';

export interface ScenariosHelper {
  getScenario: (scenarioId: string) => Scenario;
  getScenarios: () => Scenario[];
  getScenarioTeams: (scenarioId: string) => Team[];
  getScenarioObjectives: (scenarioId: string) => Objective[];
  getScenarioLessonsCategories: (scenarioId: string) => LessonsCategory[];
  getScenarioLessonsQuestions: (scenarioId: string) => LessonsQuestion[];
}
