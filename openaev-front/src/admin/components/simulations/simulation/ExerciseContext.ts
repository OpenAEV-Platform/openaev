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
} from '../../../../actions/inject';
import { bulkTestInjects } from '../../../../actions/inject_test/simulation-inject-test-actions';
import { createInjectsForSimulation, importInjectsForSimulation, searchExerciseInjectsSimple } from '../../../../actions/simulations/simulation-inject-actions';
import {
  type Exercise,
  type Inject,
  type InjectBulkProcessingInput,
  type InjectBulkUpdateInputs, type InjectInput,
  type InjectOutput, type InjectsImportInput,
  type InjectTestStatusOutput,
  type SearchPaginationInput,
} from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';

const injectContextForExercise = (exercise: Exercise) => {
  const dispatch = useAppDispatch();
  const [injects, setInjects] = useState<InjectOutput[]>([]);

  return {
    injects,
    setInjects,
    searchInjects(input: SearchPaginationInput) {
      return searchExerciseInjectsSimple(exercise.exercise_id, input);
    },
    onAddInject(inject: Inject) {
      return dispatch(addInjectForExercise(exercise.exercise_id, inject));
    },
    onAddMultipleInjects(inputs: InjectInput[]) {
      return dispatch(createInjectsForSimulation(exercise.exercise_id, inputs));
    },
    onBulkUpdateInject(param: InjectBulkUpdateInputs) {
      return bulkUpdateInjectSimple(param).then((result: { data: Inject[] }) => result?.data);
    },
    onUpdateInject(injectId: Inject['inject_id'], inject: InjectInput) {
      return dispatch(updateInjectForExercise(exercise.exercise_id, injectId, inject));
    },
    onUpdateInjectTrigger(injectId: Inject['inject_id']) {
      return dispatch(updateInjectTriggerForExercise(exercise.exercise_id, injectId));
    },
    onUpdateInjectActivation(injectId: Inject['inject_id'], injectEnabled: { inject_enabled: boolean }) {
      return dispatch(updateInjectActivationForExercise(exercise.exercise_id, injectId, injectEnabled));
    },
    onInjectDone(injectId: Inject['inject_id']) {
      return dispatch(injectDone(exercise.exercise_id, injectId));
    },
    onDeleteInject(injectId: Inject['inject_id']) {
      return dispatch(deleteInjectForExercise(exercise.exercise_id, injectId));
    },
    onImportInjectFromJson(file: File) {
      return importInjectsForSimulation(exercise.exercise_id, file).then((response) => {
        dispatch(fetchExerciseInjects(exercise.exercise_id));
        dispatch(fetchExercise(exercise.exercise_id));
        dispatch(fetchExerciseTeams(exercise.exercise_id));
        return response;
      });
    },
    onImportInjectFromXls(importId: string, input: InjectsImportInput) {
      return importXlsForExercise(exercise.exercise_id, importId, input).then((response) => {
        dispatch(fetchExerciseInjects(exercise.exercise_id));
        dispatch(fetchExercise(exercise.exercise_id));
        dispatch(fetchExerciseTeams(exercise.exercise_id));
        return response;
      });
    },
    async onDryImportInjectFromXls(importId: string, input: InjectsImportInput) {
      return dryImportXlsForExercise(exercise.exercise_id, importId, input);
    },
    onBulkDeleteInjects(param: InjectBulkProcessingInput) {
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
