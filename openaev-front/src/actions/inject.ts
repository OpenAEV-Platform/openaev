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
import { type Inject, type InjectBulkProcessingInput, type InjectBulkUpdateInputs, type InjectInput, type InjectStatus, type Team } from '../utils/api-types';
import { arrayOfInjects, arrayOfTeams, inject, injectStatus } from './schemas';

// -- INJECTS --

export const fetchInject = (injectId: string) => (dispatch: Dispatch) => {
  const uri = `/api/injects/${injectId}`;
  return getReferential(inject, uri)(dispatch);
};

export const bulkDeleteInjects = (data: InjectBulkProcessingInput) => (dispatch: Dispatch) => {
  const uri = `/api/injects`;
  return bulkDeleteReferential(uri, 'injects', data)(dispatch);
};

export const bulkDeleteInjectsSimple = (data: InjectBulkProcessingInput) => {
  const uri = `/api/injects`;
  return simpleDelCall(uri, { data });
};

export const bulkUpdateInject = (data: InjectBulkUpdateInputs) => (dispatch: Dispatch) => {
  const uri = `/api/injects`;
  return putReferential<Inject[]>(inject, uri, data)(dispatch);
};

export const bulkUpdateInjectSimple = (data: InjectBulkUpdateInputs) => {
  const uri = `/api/injects`;
  return simplePutCall<Inject[]>(uri, data);
};

// -- EXERCISES --

export const fetchExerciseInjects = (exerciseId: string | undefined) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/injects`;
  return getReferential<Inject[]>(arrayOfInjects, uri)(dispatch);
};

export const fetchInjectTeams = (exerciseId: string, injectId: string) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/injects/${injectId}/teams`;
  return getReferential<Team[]>(arrayOfTeams, uri)(dispatch);
};

export const updateInjectForExercise = (exerciseId: string, injectId: string, data: InjectInput) => (dispatch: Dispatch) => {
  const uri = `/api/injects/${exerciseId}/${injectId}`;
  return putReferential<Inject>(inject, uri, data)(dispatch);
};

export const updateInjectTriggerForExercise = (exerciseId: string, injectId: string) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/injects/${injectId}/trigger`;
  return putReferential<Inject>(inject, uri)(dispatch);
};

export const updateInjectActivationForExercise = (exerciseId: string, injectId: string, data: unknown) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/injects/${injectId}/activation`;
  return putReferential<Inject>(inject, uri, data)(dispatch);
};

export const addInjectForExercise = (exerciseId: string, data: unknown) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/injects`;
  return postReferential<Inject>(inject, uri, data)(dispatch);
};

export const duplicateInjectForExercise = (exerciseId: string, injectId: string) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/injects/${injectId}`;
  return postReferential<Inject>(inject, uri, null)(dispatch);
};

export const deleteInjectForExercise = (exerciseId: string, injectId: string) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/injects/${injectId}`;
  return delReferential(uri, 'injects', injectId)(dispatch);
};

export const executeInject = (exerciseId: string, values: unknown, files: File[]) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/inject`;
  const formData = new FormData();
  if (files && files.length > 0) {
    formData.append('file', files[0]);
  }
  const blob = new Blob([JSON.stringify(values)], { type: 'application/json' });
  formData.append('input', blob);
  return postReferential<InjectStatus>(injectStatus, uri, formData)(dispatch);
};

export const injectDone = (exerciseId: string, injectId: string) => (dispatch: Dispatch) => {
  const data = {
    status: 'SUCCESS',
    message: 'Manual validation',
  };
  const uri = `/api/exercises/${exerciseId}/injects/${injectId}/status`;
  return postReferential<Inject>(inject, uri, data)(dispatch);
};

// -- SCENARIOS --

export const addInjectForScenario = (scenarioId: string, data: Inject) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/injects`;
  return postReferential<Inject>(inject, uri, data)(dispatch);
};

export const playInjectsAssistantForScenario = (scenarioId: string, data: unknown) => {
  const uri = `/api/scenarios/${scenarioId}/injects/assistant`;
  return simplePostCall<Inject[]>(uri, data);
};

export const duplicateInjectForScenario = (scenarioId: string, injectId: string) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/injects/${injectId}`;
  return postReferential<Inject>(inject, uri, null)(dispatch);
};

export const fetchScenarioInjects = (scenarioId: string) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/injects`;
  return getReferential<Inject[]>(arrayOfInjects, uri)(dispatch);
};

export const updateInjectForScenario = (scenarioId: string, injectId: string, data: unknown) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/injects/${injectId}`;
  return putReferential<Inject>(inject, uri, data)(dispatch);
};

export const updateInjectActivationForScenario = (exerciseId: string, injectId: string, data: unknown) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${exerciseId}/injects/${injectId}/activation`;
  return putReferential<Inject>(inject, uri, data)(dispatch);
};

export const deleteInjectScenario = (scenarioId: string, injectId: string) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/injects/${injectId}`;
  return delReferential(uri, 'injects', injectId)(dispatch);
};
