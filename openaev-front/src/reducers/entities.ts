import { Map as ImmutableMap, Record as ImmutableRecord } from 'immutable';

import {
  type Agent,
  type Article,
  type AssetGroup,
  type AttackPattern,
  type CatalogConnectorOutput,
  type Challenge,
  type Channel,
  type ChannelReader,
  type Collector,
  type Comcheck,
  type ComcheckStatus,
  type Communication,
  type CustomDashboard,
  type Document, type Domain,
  type Endpoint,
  type Evaluation,
  type Executor,
  type Exercise,
  type Grant,
  type Group,
  type Inject,
  type InjectExpectation,
  type Injector,
  type InjectorContract,
  type InjectStatus,
  type KillChainPhase,
  type LessonsAnswer,
  type LessonsCategory,
  type LessonsQuestion,
  type LessonsTemplate,
  type LessonsTemplateCategory,
  type LessonsTemplateQuestion,
  type Log,
  type Mitigation,
  type Objective,
  type Organization,
  type Payload,
  type PlatformSettings,
  type Report,
  type RoleOutput,
  type Scenario,
  type ScenarioChallengesReader,
  type SecurityPlatform,
  type SimulationChallengesReader,
  type Tag,
  type Team,
  type Token,
  type User,
  type Variable,
} from '../utils/api-types';

