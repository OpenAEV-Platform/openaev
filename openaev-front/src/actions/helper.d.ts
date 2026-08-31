import {
  type Challenge,
  type Document,
  type Evaluation,
  type Exercise,
  type Objective,
  type Organization,
  type PlatformSettings,
  type Scenario,
  type ScenarioChallengesReader,
  type SimulationChallengesReader, type TenantSettingsOutput,
  type TenantXtmHubRegistration,
  type User,
} from '../utils/api-types';

export interface UserHelper {
  getMe: () => User;
  getMeAdmin: () => boolean;
  getUsersMap: () => Record<string, User>;
}

export interface ObjectiveHelper {
  getObjective: (objectiveId: string) => Objective | undefined;
  getObjectiveEvaluations: (objectiveId: string) => Evaluation[];
}

export interface OrganizationHelper {
  getOrganizations: () => Organization[];
  getOrganizationsMap: () => Record<string, Organization>;
}

export interface LoggedHelper {
  // TODO type logged object
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  logged: () => any;
  getMe: () => User;
  getPlatformSettings: () => PlatformSettings;
  getTenantSettings: () => TenantSettingsOutput;
  getPlatformName: () => string;
  getUserLang: () => string;
  getXtmHubRegistration: () => TenantXtmHubRegistration | null;
}

export interface ChallengeHelper {
  getChallengesMap: () => Record<string, Challenge>;
  getChallenges: () => Challenge[];
  getExerciseChallenges: (exerciseId: Exercise['exercise_id']) => Challenge[];
  getScenarioChallenges: (scenarioId: Scenario['scenario_id']) => Challenge[];
}

export interface DocumentHelper {
  getDocuments: () => Document[];
  getDocumentsMap: () => Record<string, Document>;
}

export interface SimulationChallengesReaderHelper { getSimulationChallengesReader: (exerciseId: SimulationChallengesReader['exercise_id']) => SimulationChallengesReader }

export interface ScenarioChallengesReaderHelper { getScenarioChallengesReader: (scenarioId: SimulationChallengesReader['scenario_id']) => ScenarioChallengesReader }
