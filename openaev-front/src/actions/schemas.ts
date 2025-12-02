import { schema } from 'normalizr';

import { type EntityKeys } from '../reducers/entities';
import type {
  Article,
  AssetGroup,
  AttackPattern,
  CatalogConnectorOutput,
  Challenge,
  Channel,
  ChannelReader,
  Collector,
  Comcheck,
  ComcheckStatus,
  Communication,
  CustomDashboard,
  Document,
  Domain,
  Endpoint,
  Evaluation,
  Executor,
  Exercise,
  Grant,
  Group,
  Inject,
  InjectExpectation,
  Injector,
  InjectorContract,
  InjectStatus,
  KillChainPhase,
  LessonsAnswer,
  LessonsCategory,
  LessonsQuestion,
  LessonsTemplate,
  LessonsTemplateCategory,
  LessonsTemplateQuestion,
  Log,
  Mitigation,
  Objective,
  Organization,
  Payload,
  PlatformSettings,
  Report,
  RoleOutput,
  Scenario,
  ScenarioChallengesReader,
  SecurityPlatform,
  SimulationChallengesReader,
  Tag,
  Team,
  Token,
  User,
  Variable,
} from '../utils/api-types';

export const document = new schema.Entity<Document, EntityKeys>(
  'documents',
  {},
  { idAttribute: 'document_id' },
);
export const arrayOfDocuments = new schema.Array(document);

export const article = new schema.Entity<Article, EntityKeys>(
  'articles',
  {},
  { idAttribute: 'article_id' },
);
export const arrayOfArticles = new schema.Array(article);

export const channel = new schema.Entity<Channel, EntityKeys>(
  'channels',
  {},
  { idAttribute: 'channel_id' },
);
export const arrayOfChannels = new schema.Array(channel);

export const assetGroup = new schema.Entity<AssetGroup, EntityKeys>(
  'asset_groups',
  {},
  { idAttribute: 'asset_group_id' },
);
export const arrayOfAssetGroups = new schema.Array(assetGroup);

export const endpoint = new schema.Entity<Endpoint, EntityKeys>(
  'endpoints',
  {},
  { idAttribute: 'asset_id' },
);
export const arrayOfEndpoints = new schema.Array(endpoint);

export const securityPlatform = new schema.Entity<SecurityPlatform, EntityKeys>(
  'securityplatforms',
  {},
  { idAttribute: 'asset_id' },
);
export const arrayOfSecurityPlatforms = new schema.Array(securityPlatform);

export const scenario = new schema.Entity<Scenario, EntityKeys>(
  'scenarios',
  {},
  { idAttribute: 'scenario_id' },
);
export const arrayOfScenarios = new schema.Array(scenario);

export const challenge = new schema.Entity<Challenge, EntityKeys>(
  'challenges',
  {},
  { idAttribute: 'challenge_id' },
);
export const arrayOfChallenges = new schema.Array(challenge);

export const tag = new schema.Entity<Tag, EntityKeys>('tags', {}, { idAttribute: 'tag_id' });
export const arrayOfTags = new schema.Array(tag);

export const injectorContract = new schema.Entity<InjectorContract, EntityKeys>(
  'injectorcontracts',
  {},
  { idAttribute: 'injector_contract_id' },
);
export const arrayOfInjectorContracts = new schema.Array(injectorContract);

export const injectStatus = new schema.Entity<InjectStatus, EntityKeys>(
  'injectstatuses',
  {},
  { idAttribute: 'status_id' },
);
export const arrayOfInjectStatuses = new schema.Array(injectStatus);

export const platformParameters = new schema.Entity<PlatformSettings, 'platformParameters'>(
  'platformParameters',
  {},
  { idAttribute: () => 'parameters' },
);

export const token = new schema.Entity<Token, EntityKeys>(
  'tokens',
  {},
  { idAttribute: 'token_id' },
);
export const arrayOfTokens = new schema.Array(token);

export const organization = new schema.Entity<Organization, EntityKeys>(
  'organizations',
  {},
  { idAttribute: 'organization_id' },
);
export const arrayOfOrganizations = new schema.Array(organization);

