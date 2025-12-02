import { type AxiosResponse } from 'axios';
import { createContext, type ReactElement } from 'react';

import { type FullArticleStore } from '../../../actions/channels/Article';
import { type Page } from '../../../components/common/queryable/Page';
import { type CustomAxiosResponse } from '../../../network';
import {
  type Article,
  type ArticleCreateInput,
  type ArticleUpdateInput,
  type Challenge,
  type Channel,
  type Evaluation,
  type EvaluationInput,
  type ImportTestSummary,
  type Inject, type InjectBulkProcessingInput,
  type InjectBulkUpdateInputs, type InjectInput,
  type InjectOutput, type InjectsImportInput,
  type InjectTestStatusOutput,
  type LessonsAnswer,
  type LessonsAnswerCreateInput,
  type LessonsCategory,
  type LessonsCategoryCreateInput,
  type LessonsCategoryTeamsInput,
  type LessonsCategoryUpdateInput,
  type LessonsQuestion,
  type LessonsQuestionCreateInput,
  type LessonsQuestionUpdateInput,
  type LessonsSendInput,
  type Objective,
  type ObjectiveInput,
  type PublicExercise,
  type PublicScenario,
  type Report,
  type ReportInput,
  type SearchPaginationInput,
  type Team,
  type TeamCreateInput,
  type TeamOutput,
  type Variable,
  type VariableInput,
} from '../../../utils/api-types';
import { INHERITED_CONTEXT, type InheritedContext } from '../../../utils/permissions/types';
import { type UserStore } from '../teams/players/Player';

export type PermissionsContextType = {
  permissions: {
    readOnly: boolean;
    canManage: boolean;
    canAccess: boolean;
    canLaunch: boolean;
    canDelete: boolean;
    isRunning: boolean;
  };
  inherited_context: InheritedContext;
};

export type ArticleContextType = {
  previewArticleUrl: (article: FullArticleStore) => string;
  fetchArticles: () => Promise<CustomAxiosResponse<Article[]>>;
  fetchChannels: () => Promise<CustomAxiosResponse<Channel[]>>;
  fetchDocuments: () => Promise<CustomAxiosResponse<Document[]>>;
  onAddArticle: (data: ArticleCreateInput) => Promise<CustomAxiosResponse<Article>>;
  onUpdateArticle: (article: Article, data: ArticleUpdateInput) => Promise<CustomAxiosResponse<Article>>;
  onDeleteArticle: (article: Article) => Promise<AxiosResponse>;
};

export type ChallengeContextType = {
  previewChallengeUrl?: () => string;
  fetchChallenges?: () => Promise<CustomAxiosResponse<Challenge[]>>;
};

export type PreviewChallengeContextType = {
  linkToPlayerMode: string;
  linkToAdministrationMode: string;
  scenarioOrExercise: PublicScenario | PublicExercise | undefined;
};

export type InjectTestContextType = {
  contextId: string;
  url?: string;
  searchInjectTests?: (contextId: string, searchPaginationInput: SearchPaginationInput) => Promise<{ data: Page<InjectTestStatusOutput> }>;
  fetchInjectTestStatus?: (testId: string) => Promise<{ data: InjectTestStatusOutput }>;
  testInject?: (contextId: string, injectId: string) => Promise<{ data: InjectTestStatusOutput }>;
  bulkTestInjects?: (contextId: string, data: InjectBulkProcessingInput) => Promise<{ data: InjectTestStatusOutput[] }>;
  deleteInjectTest?: (contextId: string, testId: string) => void;
};

export type DocumentContextType = {
  onInitDocument: () => {
    document_tags: {
      id: string;
      label: string;
    }[];
    document_exercises: {
      id: string;
      label: string;
    }[];
    document_scenarios: {
      id: string;
      label: string;
    }[];
  };
};

export type VariableContextType = {
  onCreateVariable: (data: VariableInput) => void;
  onEditVariable: (variable: Variable, data: VariableInput) => void;
  onDeleteVariable: (variable: Variable) => void;
};

export type ReportContextType = {
  onDeleteReport?: (report: Report) => void;
  onUpdateReport: (reportId: Report['report_id'], report: ReportInput) => void;
  renderReportForm: (onSubmitForm: (data: ReportInput) => void, onHandleCancel: () => void, report: Report) => ReactElement;
};

