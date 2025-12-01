import { useState } from 'react';

import { addInjectForScenario, bulkDeleteInjectsSimple, bulkUpdateInjectSimple, deleteInjectScenario, fetchScenarioInjects, updateInjectActivationForScenario, updateInjectForScenario } from '../../../../actions/Inject';
import { bulkTestInjects } from '../../../../actions/inject_test/scenario-inject-test-actions';
import { type InjectOutputType, type InjectStore } from '../../../../actions/injects/Inject';
import { dryImportXlsForScenario, fetchScenario, fetchScenarioTeams, importXlsForScenario } from '../../../../actions/scenarios/scenario-actions';
import { createInjectsForScenario, importInjectsForScenario, searchScenarioInjectsSimple } from '../../../../actions/scenarios/scenario-inject-actions';
import { type Page } from '../../../../components/common/queryable/Page';
import { type ImportTestSummary, type Inject, type InjectBulkProcessingInput, type InjectBulkUpdateInputs, type InjectInput, type InjectsImportInput, type InjectTestStatusOutput, type Scenario, type SearchPaginationInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';

const injectContextForScenario = (scenario: Scenario) => {
  const dispatch = useAppDispatch();
  const [injects, setInjects] = useState<InjectOutputType[]>([]);

  return {
    injects,
    setInjects,
    searchInjects(input: SearchPaginationInput): Promise<{ data: Page<InjectOutputType> }> {
      return searchScenarioInjectsSimple(scenario.scenario_id, input);
    },
    onAddInject(inject: Inject): Promise<{
      result: string;
      entities: { injects: Record<string, InjectStore> };
    }> {
      return dispatch(addInjectForScenario(scenario.scenario_id, inject));
    },

    onAddMultipleInjects(inputs: InjectInput[]): Promise<{
      result: string[];
      entities: { injects: Record<string, InjectStore> };
    }> {
      return dispatch(createInjectsForScenario(scenario.scenario_id, inputs));
    },
    onBulkUpdateInject(param: InjectBulkUpdateInputs): Promise<Inject[] | void> {
      return bulkUpdateInjectSimple(param).then((result: { data: Inject[] }) => result?.data);
    },
    onUpdateInject(injectId: Inject['inject_id'], inject: Inject): Promise<{
      result: string;
      entities: { injects: Record<string, InjectStore> };
    }> {
      return dispatch(updateInjectForScenario(scenario.scenario_id, injectId, inject));
    },
    onUpdateInjectActivation(injectId: Inject['inject_id'], injectEnabled: { inject_enabled: boolean }): Promise<{
      result: string;
      entities: { injects: Record<string, InjectStore> };
    }> {
      return dispatch(updateInjectActivationForScenario(scenario.scenario_id, injectId, injectEnabled));
    },
    onDeleteInject(injectId: Inject['inject_id']): Promise<void> {
      return dispatch(deleteInjectScenario(scenario.scenario_id, injectId));
    },
    onImportInjectFromJson(file: File): Promise<void> {
      return importInjectsForScenario(scenario.scenario_id, file).then(response => new Promise((resolve, _reject) => {
        dispatch(fetchScenarioInjects(scenario.scenario_id));
        dispatch(fetchScenario(scenario.scenario_id));
        dispatch(fetchScenarioTeams(scenario.scenario_id));
        resolve(response.data);
      }));
    },
    onImportInjectFromXls(importId: string, input: InjectsImportInput): Promise<ImportTestSummary> {
      return importXlsForScenario(scenario.scenario_id, importId, input).then(response => new Promise((resolve, _reject) => {
        dispatch(fetchScenarioInjects(scenario.scenario_id));
        dispatch(fetchScenario(scenario.scenario_id));
        dispatch(fetchScenarioTeams(scenario.scenario_id));
        resolve(response.data);
      }));
    },
    async onDryImportInjectFromXls(importId: string, input: InjectsImportInput): Promise<ImportTestSummary> {
      return dryImportXlsForScenario(scenario.scenario_id, importId, input).then(result => result.data);
    },
    onBulkDeleteInjects(param: InjectBulkProcessingInput): Promise<Inject[]> {
      return bulkDeleteInjectsSimple(param).then((result: { data: Inject[] }) => result?.data);
    },
    bulkTestInjects(param: InjectBulkProcessingInput): Promise<{
      uri: string;
      data: InjectTestStatusOutput[];
    }> {
      return bulkTestInjects(scenario.scenario_id, param).then(result => ({
        uri: `/admin/scenarios/${scenario.scenario_id}/tests`,
        data: result.data,
      }));
    },
  };
};

export default injectContextForScenario;