export const group = new schema.Entity<Group, EntityKeys>(
  'groups',
  {},
  { idAttribute: 'group_id' },
);
export const arrayOfGroups = new schema.Array(group);

export const grant = new schema.Entity<Grant, EntityKeys>(
  'grants',
  {},
  { idAttribute: 'grant_id' },
);
export const arrayOfGrants = new schema.Array(grant);

export const user = new schema.Entity<User, EntityKeys>('users', {}, { idAttribute: 'user_id' });
export const arrayOfUsers = new schema.Array(user);

export const role = new schema.Entity<RoleOutput, EntityKeys>(
  'roles',
  {},
  { idAttribute: 'role_id' },
);
export const arrayOfRoles = new schema.Array(role);

export const exercise = new schema.Entity<Exercise, EntityKeys>(
  'exercises',
  {},
  { idAttribute: 'exercise_id' },
);
export const arrayOfExercises = new schema.Array(exercise);

export const objective = new schema.Entity<Objective, EntityKeys>(
  'objectives',
  {},
  { idAttribute: 'objective_id' },
);
export const arrayOfObjectives = new schema.Array(objective);

export const evaluation = new schema.Entity<Evaluation, EntityKeys>(
  'evaluations',
  {},
  { idAttribute: 'evaluation_id' },
);
export const arrayOfEvaluations = new schema.Array(evaluation);

export const comcheck = new schema.Entity<Comcheck, EntityKeys>(
  'comchecks',
  {},
  { idAttribute: 'comcheck_id' },
);
export const arrayOfComchecks = new schema.Array(comcheck);

export const comcheckStatus = new schema.Entity<ComcheckStatus, EntityKeys>(
  'comcheckstatuses',
  {},
  { idAttribute: 'comcheckstatus_id' },
);
export const arrayOfComcheckStatuses = new schema.Array(comcheckStatus);

export const team = new schema.Entity<Team, EntityKeys>(
  'teams',
  {},
  { idAttribute: 'team_id' },
);
export const arrayOfTeams = new schema.Array(team);

export const inject = new schema.Entity<Inject, EntityKeys>(
  'injects',
  {},
  { idAttribute: 'inject_id' },
);
export const arrayOfInjects = new schema.Array(inject);

export const communication = new schema.Entity<Communication, EntityKeys>(
  'communications',
  {},
  { idAttribute: 'communication_id' },
);
export const arrayOfCommunications = new schema.Array(communication);

export const log = new schema.Entity<Log, EntityKeys>('logs', {}, { idAttribute: 'log_id' });
export const arrayOfLogs = new schema.Array(log);

export const channelReader = new schema.Entity<ChannelReader, EntityKeys>(
  'channelreaders',
  {},
  { idAttribute: 'channel_id' },
);
export const simulationChallengesReader = new schema.Entity<SimulationChallengesReader, EntityKeys>(
  'simulationchallengesreaders',
  {},
  { idAttribute: 'exercise_id' },
);
export const scenarioChallengesReader = new schema.Entity<ScenarioChallengesReader, EntityKeys>(
  'scenariochallengesreaders',
  {},
  { idAttribute: 'scenario_id' },
);
export const injectexpectation = new schema.Entity<InjectExpectation, EntityKeys>(
  'injectexpectations',
  {},
  { idAttribute: 'inject_expectation_id' },
);
export const arrayOfInjectexpectations = new schema.Array(injectexpectation);

export const lessonsTemplate = new schema.Entity<LessonsTemplate, EntityKeys>(
  'lessonstemplates',
  {},
  { idAttribute: 'lessonstemplate_id' },
);
export const arrayOfLessonsTemplates = new schema.Array(lessonsTemplate);

export const lessonsTemplateCategory = new schema.Entity<LessonsTemplateCategory, EntityKeys>(
  'lessonstemplatecategorys',
  {},
  { idAttribute: 'lessonstemplatecategory_id' },
);
export const arrayOfLessonsTemplateCategories = new schema.Array(
  lessonsTemplateCategory,
);

