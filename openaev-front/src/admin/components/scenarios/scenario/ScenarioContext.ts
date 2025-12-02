import { useState } from 'react';

import { addInjectForScenario, bulkDeleteInjectsSimple, bulkUpdateInjectSimple, deleteInjectScenario, fetchScenarioInjects, updateInjectActivationForScenario, updateInjectForScenario } from '../../../../actions/inject';
import { bulkTestInjects } from '../../../../actions/inject_test/scenario-inject-test-actions';
import { dryImportXlsForScenario, fetchScenario, fetchScenarioTeams, importXlsForScenario } from '../../../../actions/scenarios/scenario-actions';
import { createInjectsForScenario, importInjectsForScenario, searchScenarioInjectsSimple } from '../../../../actions/scenarios/scenario-inject-actions';
import { type Inject, type InjectBulkProcessingInput, type InjectBulkUpdateInputs, type InjectInput, type InjectOutput, type InjectsImportInput, type Scenario, type SearchPaginationInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';

const injectContextForScenario = (scenario: Scenario) => {
  const dispatch = useAppDispatch();
  const [injects, setInjects] = useState<InjectOutput[]>([]);

  return {
    injects,
    setInjects,
    searchInjects(input: SearchPaginationInput) {
      return searchScenarioInjectsSimple(scenario.scenario_id, input);
    },
    onAddInject(inject: Inject) {
      return dispatch(addInjectForScenario(scenario.scenario_id, inject));
    },

    onAddMultipleInjects(inputs: InjectInput[]) {
      return dispatch(createInjectsForScenario(scenario.scenario_id, inputs));
    },
    onBulkUpdateInject(param: InjectBulkUpdateInputs) {
      return bulkUpdateInjectSimple(param).then((result: { data: Inject[] }) => result?.data);
    },
    onUpdateInject(injectId: Inject['inject_id'], inject: InjectInput) {
      return dispatch(updateInjectForScenario(scenario.scenario_id, injectId, inject));
    },
    onUpdateInjectActivation(injectId: Inject['inject_id'], injectEnabled: { inject_enabled: boolean }) {
      return dispatch(updateInjectActivationForScenario(scenario.scenario_id, injectId, injectEnabled));
    },
    onDeleteInject(injectId: Inject['inject_id']) {
      return dispatch(deleteInjectScenario(scenario.scenario_id, injectId));
    },
    onImportInjectFromJson(file: File) {
      return importInjectsForScenario(scenario.scenario_id, file).then((response) => {
        dispatch(fetchScenarioInjects(scenario.scenario_id));
        dispatch(fetchScenario(scenario.scenario_id));
        dispatch(fetchScenarioTeams(scenario.scenario_id));
        return response;
      });
    },
    onImportInjectFromXls(importId: string, input: InjectsImportInput) {
      return importXlsForScenario(scenario.scenario_id, importId, input).then((response) => {
        dispatch(fetchScenarioInjects(scenario.scenario_id));
        dispatch(fetchScenario(scenario.scenario_id));
        dispatch(fetchScenarioTeams(scenario.scenario_id));
        return response;
      });
    },
    async onDryImportInjectFromXls(importId: string, input: InjectsImportInput) {
      return dryImportXlsForScenario(scenario.scenario_id, importId, input);
    },
    onBulkDeleteInjects(param: InjectBulkProcessingInput) {
      return bulkDeleteInjectsSimple(param).then((result: { data: Inject[] }) => result?.data);
    },
    bulkTestInjects(param: InjectBulkProcessingInput) {
      return bulkTestInjects(scenario.scenario_id, param).then(result => ({
        uri: `/admin/scenarios/${scenario.scenario_id}/tests`,
        data: result.data,
      }));
    },
  };
};

export default injectContextForScenario;
