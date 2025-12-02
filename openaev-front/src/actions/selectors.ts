import { List as ImmutableList, Map as ImmutableMap, type Record as ImmutableRecord, Seq as ImmutableSeq } from 'immutable';
import { type schema } from 'normalizr';

import { type EntityKeys, type EntityTypes } from '../reducers/entities';
import { type Logged, type StoreState } from '../store';
import { type Article, type Challenge, type Inject, type LessonsAnswer, type LessonsCategory, type LessonsQuestion, type LessonsTemplateCategory, type Mitigation, type Objective, type PlatformSettings, type Report, type Tag, type Team, type User, type Variable } from '../utils/api-types';
import locale from '../utils/BrowserLanguage.js';
import {
  article,
  assetGroup,
  attackPattern,
  catalogConnector,
  challenge,
  channel,
  channelReader,
  collector,
  comcheck,
  comcheckStatus,
  communication,
  document,
  domain,
  endpoint,
  evaluation,
  executor,
  exercise,
  group,
  inject,
  injectexpectation,
  injector,
  injectorContract,
  killChainPhase,
  lessonsAnswer,
  lessonsCategory,
  lessonsQuestion,
  lessonsTemplate,
  lessonsTemplateCategory,
  lessonsTemplateQuestion,
  log,
  mitigation,
  objective,
  organization,
  payload,
  platformParameters,
  report,
  role,
  scenario,
  scenarioChallengesReader,
  securityPlatform,
  simulationChallengesReader,
  tag,
  team,
  token,
  user,
  variable,
} from './schemas';

type Entities<T extends object> = Omit<ImmutableSeq.Indexed<ImmutableRecord<T>>, 'toJS'> & { toJS(): T[] };

// Note: We rewrite TypeScript type definitions because the original types become
// destructured/malformed when converting to immutable structures.

// Since we can push objects directly from streams into the store, we use fromJS() which has
// loose typing. The deep immutable conversion destructures the original type shape, making
// ImmutableRecord<T> an inaccurate representation of the actual runtime type structure.
const maps = <T extends EntityTypes>(schema: schema.Entity<T, EntityKeys>, state: StoreState) =>
  state.referential.entities[schema.key] as Omit<ImmutableMap<string, ImmutableRecord<T>>, 'toJS'> & { toJS(): Record<string, T | undefined> };
const entities = <T extends EntityTypes>(schema: schema.Entity<T, EntityKeys>, state: StoreState) =>
  state.referential.entities[schema.key].valueSeq() as Entities<T>;
const entity = <T extends EntityTypes>(id: string, schema: schema.Entity<T, EntityKeys>, state: StoreState) =>
  state.referential.entities[schema.key].get(id) as Omit<ImmutableRecord<T>, 'toJS'> & { toJS(): T | undefined };
const me = (state: StoreState) => state.referential.entities.users.get((state.app.logged as unknown as ImmutableRecord<Logged>).get('user') || '') as Omit<ImmutableRecord<User>, 'toJS'> & { toJS(): User | undefined };

// Authentication & User
export const getLoggedSelector = (state: StoreState) => state.app.get('logged') as unknown as { toJS(): Logged };
export const getMeSelector = (state: StoreState) => me(state);
export const getMeAdminSelector = (state: StoreState) =>
  me(state)?.get('user_admin') ?? false;
export const getMeTokensSelector = (state: StoreState) =>
  entities(token, state).filter(
    t => t.get('token_user') === me(state)?.get('user_id'),
  );
export const getUserLangSelector = (state: StoreState) => {
  const rawPlatformLang
    = state.referential.getIn(['entities', platformParameters.key, 'parameters', 'platform_lang']) as string ?? 'auto';
  const rawUserLang = me(state)?.get('user_lang') ?? 'auto';
  const platformLang = rawPlatformLang !== 'auto' ? rawPlatformLang : locale as string;
  const userLang = rawUserLang !== 'auto' ? rawUserLang : platformLang;
  return userLang;
};

