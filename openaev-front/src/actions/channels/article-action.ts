import { type Dispatch } from 'redux';

import { delReferential, getReferential, postReferential, putReferential } from '../../utils/Action';
import { type Article, type ArticleCreateInput, type ArticleUpdateInput, type Exercise, type Scenario } from '../../utils/api-types';
import { arrayOfArticles, article } from '../schemas';

// -- EXERCISES --

export const fetchExerciseArticles = (exerciseId: Exercise['exercise_id']) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/articles`;
  return getReferential<Article[]>(arrayOfArticles, uri)(dispatch);
};
export const addExerciseArticle = (exerciseId: Exercise['exercise_id'], data: ArticleCreateInput) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/articles`;
  return postReferential<Article>(article, uri, data)(dispatch);
};
export const deleteExerciseArticle = (exerciseId: Exercise['exercise_id'], articleId: Article['article_id']) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/articles/${articleId}`;
  return delReferential(uri, 'articles', articleId)(dispatch);
};
export const updateExerciseArticle = (exerciseId: Exercise['exercise_id'], articleId: Article['article_id'], data: ArticleUpdateInput) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/articles/${articleId}`;
  return putReferential<Article>(article, uri, data)(dispatch);
};

// -- SCENARIOS --

export const addScenarioArticle = (scenarioId: Scenario['scenario_id'], data: ArticleCreateInput) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/articles`;
  return postReferential<Article>(article, uri, data)(dispatch);
};

export const fetchScenarioArticles = (scenarioId: Scenario['scenario_id']) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/articles`;
  return getReferential<Article[]>(arrayOfArticles, uri)(dispatch);
};
export const updateScenarioArticle = (scenarioId: Scenario['scenario_id'], articleId: Article['article_id'], data: ArticleUpdateInput) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/articles/${articleId}`;
  return putReferential<Article>(article, uri, data)(dispatch);
};
export const deleteScenarioArticle = (scenarioId: Scenario['scenario_id'], articleId: Article['article_id']) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/articles/${articleId}`;
  return delReferential(uri, 'articles', articleId)(dispatch);
};