export type TeamContextType = {
  onAddUsersTeam?: (teamId: Team['team_id'], userIds: UserStore['user_id'][]) => void;
  onRemoveUsersTeam?: (teamId: Team['team_id'], userIds: UserStore['user_id'][]) => void;
  onCreateTeam?: (team: TeamCreateInput) => Promise<CustomAxiosResponse<Team>>;
  onRemoveTeam?: (teamId: Team['team_id']) => Promise<CustomAxiosResponse<TeamOutput[]>>;
  onReplaceTeam?: (teamIds: Team['team_id'][]) => Promise<CustomAxiosResponse<TeamOutput[]>>;
  onToggleUser?: (teamId: Team['team_id'], userId: UserStore['user_id'], userEnabled: boolean) => void;
  checkUserEnabled?: (teamId: Team['team_id'], userId: UserStore['user_id']) => boolean;
  computeTeamUsersEnabled?: (teamId: Team['team_id']) => number;
  searchTeams: (input: SearchPaginationInput, contextualOnly?: boolean) => Promise<{ data: Page<TeamOutput> }>;
  allUsersEnabledNumber?: number;
  allUsersNumber?: number;
};

export type InjectContextType = {
  injects: InjectOutput[];
  setInjects: (input: InjectOutput[]) => void;
  searchInjects: (input: SearchPaginationInput) => Promise<{ data: Page<InjectOutput> }>;
  onAddInject: (inject: Inject) => Promise<CustomAxiosResponse<Inject>>;
  onAddMultipleInjects: (inputs: InjectInput[]) => Promise<CustomAxiosResponse<Inject[]>>;
  onBulkUpdateInject: (param: InjectBulkUpdateInputs) => Promise<Inject[] | void>;
  onUpdateInject: (injectId: Inject['inject_id'], inject: InjectInput) => Promise<CustomAxiosResponse<Inject>>;
  onUpdateInjectTrigger?: (injectId: Inject['inject_id']) => Promise<CustomAxiosResponse<Inject>>;
  onUpdateInjectActivation: (injectId: Inject['inject_id'], injectEnabled: { inject_enabled: boolean }) => Promise<CustomAxiosResponse<Inject>>;
  onInjectDone?: (injectId: Inject['inject_id']) => Promise<CustomAxiosResponse<Inject>>;
  onDeleteInject: (injectId: Inject['inject_id']) => Promise<AxiosResponse>;
  onImportInjectFromJson?: (file: File) => Promise<AxiosResponse<void>>;
  onImportInjectFromXls?: (importId: string, input: InjectsImportInput) => Promise<AxiosResponse<ImportTestSummary>>;
  onDryImportInjectFromXls?: (importId: string, input: InjectsImportInput) => Promise<AxiosResponse<ImportTestSummary>>;
  onBulkDeleteInjects: (param: InjectBulkProcessingInput) => Promise<Inject[]>;
  bulkTestInjects: (param: InjectBulkProcessingInput) => Promise<{
    uri: string;
    data: InjectTestStatusOutput[];
  }>;
};
export type LessonContextType = {
  onApplyLessonsTemplate: (data: string) => Promise<AxiosResponse<LessonsCategory[]>>;
  onResetLessonsAnswers?: () => Promise<AxiosResponse<LessonsCategory[]>>;
  onEmptyLessonsCategories: () => Promise<AxiosResponse<LessonsCategory[]>>;
  onUpdateSourceLessons: (data: boolean) => Promise<AxiosResponse>;
  onSendLessons?: (data: LessonsSendInput) => void;
  onAddLessonsCategory: (data: LessonsCategoryCreateInput) => Promise<AxiosResponse<LessonsCategory>>;
  onDeleteLessonsCategory: (data: string) => void;
  onUpdateLessonsCategory: (lessonCategoryId: string, data: LessonsCategoryUpdateInput) => Promise<AxiosResponse<LessonsCategory>>;
  onUpdateLessonsCategoryTeams: (lessonCategoryId: string, data: LessonsCategoryTeamsInput) => Promise<AxiosResponse<LessonsCategory>>;
  onDeleteLessonsQuestion: (lessonsCategoryId: string, lessonsQuestionId: string) => void;
  onUpdateLessonsQuestion: (lessonsCategoryId: string, lessonsQuestionId: string, data: LessonsQuestionUpdateInput) => Promise<AxiosResponse<LessonsQuestion>>;
  onAddLessonsQuestion: (lessonsCategoryId: string, data: LessonsQuestionCreateInput) => Promise<AxiosResponse<LessonsQuestion>>;
  onAddObjective: (data: ObjectiveInput) => Promise<CustomAxiosResponse<Objective>>;
  onUpdateObjective: (objectiveId: string, data: ObjectiveInput) => Promise<CustomAxiosResponse<Objective>>;
  onDeleteObjective: (objectiveId: string) => void;
  onAddEvaluation: (objectiveId: string, data: EvaluationInput) => Promise<CustomAxiosResponse<Evaluation>>;
  onUpdateEvaluation: (objectiveId: string, evaluationId: string, data: EvaluationInput) => Promise<CustomAxiosResponse<Evaluation>>;
  onFetchEvaluation: (objectiveId: string) => void;
};
export type ViewLessonContextType = {
  onAddLessonsAnswers?: (questionCategory: string, lessonsQuestionId: string, answerData: LessonsAnswerCreateInput) => Promise<AxiosResponse<LessonsAnswer>>;
  onFetchPlayerLessonsAnswers?: () => Promise<AxiosResponse<LessonsAnswer[]>>;
};