// Exercises
export const getExercisesSelector = (state: StoreState) =>
  entities(exercise, state);
export const getExercisesMapSelector = (state: StoreState) =>
  maps(exercise, state);
export const getExerciseSelector = (id: string, state: StoreState) =>
  entity(id, exercise, state);
export const getExerciseComchecksSelector = (id: string, state: StoreState) =>
  entities(comcheck, state).filter(
    i => i.get('comcheck_exercise') === id,
  );
export const getExerciseTeamsSelector = (id: string, state: StoreState) =>
  entities(team, state).filter(i => i.get('team_exercises')?.includes(id)) as Entities<Team>;
export const getExerciseVariablesSelector = (id: string, state: StoreState) =>
  entities(variable, state).filter((i) => {
    const ooo = i.get('variable_exercise');
    const test = ooo === id;
    return test;
  });
export const getExerciseArticlesSelector = (id: string, state: StoreState) =>
  entities(article, state).filter(i => i.get('article_exercise') === id);
export const getExerciseInjectsSelector = (id: string, state: StoreState) =>
  entities(inject, state).filter(i => i.get('inject_exercise') === id) as Entities<Inject>;
export const getExerciseCommunicationsSelector = (id: string, state: StoreState) =>
  entities(communication, state).filter(
    i => i.get('communication_exercise') === id,
  );
export const getExerciseObjectivesSelector = (id: string, state: StoreState) =>
  entities(objective, state).filter(o => o.get('objective_exercise') === id) as Entities<Objective>;
export const getExerciseLogsSelector = (id: string, state: StoreState) =>
  entities(log, state).filter(l => l.get('log_exercise') === id);
export const getExerciseLessonsCategoriesSelector = (id: string, state: StoreState) =>
  entities(lessonsCategory, state).filter(
    l => l.get('lessons_category_exercise') === id,
  ) as Entities<LessonsCategory>;
export const getExerciseLessonsQuestionsSelector = (id: string, state: StoreState) =>
  entities(lessonsQuestion, state).filter(
    l => l.get('lessons_question_exercise') === id,
  ) as Entities<LessonsQuestion>;
export const getExerciseLessonsAnswersSelector = (exerciseId: string, state: StoreState) =>
  entities(lessonsAnswer, state).filter(
    l => l.get('lessons_answer_exercise') === exerciseId,
  ) as Entities<LessonsAnswer>;
export const getExerciseUserLessonsAnswersSelector = (exerciseId: string, userId: string, state: StoreState) =>
  entities(lessonsAnswer, state).filter(
    l =>
      l.get('lessons_answer_exercise') === exerciseId
      && l.get('lessons_answer_user') === userId,
  );
export const isExerciseSelector = (id: string, state: StoreState) =>
  !maps(exercise, state)?.has(id);
export const getExerciseReportsSelector = (exerciseId: string, state: StoreState) =>
  entities(report, state).filter(
    l => l.get('report_exercise') === exerciseId,
  ) as Entities<Report>;

// Tags
export const getTagSelector = (id: Tag['tag_id'], state: StoreState) =>
  entity(id, tag, state);
export const getTagsSelector = (state: StoreState) =>
  entities(tag, state);
export const getTagsMapSelector = (state: StoreState) =>
  maps(tag, state);

// Reports
export const getReportSelector = (id: string, state: StoreState) =>
  entity(id, report, state);

// Comcheck
export const getComcheckSelector = (id: string, state: StoreState) =>
  entity(id, comcheck, state);
export const getComcheckStatusSelector = (id: string, state: StoreState) =>
  entity(id, comcheckStatus, state);
export const getComcheckStatusesSelector = (id: string, state: StoreState) =>
  entities(comcheckStatus, state).filter(
    i => i.get('comcheckstatus_comcheck') === id,
  );
export const getChannelReaderSelector = (id: string, state: StoreState) =>
  entity(id, channelReader, state);
export const getSimulationChallengesReaderSelector = (id: string, state: StoreState) =>
  entity(id, simulationChallengesReader, state);
