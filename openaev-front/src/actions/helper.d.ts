import {
  type Challenge,
  type Document,
  type Exercise,
  type Organization,
  type PlatformSettings,
  type Scenario,
  type ScenarioChallengesReader,
  type SimulationChallengesReader,
  type Token,
  type User,
} from '../utils/api-types';
import type { TenantSettings } from './settings/tenant-settings-action';

export interface UserHelper {
  getMe: () => User;
  getMeAdmin: () => boolean;
  getUsersMap: () => Record<string, User>;
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
  getTenantSettings: () => TenantSettings;
  getPlatformName: () => string;
  getUserLang: () => string;
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

export interface MeTokensHelper { getMeTokens: () => Token[] }

export interface SimulationChallengesReaderHelper { getSimulationChallengesReader: (exerciseId: SimulationChallengesReader['exercise_id']) => SimulationChallengesReader }

export interface ScenarioChallengesReaderHelper { getScenarioChallengesReader: (scenarioId: SimulationChallengesReader['scenario_id']) => ScenarioChallengesReader }
