import { type Map as ImmutableMap } from 'immutable';
import { type Dispatch } from 'redux';

import { DATA_DELETE_BATCH_SUCCESS } from '../constants/ActionTypes';
import { store } from '../store';
import {
  delReferential,
  getReferential,
  postReferential,
  putReferential,
  simplePostCall,
} from '../utils/Action';
import type { Inject, InjectAssistantInput, InjectInput, InjectUpdateActivationInput } from '../utils/api-types';
import * as schema from './Schema';

type AppDispatch = Dispatch;

// -- INJECTS --

export const fetchInject = (injectId: string) => (dispatch: AppDispatch) => {
  const uri = `/api/injects/${injectId}`;
  return getReferential(schema.inject, uri)(dispatch);
};

// -- EXERCISES --

export const fetchExerciseInjects = (exerciseId: string) => (dispatch: AppDispatch) => {
  const uri = `/api/exercises/${exerciseId}/injects`;
  return getReferential(schema.arrayOfInjects, uri)(dispatch);
};

// Reconciles the entity store with the backend after a simulation lifecycle
// transition that deletes injects server-side (Stop on a chained simulation
// drops its injects, Reset always does). The normalized store only ever MERGES
// fetch payloads - a refetch cannot evict an entity deleted server-side, and
// the per-entity SSE delete events are not guaranteed to reach this tab - so
// without an explicit eviction the Execution screens keep showing the deleted
// injects as "completed" until a full page reload. Best-effort: on fetch
// failure nothing is evicted (better stale than wrongly empty).
export const reconcileExerciseInjects = (exerciseId: string) => async (dispatch: AppDispatch) => {
  let payload;
  try {
    payload = await fetchExerciseInjects(exerciseId)(dispatch);
  } catch {
    return;
  }
  const freshIds = new Set<string>(payload?.result ?? []);
  const injectsMap = store.getState().referential.getIn(['entities', 'injects']);
  if (!injectsMap) {
    return;
  }
  const staleDeletes = injectsMap
    .valueSeq()
    .filter((inject: ImmutableMap<string, unknown>) => inject.get('inject_exercise') === exerciseId && !freshIds.has(inject.get('inject_id') as string))
    .map((inject: ImmutableMap<string, unknown>) => ({
      id: inject.get('inject_id') as string,
      type: 'injects',
    }))
    .toArray();
  if (staleDeletes.length > 0) {
    dispatch({
      type: DATA_DELETE_BATCH_SUCCESS,
      payload: staleDeletes,
    });
  }
};

// Lightweight variant (InjectOutput) used where only targeting metadata is
// needed (e.g. the simulation hero usage stats), not the full inject payload.
export const fetchExerciseInjectsSimple = (exerciseId: string) => (dispatch: AppDispatch) => {
  const uri = `/api/exercises/${exerciseId}/injects/simple`;
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