export const getScenarioChallengesReaderSelector = (id: string, state: StoreState) =>
  entity(id, scenarioChallengesReader, state);

// Users & Organizations
export const getUsersSelector = (state: StoreState) =>
  entities(user, state);
export const getGroupSelector = (id: string, state: StoreState) =>
  entity(id, group, state);
export const getGroupsSelector = (state: StoreState) =>
  entities(group, state);
export const getRolesSelector = (state: StoreState) =>
  entities(role, state);
export const getUsersMapSelector = (state: StoreState) =>
  maps(user, state);
export const getOrganizationsSelector = (state: StoreState) =>
  entities(organization, state);
export const getOrganizationsMapSelector = (state: StoreState) =>
  maps(organization, state);

// Objectives
export const getObjectiveSelector = (id: string, state: StoreState) =>
  entity(id, objective, state);
export const getObjectiveEvaluationsSelector = (id: string, state: StoreState) =>
  entities(evaluation, state).filter(
    e => e.get('evaluation_objective') === id,
  );

// Injects & Related
export const getInjectSelector = (id: string, state: StoreState) =>
  entity(id, inject, state);
export const getAtomicTestingSelector = (id: string, state: StoreState) =>
  entity(id, inject, state);
// export const getAtomicTestingDetailSelector = (id: string, state: StoreState) =>
//   entity(id, atomicdetail, state);
export const getAtomicTestingsSelector = (state: StoreState) =>
  entities(inject, state);
// export const getTargetResultsSelector = (id: string, injectId: string, state: StoreState) =>
//   entities(targetresult, state).filter(r => r.get('target_id') === id && r.get('target_inject_id') === injectId);
export const getInjectsMapSelector = (state: StoreState) =>
  maps(inject, state);
export const getInjectCommunicationsSelector = (id: string, state: StoreState) =>
  entities(communication, state).filter(i => i.get('communication_inject') === id);

// Inject Expectations
export const getInjectExpectationsSelector = (state: StoreState) =>
  entities(injectexpectation, state);
export const getExerciseInjectExpectationsSelector = (id: string, state: StoreState) =>
  entities(injectexpectation, state).filter(i => i.get('inject_expectation_exercise') === id);
export const getInjectExpectationsMapSelector = (state: StoreState) =>
  maps(injectexpectation, state);

// Documents
export const getDocumentsSelector = (state: StoreState) =>
  entities(document, state);
export const getDocumentsMapSelector = (state: StoreState) =>
  maps(document, state);

// Teams
export const getTeamSelector = (id: string, state: StoreState) =>
  entity(id, team, state);
export const getTeamUsersSelector = (id: string, state: StoreState) => {
  const selectTeam = entity(id, team, state);
  const selectUsers = selectTeam
    ?.get('team_users')
    ?.map(tu => entity(tu, user, state))
    ?.filter(u => !!u) as unknown as Entities<User>; ;
  if (!selectTeam || !selectUsers) return ImmutableSeq.Indexed([]);
  return selectUsers;
};
export const getTeamExerciseInjectsSelector = (id: string, state: StoreState) => {
  const selectTeam = entity(id, team, state);
  if (!selectTeam) return ImmutableList([]);
  return selectTeam
    .get('team_exercise_injects')
    ?.map(te => entity(te, inject, state))
    ?.filter(i => !!i);
};
export const getTeamsSelector = (state: StoreState) =>
  entities(team, state);
export const getTeamsMapSelector = (state: StoreState) =>
  maps(team, state);

// Platform Settings
export const getPlatformSettingsSelector = (state: StoreState) =>
  (state.referential.getIn(['entities', platformParameters.key, 'parameters'])
    || ImmutableMap({})) as Omit<ImmutableRecord<PlatformSettings>, 'toJS'> & { toJS(): PlatformSettings | undefined };
export const getPlatformNameSelector = (state: StoreState) =>
  state.referential.getIn([
    'entities',
    platformParameters.key,
    'parameters',
    'platform_name',
  ]) as string || 'OpenBAS - Breach and Attack Simulation Platform';

