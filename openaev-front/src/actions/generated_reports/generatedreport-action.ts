import { simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';
import { type Exercise, type GeneratedReport, type GeneratedReportInput, type GeneratedReportStatusInput, type Scenario } from '../../utils/api-types';

const generatedReportsUri = (exerciseId: Exercise['exercise_id']) => `/api/exercises/${exerciseId}/generated-reports`;

export const fetchGeneratedReports = (exerciseId: Exercise['exercise_id']) => {
  return simpleCall(generatedReportsUri(exerciseId));
};

export const fetchGeneratedReport = (exerciseId: Exercise['exercise_id'], generatedReportId: GeneratedReport['generated_report_id']) => {
  return simpleCall(`${generatedReportsUri(exerciseId)}/${generatedReportId}`);
};

export const createGeneratedReport = (exerciseId: Exercise['exercise_id'], data: GeneratedReportInput) => {
  return simplePostCall(generatedReportsUri(exerciseId), data, undefined, true, false);
};

export const updateGeneratedReportStatus = (
  exerciseId: Exercise['exercise_id'],
  generatedReportId: GeneratedReport['generated_report_id'],
  data: GeneratedReportStatusInput,
) => {
  return simplePutCall(`${generatedReportsUri(exerciseId)}/${generatedReportId}/status`, data, undefined, false, false);
};

export const uploadGeneratedReportDocument = (
  exerciseId: Exercise['exercise_id'],
  generatedReportId: GeneratedReport['generated_report_id'],
  file: File,
) => {
  const formData = new FormData();
  formData.append('file', file);
  return simplePostCall(`${generatedReportsUri(exerciseId)}/${generatedReportId}/document`, formData, undefined, false, false);
};

export const deleteGeneratedReport = (exerciseId: Exercise['exercise_id'], generatedReportId: GeneratedReport['generated_report_id']) => {
  return simpleDelCall(`${generatedReportsUri(exerciseId)}/${generatedReportId}`);
};

export const downloadGeneratedReportUrl = (exerciseId: Exercise['exercise_id'], generatedReportId: GeneratedReport['generated_report_id']) => {
  return `/api/exercises/${exerciseId}/generated-reports/${generatedReportId}/file`;
};

// -- Global reports (not scoped to a single simulation, covering every simulation) --

const globalGeneratedReportsUri = '/api/generated-reports';

export const fetchGlobalGeneratedReports = () => {
  return simpleCall(globalGeneratedReportsUri);
};

/** Every report regardless of scope (global/simulation/scenario), for the unified "Reports" page. */
export const fetchAllGeneratedReports = () => {
  return simpleCall(`${globalGeneratedReportsUri}/all`);
};

export const createGlobalGeneratedReport = (data: GeneratedReportInput) => {
  return simplePostCall(globalGeneratedReportsUri, data, undefined, true, false);
};

export const updateGlobalGeneratedReportStatus = (
  generatedReportId: GeneratedReport['generated_report_id'],
  data: GeneratedReportStatusInput,
) => {
  return simplePutCall(`${globalGeneratedReportsUri}/${generatedReportId}/status`, data, undefined, false, false);
};

export const uploadGlobalGeneratedReportDocument = (
  generatedReportId: GeneratedReport['generated_report_id'],
  file: File,
) => {
  const formData = new FormData();
  formData.append('file', file);
  return simplePostCall(`${globalGeneratedReportsUri}/${generatedReportId}/document`, formData, undefined, false, false);
};

export const deleteGlobalGeneratedReport = (generatedReportId: GeneratedReport['generated_report_id']) => {
  return simpleDelCall(`${globalGeneratedReportsUri}/${generatedReportId}`);
};

export const downloadGlobalGeneratedReportUrl = (generatedReportId: GeneratedReport['generated_report_id']) => {
  return `${globalGeneratedReportsUri}/${generatedReportId}/file`;
};

// -- Scenario reports (aggregate every run of one scenario within a comparison window) --

const scenarioGeneratedReportsUri = (scenarioId: Scenario['scenario_id']) => `/api/scenarios/${scenarioId}/generated-reports`;

export const fetchScenarioGeneratedReports = (scenarioId: Scenario['scenario_id']) => {
  return simpleCall(scenarioGeneratedReportsUri(scenarioId));
};

export const createScenarioGeneratedReport = (scenarioId: Scenario['scenario_id'], data: GeneratedReportInput) => {
  return simplePostCall(scenarioGeneratedReportsUri(scenarioId), data, undefined, true, false);
};

export const updateScenarioGeneratedReportStatus = (
  scenarioId: Scenario['scenario_id'],
  generatedReportId: GeneratedReport['generated_report_id'],
  data: GeneratedReportStatusInput,
) => {
  return simplePutCall(`${scenarioGeneratedReportsUri(scenarioId)}/${generatedReportId}/status`, data, undefined, false, false);
};

export const uploadScenarioGeneratedReportDocument = (
  scenarioId: Scenario['scenario_id'],
  generatedReportId: GeneratedReport['generated_report_id'],
  file: File,
) => {
  const formData = new FormData();
  formData.append('file', file);
  return simplePostCall(`${scenarioGeneratedReportsUri(scenarioId)}/${generatedReportId}/document`, formData, undefined, false, false);
};

export const deleteScenarioGeneratedReport = (scenarioId: Scenario['scenario_id'], generatedReportId: GeneratedReport['generated_report_id']) => {
  return simpleDelCall(`${scenarioGeneratedReportsUri(scenarioId)}/${generatedReportId}`);
};

export const downloadScenarioGeneratedReportUrl = (scenarioId: Scenario['scenario_id'], generatedReportId: GeneratedReport['generated_report_id']) => {
  return `${scenarioGeneratedReportsUri(scenarioId)}/${generatedReportId}/file`;
};