export const PermissionsContext = createContext<PermissionsContextType>({
  permissions: {
    canAccess: false,
    canManage: false,
    canLaunch: false,
    canDelete: false,
    readOnly: false,
    isRunning: false,
  },
  inherited_context: INHERITED_CONTEXT.NONE,
});
export const ArticleContext = createContext<ArticleContextType>({
  fetchArticles: () => new Promise<CustomAxiosResponse<Article[]>>(() => {}),
  fetchChannels: () => new Promise<CustomAxiosResponse<Channel[]>>(() => {}),
  fetchDocuments: () => new Promise<CustomAxiosResponse<Document[]>>(() => {}),
  onAddArticle: (_data: ArticleCreateInput) => new Promise<CustomAxiosResponse<Article>>(() => {}),
  onDeleteArticle: (_article: Article) => new Promise<CustomAxiosResponse>(() => {}),
  onUpdateArticle: (_article: Article, _data: ArticleUpdateInput) => new Promise<CustomAxiosResponse<Article>>(() => {}),
  previewArticleUrl(_article: FullArticleStore): string {
    return '';
  },
});
export const ChallengeContext = createContext<ChallengeContextType>({
  previewChallengeUrl(): string {
    return '';
  },
  fetchChallenges: () => new Promise<CustomAxiosResponse<Challenge[]>>(() => {}),
});
export const PreviewChallengeContext = createContext<PreviewChallengeContextType>({
  linkToPlayerMode: '',
  linkToAdministrationMode: '',
  scenarioOrExercise: {
    description: '',
    id: '',
    name: '',
  },
});

