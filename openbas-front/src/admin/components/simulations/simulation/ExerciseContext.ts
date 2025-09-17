import { useState } from 'react';

import { fetchExercise, fetchExerciseTeams } from '../../../../actions/Exercise';
import { dryImportXlsForExercise, importXlsForExercise } from '../../../../actions/exercises/exercise-action';
import {
  addInjectForExercise,
  bulkDeleteInjectsSimple,
  bulkUpdateInjectSimple,
  deleteInjectForExercise,
  fetchExerciseInjects,
  injectDone,
  updateInjectActivationForExercise,
  updateInjectForExercise,
  updateInjectTriggerForExercise,
} from '../../../../actions/Inject';
import { bulkTestInjects } from '../../../../actions/inject_test/simulation-inject-test-actions';
import { type InjectOutputType, type InjectStore } from '../../../../actions/injects/Inject';
import {
  importInjectsForSimulation,
  searchExerciseInjectsSimple,
} from '../../../../actions/injects/inject-action';
import { type Page } from '../../../../components/common/queryable/Page';
import {
  type Exercise,
  type ImportTestSummary,
  type Inject,
  type InjectBulkProcessingInput,
  type InjectBulkUpdateInputs,
  type InjectsImportInput,
  type InjectTestStatusOutput,
  type SearchPaginationInput,
} from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';

const injectContextForExercise = (exercise: Exercise) => {
  const dispatch = useAppDispatch();
  const [injects, setInjects] = useState<InjectOutputType[]>([]);

  return {
    injects,
    setInjects,
    searchInjects(input: SearchPaginationInput): Promise<{ data: Page<InjectOutputType> }> {
      return searchExerciseInjectsSimple(exercise.exercise_id, input);
    },
    onAddInject(inject: Inject): Promise<{
      result: string;
      entities: { injects: Record<string, InjectStore> };
    }> {
      return dispatch(addInjectForExercise(exercise.exercise_id, inject));
    },
    onBulkUpdateInject(param: InjectBulkUpdateInputs): Promise<Inject[] | void> {
      return bulkUpdateInjectSimple(param).then((result: { data: Inject[] }) => result?.data);
    },
    onUpdateInject(injectId: Inject['inject_id'], inject: Inject): Promise<{
      result: string;
      entities: { injects: Record<string, InjectStore> };
    }> {
      return dispatch(updateInjectForExercise(exercise.exercise_id, injectId, inject));
    },
    onUpdateInjectTrigger(injectId: Inject['inject_id']): Promise<{
      result: string;
      entities: { injects: Record<string, InjectStore> };
    }> {
      return dispatch(updateInjectTriggerForExercise(exercise.exercise_id, injectId));
    },
    onUpdateInjectActivation(injectId: Inject['inject_id'], injectEnabled: { inject_enabled: boolean }): Promise<{
      result: string;
      entities: { injects: Record<string, InjectStore> };
    }> {
      return dispatch(updateInjectActivationForExercise(exercise.exercise_id, injectId, injectEnabled));
    },
    onInjectDone(injectId: Inject['inject_id']): Promise<{
      result: string;
      entities: { injects: Record<string, InjectStore> };
    }> {
      return dispatch(injectDone(exercise.exercise_id, injectId));
    },
    onDeleteInject(injectId: Inject['inject_id']): Promise<void> {
      return dispatch(deleteInjectForExercise(exercise.exercise_id, injectId));
    },
    onImportInjectFromJson(file: File): Promise<void> {
      return importInjectsForSimulation(exercise.exercise_id, file).then(response => new Promise((resolve, _reject) => {
        dispatch(fetchExerciseInjects(exercise.exercise_id));
        dispatch(fetchExercise(exercise.exercise_id));
        dispatch(fetchExerciseTeams(exercise.exercise_id));
        resolve(response.data);
      }));
    },
    onImportInjectFromXls(importId: string, input: InjectsImportInput): Promise<ImportTestSummary> {
      return importXlsForExercise(exercise.exercise_id, importId, input).then(response => new Promise((resolve, _reject) => {
        dispatch(fetchExerciseInjects(exercise.exercise_id));
        dispatch(fetchExercise(exercise.exercise_id));
        dispatch(fetchExerciseTeams(exercise.exercise_id));
        resolve(response.data);
      }));
    },
    async onDryImportInjectFromXls(importId: string, input: InjectsImportInput): Promise<ImportTestSummary> {
      return dryImportXlsForExercise(exercise.exercise_id, importId, input).then(result => result.data);
    },
    onBulkDeleteInjects(param: InjectBulkProcessingInput): Promise<Inject[]> {
      return bulkDeleteInjectsSimple(param).then((result: { data: Inject[] }) => result?.data);
    },
    bulkTestInjects(param: InjectBulkProcessingInput): Promise<{
      uri: string;
      data: InjectTestStatusOutput[];
    }> {
      return bulkTestInjects(exercise.exercise_id, param).then(result => ({
        uri: `/admin/simulations/${exercise.exercise_id}/tests`,
        data: result.data,
      }));
    },
  };
};

export default injectContextForExercise;
