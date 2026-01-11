import { type Dispatch } from 'redux';

import {
  bulkDeleteReferential,
  delReferential,
  getReferential,
  postReferential,
  putReferential,
  simpleDelCall,
  simplePostCall,
  simplePutCall,
} from '../utils/Action';
import type { Inject, InjectAssistantInput, InjectBulkProcessingInput, InjectBulkUpdateInputs, InjectInput, InjectUpdateActivationInput } from '../utils/api-types';
import * as schema from './Schema';

type AppDispatch = Dispatch;

// -- INJECTS --

export const fetchInject = (injectId: string) => (dispatch: AppDispatch) => {
  const uri = `/api/injects/${injectId}`;
  return getReferential(schema.inject, uri)(dispatch);
};

export const bulkDeleteInjects = (data: InjectBulkProcessingInput) => (dispatch: AppDispatch) => {
  const uri = '/api/injects';
  return bulkDeleteReferential(uri, 'injects', data)(dispatch);
};

export const bulkDeleteInjectsSimple = (data: InjectBulkProcessingInput) => {
  const uri = '/api/injects';
  return simpleDelCall(uri, { data });
};

export const bulkUpdateInject = (data: InjectBulkUpdateInputs) => (dispatch: AppDispatch) => {
  const uri = '/api/injects';
  return putReferential(schema.inject, uri, data)(dispatch);
};

export const bulkUpdateInjectSimple = (data: InjectBulkUpdateInputs) => {
  const uri = '/api/injects';
  return simplePutCall(uri, data);
};

// -- EXERCISES --

export const fetchExerciseInjects = (exerciseId: string) => (dispatch: AppDispatch) => {
  const uri = `/api/exercises/${exerciseId}/injects`;
  return getReferential(schema.arrayOfInjects, uri)(dispatch);
};

export const fetchInjectTeams = (exerciseId: string, injectId: string) => (dispatch: AppDispatch) => {
  const uri = `/api/exercises/${exerciseId}/injects/${injectId}/teams`;
  return getReferential(schema.arrayOfTeams, uri)(dispatch);
};

export const updateInjectForExercise = (exerciseId: string, injectId: string, data: Inject | InjectInput) => (dispatch: AppDispatch) => {
  const uri = `/api/injects/${exerciseId}/${injectId}`;
  return putReferential(schema.inject, uri, data)(dispatch);
};

export const updateInjectTriggerForExercise = (exerciseId: string, injectId: string) => (dispatch: AppDispatch) => {
  const uri = `/api/exercises/${exerciseId}/injects/${injectId}/trigger`;
  return putReferential(schema.inject, uri, {})(dispatch);
};

export const updateInjectActivationForExercise = (exerciseId: string, injectId: string, data: InjectUpdateActivationInput) => (dispatch: AppDispatch) => {
  const uri = `/api/exercises/${exerciseId}/injects/${injectId}/activation`;
  return putReferential(schema.inject, uri, data)(dispatch);
};

export const addInjectForExercise = (exerciseId: string, data: Inject | InjectInput) => (dispatch: AppDispatch) => {
  const uri = `/api/exercises/${exerciseId}/injects`;
  return postReferential(schema.inject, uri, data)(dispatch);
};

export const duplicateInjectForExercise = (exerciseId: string, injectId: string) => (dispatch: AppDispatch) => {
  const uri = `/api/exercises/${exerciseId}/injects/${injectId}`;
  return postReferential(schema.inject, uri, null)(dispatch);
};

export const deleteInjectForExercise = (exerciseId: string, injectId: string) => (dispatch: AppDispatch) => {
  const uri = `/api/exercises/${exerciseId}/injects/${injectId}`;
  return delReferential(uri, 'injects', injectId)(dispatch);
};

export const executeInject = (exerciseId: string, values: InjectInput, files: File[] | null) => (dispatch: AppDispatch) => {
  const uri = `/api/exercises/${exerciseId}/inject`;
  const formData = new FormData();
  formData.append('file', files && files.length > 0 ? files[0] : '');
  const blob = new Blob([JSON.stringify(values)], { type: 'application/json' });
  formData.append('input', blob);
  return postReferential(schema.injectStatus, uri, formData)(dispatch);
};

export const injectDone = (exerciseId: string, injectId: string) => (dispatch: AppDispatch) => {
  const data = {
    status: 'SUCCESS',
    message: 'Manual validation',
  };
  const uri = `/api/exercises/${exerciseId}/injects/${injectId}/status`;
  return postReferential(schema.inject, uri, data)(dispatch);
};

// -- SCENARIOS --

export const addInjectForScenario = (scenarioId: string, data: Inject | InjectInput) => (dispatch: AppDispatch) => {
  const uri = `/api/scenarios/${scenarioId}/injects`;
  return postReferential(schema.inject, uri, data)(dispatch);
};

export const playInjectsAssistantForScenario = (scenarioId: string, data: InjectAssistantInput) => {
  const uri = `/api/scenarios/${scenarioId}/injects/assistant`;
  return simplePostCall(uri, data);
};

export const duplicateInjectForScenario = (scenarioId: string, injectId: string) => (dispatch: AppDispatch) => {
  const uri = `/api/scenarios/${scenarioId}/injects/${injectId}`;
  return postReferential(schema.inject, uri, null)(dispatch);
};

export const fetchScenarioInjects = (scenarioId: string) => (dispatch: AppDispatch) => {
  const uri = `/api/scenarios/${scenarioId}/injects`;
  return getReferential(schema.arrayOfInjects, uri)(dispatch);
};

export const updateInjectForScenario = (scenarioId: string, injectId: string, data: Inject | InjectInput) => (dispatch: AppDispatch) => {
  const uri = `/api/scenarios/${scenarioId}/injects/${injectId}`;
  return putReferential(schema.inject, uri, data)(dispatch);
};

export const updateInjectActivationForScenario = (scenarioId: string, injectId: string, data: InjectUpdateActivationInput) => (dispatch: AppDispatch) => {
  const uri = `/api/scenarios/${scenarioId}/injects/${injectId}/activation`;
  return putReferential(schema.inject, uri, data)(dispatch);
};

export const deleteInjectScenario = (scenarioId: string, injectId: string) => (dispatch: AppDispatch) => {
  const uri = `/api/scenarios/${scenarioId}/injects/${injectId}`;
  return delReferential(uri, 'injects', injectId)(dispatch);
};