export const InjectTestContext = createContext<InjectTestContextType>({
  contextId: '',
  url: '',
  searchInjectTests: undefined,
  fetchInjectTestStatus: undefined,
  testInject: undefined,
  bulkTestInjects: undefined,
  deleteInjectTest: undefined,
});
export const DocumentContext = createContext<DocumentContextType>({
  onInitDocument(): {
    document_tags: {
      id: string;
      label: string;
    }[];
    document_exercises: {
      id: string;
      label: string;
    }[];
    document_scenarios: {
      id: string;
      label: string;
    }[];
  } {
    return {
      document_exercises: [],
      document_scenarios: [],
      document_tags: [],
    };
  },
});
export const VariableContext = createContext<VariableContextType>({
  onCreateVariable(_data: VariableInput): void {
  },
  onDeleteVariable(_variable: Variable): void {
  },
  onEditVariable(_variable: Variable, _data: VariableInput): void {
  },
});
export const ReportContext = createContext<ReportContextType>(<ReportContextType>{
  onUpdateReport(_reportId: Report['report_id'], _report: ReportInput): void {
  },
  renderReportForm(_onSubmit: (data: ReportInput) => void, _onCancel: () => void, _report: Report): void {
  },
});
export const TeamContext = createContext<TeamContextType>({
  onAddUsersTeam(_teamId: Team['team_id'], _userIds: UserStore['user_id'][]): Promise<void> {
    return new Promise<void>(() => {
    });
  },
  onRemoveUsersTeam(_teamId: Team['team_id'], _userIds: UserStore['user_id'][]): Promise<void> {
    return new Promise<void>(() => {
    });
  },
  searchTeams(_: SearchPaginationInput, _contextualOnly?: boolean): Promise<{ data: Page<TeamOutput> }> {
    return new Promise<{ data: Page<TeamOutput> }>(() => {
    });
  },
});
export const InjectContext = createContext<InjectContextType>({
  injects: [],
  setInjects: () => {},
  searchInjects(_: SearchPaginationInput) {
    return new Promise<{ data: Page<InjectOutput> }>(() => {
    });
  },
  onAddInject: () => new Promise<CustomAxiosResponse<Inject>>(() => {}),
  onAddMultipleInjects: (_inputs: InjectInput[]) => new Promise<CustomAxiosResponse<Inject[]>>(() => {}),
  onBulkUpdateInject(_param: InjectBulkUpdateInputs): Promise<Inject[] | void> {
    return Promise.resolve([]);
  },
  onUpdateInject: () => new Promise<CustomAxiosResponse<Inject>>(() => {}),
  onUpdateInjectTrigger: () => new Promise<CustomAxiosResponse<Inject>>(() => {}),
  onUpdateInjectActivation: () => new Promise<CustomAxiosResponse<Inject>>(() => {}),
  onInjectDone: () => new Promise<CustomAxiosResponse<Inject>>(() => {}),
  onDeleteInject: () => new Promise<AxiosResponse>(() => {}),
  onImportInjectFromXls(_importId: string, _input: InjectsImportInput) {
    return new Promise<AxiosResponse<ImportTestSummary>>(() => {
    });
  },
  onDryImportInjectFromXls(_importId: string, _input: InjectsImportInput) {
    return new Promise<AxiosResponse<ImportTestSummary>>(() => {
    });
  },
  onBulkDeleteInjects(_param: InjectBulkProcessingInput): Promise<Inject[]> {
    return new Promise<Inject[]>(() => {
    });
  },
  bulkTestInjects(_param: InjectBulkProcessingInput): Promise<{
    uri: string;
    data: InjectTestStatusOutput[];
  }> {
    return new Promise<{
      uri: string;
      data: InjectTestStatusOutput[];
    }>(() => {
    });
  },
});
export const LessonContext = createContext<LessonContextType>({
  onApplyLessonsTemplate(_data: string) {
    return new Promise<AxiosResponse<LessonsCategory[]>>(() => {
    });
  },
  onResetLessonsAnswers() {
    return new Promise<AxiosResponse<LessonsCategory[]>>(() => {
    });
  },
  onEmptyLessonsCategories() {
    return new Promise<AxiosResponse<LessonsCategory[]>>(() => {
    });
  },
  onUpdateSourceLessons(_data: boolean) {
    return new Promise<AxiosResponse>(() => {
    });
  },
  onSendLessons(_data: LessonsSendInput): void {
  },
  onAddLessonsCategory(_data: LessonsCategoryCreateInput) {
    return new Promise<AxiosResponse<LessonsCategory>>(() => {
    });
  },
  onDeleteLessonsCategory(_data: string): void {
  },
  onUpdateLessonsCategory(_lessonCategoryId: string, _data: LessonsCategoryUpdateInput) {
    return new Promise<AxiosResponse<LessonsCategory>>(() => {
    });
  },
  onUpdateLessonsCategoryTeams(_lessonCategoryId: string, _data: LessonsCategoryTeamsInput) {
    return new Promise<AxiosResponse<LessonsCategory>>(() => {
    });
  },
  onDeleteLessonsQuestion(_lessonsCategoryId: string, _lessonsQuestionId: string): void {
  },
  onUpdateLessonsQuestion(_lessonsCategoryId: string, _lessonsQuestionId: string, _data: LessonsQuestionUpdateInput) {
    return new Promise<AxiosResponse<LessonsQuestion>>(() => {
    });
  },
  onAddLessonsQuestion(_lessonsCategoryId: string, _data: LessonsQuestionCreateInput) {
    return new Promise<AxiosResponse<LessonsQuestion>>(() => {
    });
  },
  onAddObjective(_data: ObjectiveInput) {
    return new Promise<CustomAxiosResponse<Objective>>(() => {
    });
  },
  onUpdateObjective(_objectiveId: string, _data: ObjectiveInput) {
    return new Promise<CustomAxiosResponse<Objective>>(() => {
    });
  },
  onDeleteObjective(_objectiveId: string): void {
  },
  onAddEvaluation(_objectiveId: string, _data: EvaluationInput) {
    return new Promise<CustomAxiosResponse<Evaluation>>(() => {
    });
  },
  onUpdateEvaluation(_objectiveId: string, _evaluationId: string, _data: EvaluationInput) {
    return new Promise<CustomAxiosResponse<Evaluation>>(() => {
    });
  },
  onFetchEvaluation(_objectiveId: string): Promise<Evaluation[]> {
    return new Promise<Evaluation[]>(() => {
    });
  },
});
export const ViewLessonContext = createContext<ViewLessonContextType>({
  onAddLessonsAnswers(_questionCategory: string, _lessonsQuestionId: string, _answerData: LessonsAnswerCreateInput) {
    return new Promise<CustomAxiosResponse<LessonsAnswer>>(() => {});
  },
  onFetchPlayerLessonsAnswers() {
    return new Promise<CustomAxiosResponse<LessonsAnswer[]>>(() => {});
  },
});
export const ViewModeContext = createContext('list');
