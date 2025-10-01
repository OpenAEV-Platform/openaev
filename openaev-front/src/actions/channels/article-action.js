import { delReferential, getReferential, postReferential, putReferential } from '../../utils/Action';
import { arrayOfArticles, article } from './article-schema';

// -- EXERCISES --

export const fetchExerciseArticles = exerciseId => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/articles`;
  return getReferential(arrayOfArticles, uri)(dispatch);
};
export const addExerciseArticle = (exerciseId, data) => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/articles`;
  return postReferential(article, uri, data)(dispatch);
};
export const deleteExerciseArticle = (exerciseId, articleId) => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/articles/${articleId}`;
  return delReferential(uri, 'articles', articleId)(dispatch);
};
export const updateExerciseArticle = (exerciseId, articleId, data) => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/articles/${articleId}`;
  return putReferential(article, uri, data)(dispatch);
};

// -- SCENARIOS --

export const addScenarioArticle = (scenarioId, data) => (dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/articles`;
  return postReferential(article, uri, data)(dispatch);
};

export const fetchScenarioArticles = scenarioId => (dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/articles`;
  return getReferential(arrayOfArticles, uri)(dispatch);
};
export const updateScenarioArticle = (scenarioId, articleId, data) => (dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/articles/${articleId}`;
  return putReferential(article, uri, data)(dispatch);
};
export const deleteScenarioArticle = (scenarioId, articleId) => (dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/articles/${articleId}`;
  return delReferential(uri, 'articles', articleId)(dispatch);
};