// Kill Chain Phases
export const getKillChainPhaseSelector = (id: string, state: StoreState) =>
  entity(id, killChainPhase, state);
export const getKillChainPhasesSelector = (state: StoreState) =>
  entities(killChainPhase, state);
export const getKillChainPhasesMapSelector = (state: StoreState) =>
  maps(killChainPhase, state);

// Attack Patterns
export const getAttackPatternSelector = (id: string, state: StoreState) =>
  entity(id, attackPattern, state);
export const getAttackPatternsSelector = (state: StoreState) =>
  entities(attackPattern, state);
export const getAttackPatternsMapSelector = (state: StoreState) =>
  maps(attackPattern, state);

// Mitigations
export const getMitigationSelector = (id: Mitigation['mitigation_id'], state: StoreState) =>
  entity(id, mitigation, state);
export const getMitigationsSelector = (state: StoreState) =>
  entities(mitigation, state);
export const getMitigationsMapSelector = (state: StoreState) =>
  maps(mitigation, state);

// Injectors
export const getInjectorSelector = (id: string, state: StoreState) =>
  entity(id, injector, state);
export const getInjectorsSelector = (state: StoreState) =>
  entities(injector, state);
export const getInjectorsMapSelector = (state: StoreState) =>
  maps(injector, state);
export const getInjectorContractSelector = (id: string, state: StoreState) => entity(id, injectorContract, state);
export const getInjectorContractsSelector = (state: StoreState) =>
  entities(injectorContract, state);

// Collectors
export const getCollectorSelector = (id: string, state: StoreState) =>
  entity(id, collector, state);
export const getCollectorsSelector = (state: StoreState) =>
  entities(collector, state);
export const getCollectorsMapSelector = (state: StoreState) =>
  maps(collector, state);

// Executors
export const getExecutorSelector = (id: string, state: StoreState) =>
  entity(id, executor, state);
export const getExecutorsSelector = (state: StoreState) =>
  entities(executor, state);
export const getExecutorsMapSelector = (state: StoreState) =>
  maps(executor, state);

// Channels
export const getChannelsSelector = (state: StoreState) =>
  entities(channel, state);
export const getChannelSelector = (id: string, state: StoreState) =>
  entity(id, channel, state);
export const getChannelsMapSelector = (state: StoreState) =>
  maps(channel, state);

// Payloads
export const getPayloadsSelector = (state: StoreState) =>
  entities(payload, state);
export const getPayloadSelector = (id: string, state: StoreState) =>
  entity(id, payload, state);
export const getPayloadsMapSelector = (state: StoreState) =>
  maps(payload, state);

// Articles
export const getArticlesSelector = (state: StoreState) =>
  entities(article, state);
export const getArticleSelector = (id: string, state: StoreState) =>
  entity(id, article, state);
export const getArticlesMapSelector = (state: StoreState) =>
  maps(article, state);

// Challenges
export const getChallengesSelector = (state: StoreState) =>
  entities(challenge, state);
export const getExerciseChallengesSelector = (id: string, state: StoreState) =>
  entities(challenge, state).filter(c => c.get('challenge_exercises')?.includes(id));
export const getChallengesMapSelector = (state: StoreState) =>
  maps(challenge, state);

// Lessons Templates
export const getLessonsTemplateSelector = (id: string, state: StoreState) =>
  entity(id, lessonsTemplate, state);
export const getLessonsTemplatesSelector = (state: StoreState) =>
  entities(lessonsTemplate, state);
export const getLessonsTemplatesMapSelector = (state: StoreState) =>
  maps(lessonsTemplate, state);
export const getLessonsTemplateCategoriesSelector = (id: string, state: StoreState) =>
  entities(lessonsTemplateCategory, state).filter(
    c => c.get('lessons_template_category_template') === id,
  ) as Entities<LessonsTemplateCategory>;