export const lessonsTemplateQuestion = new schema.Entity<LessonsTemplateQuestion, EntityKeys>(
  'lessonstemplatequestions',
  {},
  { idAttribute: 'lessonstemplatequestion_id' },
);
export const arrayOfLessonsTemplateQuestions = new schema.Array(
  lessonsTemplateQuestion,
);

export const lessonsCategory = new schema.Entity<LessonsCategory, EntityKeys>(
  'lessonscategorys',
  {},
  { idAttribute: 'lessonscategory_id' },
);
export const arrayOfLessonsCategories = new schema.Array(lessonsCategory);

export const lessonsQuestion = new schema.Entity<LessonsQuestion, EntityKeys>(
  'lessonsquestions',
  {},
  { idAttribute: 'lessonsquestion_id' },
);
export const arrayOfLessonsQuestions = new schema.Array(lessonsQuestion);

export const lessonsAnswer = new schema.Entity<LessonsAnswer, EntityKeys>(
  'lessonsanswers',
  {},
  { idAttribute: 'lessonsanswer_id' },
);
export const arrayOfLessonsAnswers = new schema.Array(lessonsAnswer);

export const report = new schema.Entity<Report, EntityKeys>(
  'reports',
  {},
  { idAttribute: 'report_id' },
);
export const arrayOfReports = new schema.Array(report);

export const variable = new schema.Entity<Variable, EntityKeys>(
  'variables',
  {},
  { idAttribute: 'variable_id' },
);
export const arrayOfVariables = new schema.Array(variable);

export const killChainPhase = new schema.Entity<KillChainPhase, EntityKeys>(
  'killchainphases',
  {},
  { idAttribute: 'phase_id' },
);
export const arrayOfKillChainPhases = new schema.Array(killChainPhase);

export const attackPattern = new schema.Entity<AttackPattern, EntityKeys>(
  'attackpatterns',
  {},
  { idAttribute: 'attack_pattern_id' },
);
export const arrayOfAttackPatterns = new schema.Array(attackPattern);

export const injector = new schema.Entity<Injector, EntityKeys>(
  'injectors',
  {},
  { idAttribute: 'injector_id' },
);
export const arrayOfInjectors = new schema.Array(injector);

export const collector = new schema.Entity<Collector, EntityKeys>(
  'collectors',
  {},
  { idAttribute: 'collector_id' },
);
export const arrayOfCollectors = new schema.Array(collector);

export const executor = new schema.Entity<Executor, EntityKeys>(
  'executors',
  {},
  { idAttribute: 'executor_id' },
);
export const arrayOfExecutors = new schema.Array(executor);

export const payload = new schema.Entity<Payload, EntityKeys>(
  'payloads',
  {},
  { idAttribute: 'payload_id' },
);
export const arrayOfPayloads = new schema.Array(payload);

export const mitigation = new schema.Entity<Mitigation, EntityKeys>(
  'mitigations',
  {},
  { idAttribute: 'mitigation_id' },
);
export const arrayOfMitigations = new schema.Array(mitigation);

export const customDashboard = new schema.Entity<CustomDashboard, EntityKeys>(
  'customdashboards',
  {},
  { idAttribute: 'custom_dashboard_id' },
);
export const arrayOfCustomDashboards = new schema.Array(customDashboard);

export const agent = new schema.Entity(
  'agents',
  {},
  { idAttribute: 'agent_id' },
);
export const arrayOfAgents = new schema.Array(agent);

export const domain = new schema.Entity<Domain, EntityKeys>(
  'domains',
  {},
  { idAttribute: 'domain_id' },
);
export const arrayOfDomains = new schema.Array(domain);

export const catalogConnector = new schema.Entity<CatalogConnectorOutput, EntityKeys>(
  'catalog_connectors',
  {},
  { idAttribute: 'catalog_connector_id' },
);
export const arrayOfCatalogConnectors = new schema.Array(catalogConnector);

token.define({ token_user: user });
user.define({ user_organization: organization });