// When adding a new entity, remember to update the EntityTypes union type & EntitiesJS
const entities = {
  files: ImmutableMap<Document['document_id'], unknown>({}),
  users: ImmutableMap<User['user_id'], unknown>({}),
  groups: ImmutableMap<Group['group_id'], unknown>({}),
  roles: ImmutableMap<RoleOutput['role_id'], unknown>({}),
  grants: ImmutableMap<Grant['grant_id'], unknown>({}),
  organizations: ImmutableMap<Organization['organization_id'], unknown>({}),
  tokens: ImmutableMap<Token['token_id'], unknown>({}),
  exercises: ImmutableMap<Exercise['exercise_id'], unknown>({}),
  objectives: ImmutableMap<Objective['objective_id'], unknown>({}),
  evaluations: ImmutableMap<Evaluation['evaluation_id'], unknown>({}),
  comchecks: ImmutableMap<Comcheck['comcheck_id'], unknown>({}),
  comcheckstatuses: ImmutableMap<ComcheckStatus['comcheckstatus_id'], unknown>({}),
  channelreaders: ImmutableMap<ChannelReader['channel_id'], unknown>({}),
  simulationchallengesreaders: ImmutableMap<SimulationChallengesReader['exercise_id'], unknown>({}),
  scenariochallengesreaders: ImmutableMap<ScenarioChallengesReader['scenario_id'], unknown>({}),
  teams: ImmutableMap<Team['team_id'], unknown>({}),
  injects: ImmutableMap<Inject['inject_id'], unknown>({}),
  atomics: ImmutableMap<Inject['inject_id'], unknown>({}),
  // atomicdetails: ImmutableMap({}),
  // targetresults: ImmutableMap({}),
  injectorcontracts: ImmutableMap<InjectorContract['injector_contract_id'], unknown>({}),
  injectstatuses: ImmutableMap<InjectStatus['status_id'], unknown>({}),
  communications: ImmutableMap<Communication['communication_id'], unknown>({}),
  logs: ImmutableMap<Log['log_id'], unknown>({}),
  tags: ImmutableMap<Tag['tag_id'], unknown>({}),
  documents: ImmutableMap<Document['document_id'], unknown>({}),
  channels: ImmutableMap<Channel['channel_id'], unknown>({}),
  payloads: ImmutableMap<Payload['payload_id'], unknown>({}),
  challenges: ImmutableMap<Challenge['challenge_id'], unknown>({}),
  articles: ImmutableMap<Article['article_id'], unknown>({}),
  injectexpectations: ImmutableMap<InjectExpectation['inject_expectation_id'], unknown>({}),
  lessonstemplates: ImmutableMap<LessonsTemplate['lessonstemplate_id'], unknown>({}),
  lessonstemplatecategorys: ImmutableMap<LessonsTemplateCategory['lessonstemplatecategory_id'], unknown>({}),
  lessonstemplatequestions: ImmutableMap<LessonsTemplateQuestion['lessonstemplatequestion_id'], unknown>({}),
  lessonscategorys: ImmutableMap<LessonsCategory['lessonscategory_id'], unknown>({}),
  lessonsquestions: ImmutableMap<LessonsQuestion['lessonsquestion_id'], unknown>({}),
  lessonsanswers: ImmutableMap<LessonsAnswer['lessonsanswer_id'], unknown>({}),
  reports: ImmutableMap<Report['report_id'], unknown>({}),
  variables: ImmutableMap<Variable['variable_id'], unknown>({}),
  killchainphases: ImmutableMap<KillChainPhase['phase_id'], unknown>({}),
  attackpatterns: ImmutableMap<AttackPattern['attack_pattern_id'], unknown>({}),
  endpoints: ImmutableMap<Endpoint['asset_id'], unknown>({}),
  asset_groups: ImmutableMap<AssetGroup['asset_group_id'], unknown>({}),
  securityplatforms: ImmutableMap<SecurityPlatform['asset_id'], unknown>({}),
  scenarios: ImmutableMap<Scenario['scenario_id'], unknown>({}),
  injectors: ImmutableMap<Injector['injector_id'], unknown>({}),
  collectors: ImmutableMap<Collector['collector_id'], unknown>({}),
  executors: ImmutableMap<Executor['executor_id'], unknown>({}),
  mitigations: ImmutableMap<Mitigation['mitigation_id'], unknown>({}),
  customdashboards: ImmutableMap<CustomDashboard['custom_dashboard_id'], unknown>({}),
  agents: ImmutableMap<Agent['agent_id'], unknown>({}),
  platformParameters: ImmutableMap<string, unknown>({}),
  domains: ImmutableMap<Domain['domain_id'], unknown>({}),
  catalog_connectors: ImmutableMap<string, unknown>({}),
};
export const entitiesInitializer = ImmutableRecord({ entities: ImmutableRecord(entities)() })();
export type EntitiesJS = {
  files: Record<Document['document_id'], unknown>;
  users: Record<User['user_id'], unknown>;
  groups: Record<Group['group_id'], unknown>;
  roles: Record<RoleOutput['role_id'], unknown>;
  grants: Record<Grant['grant_id'], unknown>;
  organizations: Record<Organization['organization_id'], unknown>;
  tokens: Record<Token['token_id'], unknown>;
  exercises: Record<Exercise['exercise_id'], unknown>;
  objectives: Record<Objective['objective_id'], unknown>;
  evaluations: Record<Evaluation['evaluation_id'], unknown>;
  comchecks: Record<Comcheck['comcheck_id'], unknown>;
  comcheckstatuses: Record<ComcheckStatus['comcheckstatus_id'], unknown>;
  channelreaders: Record<ChannelReader['channel_id'], unknown>;
  simulationchallengesreaders: Record<SimulationChallengesReader['exercise_id'], unknown>;
  scenariochallengesreaders: Record<ScenarioChallengesReader['scenario_id'], unknown>;
  teams: Record<Team['team_id'], unknown>;
  injects: Record<Inject['inject_id'], unknown>;
  atomics: Record<Inject['inject_id'], unknown>;
  // atomicdetails: ImmutableMap({}),
  // targetresults: ImmutableMap({}),
  injectorcontracts: Record<InjectorContract['injector_contract_id'], unknown>;
  injectstatuses: Record<InjectStatus['status_id'], unknown>;
  communications: Record<Communication['communication_id'], unknown>;
  logs: Record<Log['log_id'], unknown>;
  tags: Record<Tag['tag_id'], unknown>;
  documents: Record<Document['document_id'], unknown>;
  channels: Record<Channel['channel_id'], unknown>;
  payloads: Record<Payload['payload_id'], unknown>;
  challenges: Record<Challenge['challenge_id'], unknown>;
  articles: Record<Article['article_id'], unknown>;
  injectexpectations: Record<InjectExpectation['inject_expectation_id'], unknown>;
  lessonstemplates: Record<LessonsTemplate['lessonstemplate_id'], unknown>;
  lessonstemplatecategorys: Record<LessonsTemplateCategory['lessonstemplatecategory_id'], unknown>;
  lessonstemplatequestions: Record<LessonsTemplateQuestion['lessonstemplatequestion_id'], unknown>;
  lessonscategorys: Record<LessonsCategory['lessonscategory_id'], unknown>;
  lessonsquestions: Record<LessonsQuestion['lessonsquestion_id'], unknown>;
  lessonsanswers: Record<LessonsAnswer['lessonsanswer_id'], unknown>;
  reports: Record<Report['report_id'], unknown>;
  variables: Record<Variable['variable_id'], unknown>;
  killchainphases: Record<KillChainPhase['phase_id'], unknown>;
  attackpatterns: Record<AttackPattern['attack_pattern_id'], unknown>;
  endpoints: Record<Endpoint['asset_id'], unknown>;
  asset_groups: Record<AssetGroup['asset_group_id'], unknown>;
  securityplatforms: Record<SecurityPlatform['asset_id'], unknown>;
  scenarios: Record<Scenario['scenario_id'], unknown>;
  injectors: Record<Injector['injector_id'], unknown>;
  collectors: Record<Collector['collector_id'], unknown>;
  executors: Record<Executor['executor_id'], unknown>;
  mitigations: Record<Mitigation['mitigation_id'], unknown>;
  customdashboards: Record<CustomDashboard['custom_dashboard_id'], unknown>;
  agents: Record<Agent['agent_id'], unknown>;
  platformParameters: Record<string, unknown>;
  domains: Record<Domain['domain_id'], unknown>;
  catalog_connectors: Record<string, unknown>;
};
export type EntityKeys = keyof typeof entities;
export type EntityTypes
  = | Document
    | User
    | Group
    | RoleOutput
    | Grant
    | Organization
    | Token
    | Exercise
    | Objective
    | Evaluation
    | Comcheck
    | ComcheckStatus
    | ChannelReader
    | SimulationChallengesReader
    | ScenarioChallengesReader
    | Team
    | Inject
    | InjectorContract
    | InjectStatus
    | Communication
    | Log
    | Tag
    | Channel
    | Payload
    | Challenge
    | Article
    | InjectExpectation
    | LessonsTemplate
    | LessonsTemplateCategory
    | LessonsTemplateQuestion
    | LessonsCategory
    | LessonsQuestion
    | LessonsAnswer
    | Report
    | Variable
    | KillChainPhase
    | AttackPattern
    | Endpoint
    | AssetGroup
    | SecurityPlatform
    | Scenario
    | Injector
    | Collector
    | Executor
    | Mitigation
    | CustomDashboard
    | Agent
    | PlatformSettings
    | Domain
    | CatalogConnectorOutput;