export const getLessonsTemplateQuestionsSelector = (state: StoreState) =>
  entities(lessonsTemplateQuestion, state);
export const getLessonsTemplateQuestionsMapSelector = (state: StoreState) =>
  maps(lessonsTemplateQuestion, state);
export const getLessonsTemplateCategoryQuestionsSelector = (id: string, state: StoreState) =>
  entities(lessonsTemplateQuestion, state).filter(
    c => c.get('lessons_template_question_category') === id,
  );

// Assets & Groups
export const getEndpointSelector = (id: string, state: StoreState) =>
  entity(id, endpoint, state);
export const getEndpointsSelector = (state: StoreState) =>
  entities(endpoint, state);
export const getEndpointsMapSelector = (state: StoreState) =>
  maps(endpoint, state);
export const getAssetGroupsSelector = (state: StoreState) =>
  entities(assetGroup, state);
export const getAssetGroupMapsSelector = (state: StoreState) =>
  maps(assetGroup, state);
export const getAssetGroupSelector = (id: string, state: StoreState) =>
  entity(id, assetGroup, state);

// Security Platforms
export const getSecurityPlatformsSelector = (state: StoreState) =>
  entities(securityPlatform, state);
export const getSecurityPlatformsMapSelector = (state: StoreState) =>
  maps(securityPlatform, state);
export const getSecurityPlatformSelector = (id: string, state: StoreState) =>
  entity(id, securityPlatform, state);

// Scenarios
export const getScenariosSelector = (state: StoreState) =>
  entities(scenario, state);
export const getScenariosMapSelector = (state: StoreState) =>
  maps(scenario, state);
export const getScenarioSelector = (id: string, state: StoreState) =>
  entity(id, scenario, state);
export const getScenarioTeamsSelector = (id: string, state: StoreState) =>
  entities(team, state).filter(i =>
    i.get('team_scenarios')?.includes(id),
  ) as Entities<Team>;
export const getScenarioVariablesSelector = (id: string, state: StoreState) =>
  entities(variable, state).filter(
    i => i.get('variable_scenario') === id,
  ) as Entities<Variable>;
export const getScenarioArticlesSelector = (id: string, state: StoreState) =>
  entities(article, state).filter(
    i => i.get('article_scenario') === id,
  ) as Entities<Article>;
export const getScenarioChallengesSelector = (id: string, state: StoreState) =>
  entities(challenge, state).filter(c =>
    c.get('challenge_scenarios')?.includes(id),
  ) as Entities<Challenge>;
export const getScenarioInjectsSelector = (id: string, state: StoreState) =>
  entities(inject, state).filter(
    i => i.get('inject_scenario') === id,
  ) as Entities<Inject>;
export const getTeamScenarioInjectsSelector = (id: string, state: StoreState) => {
  const selectTeam = entity(id, team, state);
  if (!selectTeam) return ImmutableList([]);
  return selectTeam
    .get('team_scenario_injects')
    ?.map(te => entity(te, inject, state))
    ?.filter(i => !!i);
};
export const getScenarioObjectivesSelector = (id: string, state: StoreState) =>
  entities(objective, state).filter(
    o => o.get('objective_scenario') === id,
  ) as Entities<Objective>;
export const getScenarioLessonsCategoriesSelector = (id: string, state: StoreState) =>
  entities(lessonsCategory, state).filter(
    l => l.get('lessons_category_scenario') === id,
  ) as Entities<LessonsCategory>;
export const getScenarioLessonsQuestionsSelector = (id: string, state: StoreState) =>
  entities(lessonsQuestion, state).filter(
    l => l.get('lessons_question_scenario') === id,
  ) as Entities<LessonsQuestion>;

// Domain
export const getDomainsSelector = (state: StoreState) => entities(domain, state);

// Catalog connector
export const getCatalogConnectorsSelector = (state: StoreState) => entities(catalogConnector, state);
export const getCatalogConnectorSelector = (id: string, state: StoreState) => entity(id, catalogConnector, state);
