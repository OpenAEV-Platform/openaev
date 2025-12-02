import { fromJS, List, Map } from 'immutable';

import locale from '../utils/BrowserLanguage.js';

const maps = (key, state) => state.referential.getIn(['entities', key]);
const entities = (key, state) => maps(key, state).valueSeq();
const entity = (id, key, state) => state.referential.getIn(['entities', key, id]);
const me = state => state.referential.getIn(['entities', 'users', state.app.getIn(['logged', 'user'])]);

// eslint-disable-next-line import/prefer-default-export
export const storeHelper = state => ({
  logged: () => state.app.get('logged'),
  getMe: () => me(state),
  getMeAdmin: () => me(state)?.get('user_admin') ?? false,
  getMeTokens: () => entities('tokens', state).filter(
    t => t.get('token_user') === me(state)?.get('user_id'),
  ),
  getUserLang: () => {
    const rawPlatformLang = state.referential.getIn(['entities', 'platformParameters', 'parameters', 'platform_lang']) ?? 'auto';
    const rawUserLang = me(state)?.get('user_lang') ?? 'auto';
    const platformLang = rawPlatformLang !== 'auto' ? rawPlatformLang : locale;
    const userLang = rawUserLang !== 'auto' ? rawUserLang : platformLang;
    return userLang;
  },
  getStatistics: () => state.referential.getIn(['entities', 'statistics', 'openbas']),
  // exercises
  getExercises: () => entities('exercises', state),
  getExercisesMap: () => maps('exercises', state),
  getExercise: id => entity(id, 'exercises', state),
  getExerciseComchecks: id => entities('comchecks', state).filter(i => i.get('comcheck_exercise') === id),
  getExerciseTeams: id => entities('teams', state).filter(i => i.get('team_exercises')?.includes(id)),
  getExerciseVariables: id => entities('variables', state).filter(i => i.get('variable_exercise') === id),
  getExerciseArticles: id => entities('articles', state).filter(i => i.get('article_exercise') === id),
  getExerciseInjects: id => entities('injects', state).filter(i => i.get('inject_exercise') === id),
  getExerciseCommunications: id => entities('communications', state).filter(
    i => i.get('communication_exercise') === id,
  ),
  getExerciseObjectives: id => entities('objectives', state).filter(o => o.get('objective_exercise') === id),
  getExerciseLogs: id => entities('logs', state).filter(l => l.get('log_exercise') === id),
  getExerciseLessonsCategories: id => entities('lessonscategorys', state).filter(
    l => l.get('lessons_category_exercise') === id,
  ),
  getExerciseLessonsQuestions: id => entities('lessonsquestions', state).filter(
    l => l.get('lessons_question_exercise') === id,
  ),
  getExerciseLessonsAnswers: exerciseId => entities('lessonsanswers', state).filter(
    l => l.get('lessons_answer_exercise') === exerciseId,
  ),
  getExerciseUserLessonsAnswers: (exerciseId, userId) => entities('lessonsanswers', state).filter(
    l => l.get('lessons_answer_exercise') === exerciseId
      && l.get('lessons_answer_user') === userId,
  ),
  isExercise: id => !maps('exercises', state)?.get(id)?.isEmpty(),
  getExerciseReports: exerciseId => entities('reports', state).filter(l => l.get('report_exercise') === exerciseId),
  // report
  getReport: id => entity(id, 'reports', state),
  // comcheck
  getComcheck: id => entity(id, 'comchecks', state),
  getComcheckStatus: id => entity(id, 'comcheckstatuses', state),
  getComcheckStatuses: id => entities('comcheckstatuses', state).filter(
    i => i.get('comcheckstatus_comcheck') === id,
  ),
  getChannelReader: id => entity(id, 'channelreaders', state),
  getSimulationChallengesReader: id => entity(id, 'simulationchallengesreaders', state),
  getScenarioChallengesReader: id => entity(id, 'scenariochallengesreaders', state),
  // users
  getUsers: () => entities('users', state),
  getGroup: id => entity(id, 'groups', state),
  getGroups: () => entities('groups', state),
  getRoles: () => entities('roles', state),
  getUsersMap: () => maps('users', state),
  getOrganizations: () => entities('organizations', state),
  getOrganizationsMap: () => maps('organizations', state),
  // objectives
  getObjective: id => entity(id, 'objectives', state),
  getObjectiveEvaluations: id => entities('evaluations', state).filter(e => e.get('evaluation_objective') === id),
  // tags
  getTag: id => entity(id, 'tags', state),
  getTags: () => entities('tags', state),
  getTagsMap: () => maps('tags', state),

  // injects
  getInject: id => entity(id, 'injects', state),
  getAtomicTesting: id => entity(id, 'atomics', state),
  getAtomicTestingDetail: id => entity(id, 'atomicdetails', state),
  getAtomicTestings: () => entities('atomics', state),
  getTargetResults: (id, injectId) => entities('targetresults', state).filter(r => (r.get('target_id') === id) && (r.get('target_inject_id') === injectId)),
  getInjectsMap: () => maps('injects', state),
  getInjectCommunications: id => entities('communications', state).filter(
    i => i.get('communication_inject') === id,
  ),
  // injectexpectation
  getInjectExpectations: () => entities('injectexpectations', state),
  getExerciseInjectExpectations: id => entities('injectexpectations', state).filter(
    i => i.get('inject_expectation_exercise') === id,
  ),
  getInjectExpectationsMap: () => maps('injectexpectations', state),
  // documents
  getDocuments: () => entities('documents', state),
  getDocumentsMap: () => maps('documents', state),
  // teams
  getTeam: id => entity(id, 'teams', state),
  getTeamUsers: (id) => {
    const team = entity(id, 'teams', state);
    if (!team) return List([]);
    return team.get('team_users').map(tu => entity(tu, 'users', state)).filter(u => !!u);
  },
  getTeamExerciseInjects: (id) => {
    const team = entity(id, 'teams', state);
    if (!team) return List([]);
    return team.get('team_exercise_injects').map(te => entity(te, 'injects', state)).filter(i => !!i);
  },
  getTeams: () => entities('teams', state),
  getTeamsMap: () => maps('teams', state),
  getPlatformSettings: () => {
    return state.referential.getIn(['entities', 'platformParameters', 'parameters']) || Map({});
  },
  getPlatformName: () => {
    return state.referential.getIn(['entities', 'platformParameters', 'parameters', 'platform_name']) || 'OpenBAS - Breach and Attack Simulation Platform';
  },
  // kill chain phases
  getKillChainPhase: id => entity(id, 'killchainphases', state),
  getKillChainPhases: () => entities('killchainphases', state),
  getKillChainPhasesMap: () => maps('killchainphases', state),
  // attack patterns
  getAttackPattern: id => entity(id, 'attackpatterns', state),
  getAttackPatterns: () => entities('attackpatterns', state),
  getAttackPatternsMap: () => maps('attackpatterns', state),
  // mitigations
  getMitigation: id => entity(id, 'mitigations', state),
  getMitigations: () => entities('mitigations', state),
  getMitigationsMap: () => maps('mitigations', state),
  // injectors
  getInjector: id => entity(id, 'injectors', state),
  getInjectors: () => entities('injectors', state),
  getInjectorsMap: () => maps('injectors', state),
  // injector contracts
  getInjectorContract: (id) => {
    const i = entity(id, 'injectorcontracts', state);
    if (!i || i.isEmpty()) {
      return i;
    }
    return i.merge(fromJS(JSON.parse(i.get('injector_contract_content'))));
  },
  getInjectorContracts: () => entities('injectorcontracts', state),
  // collectors
  getCollector: id => entity(id, 'collectors', state),
  getCollectors: () => entities('collectors', state),
  getCollectorsMap: () => maps('collectors', state),
  // executors
  getExecutor: id => entity(id, 'executors', state),
  getExecutors: () => entities('executors', state),
  getExecutorsMap: () => maps('executors', state),
  // channels
  getChannels: () => entities('channels', state),
  getChannel: id => entity(id, 'channels', state),
  getChannelsMap: () => maps('channels', state),
  // payloads
  getPayloads: () => entities('payloads', state),
  getPayload: id => entity(id, 'payloads', state),
  getPayloadsMap: () => maps('payloads', state),
  // articles
  getArticles: () => entities('articles', state),
  getArticle: id => entity(id, 'articles', state),
  getArticlesMap: () => maps('articles', state),
  // challenges
  getChallenges: () => entities('challenges', state),
  getExerciseChallenges: id => entities('challenges', state).filter(c => c.get('challenge_exercises').includes(id)),
  getChallengesMap: () => maps('challenges', state),
  // lessons templates
  getLessonsTemplate: id => entity(id, 'lessonstemplates', state),
  getLessonsTemplates: () => entities('lessonstemplates', state),
  getLessonsTemplatesMap: () => maps('lessonstemplates', state),
  getLessonsTemplateCategories: id => entities('lessonstemplatecategorys', state).filter(
    c => c.get('lessons_template_category_template') === id,
  ),
  getLessonsTemplateQuestions: () => entities('lessonstemplatequestions', state),
  getLessonsTemplateQuestionsMap: () => maps('lessonstemplatequestions', state),
  getLessonsTemplateCategoryQuestions: id => entities('lessonstemplatequestions', state).filter(
    c => c.get('lessons_template_question_category') === id,
  ),
  // assets
  getEndpoint: id => entity(id, 'endpoints', state),
  getEndpoints: () => entities('endpoints', state),
  getEndpointsMap: () => maps('endpoints', state),
  // asset groups
  getAssetGroups: () => entities('asset_groups', state),
  getAssetGroupMaps: () => maps('asset_groups', state),
  getAssetGroup: id => entity(id, 'asset_groups', state),
  // security platforms
  getSecurityPlatforms: () => entities('securityplatforms', state),
  getSecurityPlatformsMap: () => maps('securityplatforms', state),
  getSecurityPlatform: id => entity(id, 'securityplatforms', state),
  // scenarios
  getScenarios: () => entities('scenarios', state),
  getScenariosMap: () => maps('scenarios', state),
  getScenario: id => entity(id, 'scenarios', state),
  getScenarioTeams: id => entities('teams', state).filter(i => i.get('team_scenarios').includes(id)),
  getScenarioVariables: id => entities('variables', state).filter(i => i.get('variable_scenario') === id),
  getScenarioArticles: id => entities('articles', state).filter(i => i.get('article_scenario') === id),
  getScenarioChallenges: id => entities('challenges', state).filter(c => c.get('challenge_scenarios').includes(id)),
  getScenarioInjects: id => entities('injects', state).filter(i => i.get('inject_scenario') === id),
  getTeamScenarioInjects: (id) => {
    const team = entity(id, 'teams', state);
    if (!team) return List([]);
    return team.get('team_scenario_injects').map(te => entity(te, 'injects', state)).filter(i => !!i);
  },
  getScenarioObjectives: id => entities('objectives', state).filter(o => o.get('objective_scenario') === id),
  getScenarioLessonsCategories: id => entities('lessonscategorys', state).filter(
    l => l.get('lessons_category_scenario') === id,
  ),
  getScenarioLessonsQuestions: id => entities('lessonsquestions', state).filter(
    l => l.get('lessons_question_scenario') === id,
  ),
  getAgents: () => entities('agents', state),
  // domains
  getDomains: () => entities('domains', state),
  // catalog
  getCatalogConnectors: () => entities('catalog_connectors', state),
  getCatalogConnector: id => entity(id, 'catalog_connectors', state),
});
