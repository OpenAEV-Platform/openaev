import { simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';
import {
  type Reporting,
  type ReportingGenerateInput,
  type ReportingInput,
  type ReportingScheduleInput,
  type SearchPaginationInput,
} from '../../utils/api-types';

export const REPORTING_URI = '/api/reportings';

// -- CRUD --

export const createReporting = (input: ReportingInput) => {
  return simplePostCall(REPORTING_URI, input);
};

export const searchReportings = (searchPaginationInput: SearchPaginationInput) => {
  return simplePostCall(`${REPORTING_URI}/search`, searchPaginationInput);
};

export const fetchReporting = (id: Reporting['reporting_id']) => {
  return simpleCall(`${REPORTING_URI}/${id}`);
};

export const updateReporting = (id: Reporting['reporting_id'], input: ReportingInput) => {
  return simplePutCall(`${REPORTING_URI}/${id}`, input);
};

export const deleteReporting = (id: Reporting['reporting_id']) => {
  return simpleDelCall(`${REPORTING_URI}/${id}`);
};

// -- CONTEXT (per-entity reports) --

export const fetchReportingsByContext = (
  contextType: Reporting['reporting_context_type'],
  contextId?: string,
) => {
  return simpleCall(
    contextId
      ? `${REPORTING_URI}/context/${contextType}/${contextId}`
      : `${REPORTING_URI}/context/${contextType}`,
  );
};

// -- GENERATIONS --

export const generateReporting = (id: Reporting['reporting_id'], input: ReportingGenerateInput) => {
  return simplePostCall(`${REPORTING_URI}/${id}/generate`, input);
};

export const fetchReportingGenerations = (id: Reporting['reporting_id']) => {
  return simpleCall(`${REPORTING_URI}/${id}/generations`);
};

export const fetchReportingGeneration = (generationId: string) => {
  return simpleCall(`${REPORTING_URI}/generations/${generationId}`);
};

export const deleteReportingGeneration = (generationId: string) => {
  return simpleDelCall(`${REPORTING_URI}/generations/${generationId}`);
};

export const downloadReportingGenerationUrl = (generationId: string) => {
  return `${REPORTING_URI}/generations/${generationId}/file`;
};

// -- SCHEDULES --

export const createReportingSchedule = (id: Reporting['reporting_id'], input: ReportingScheduleInput) => {
  return simplePostCall(`${REPORTING_URI}/${id}/schedules`, input);
};

export const updateReportingSchedule = (
  id: Reporting['reporting_id'],
  scheduleId: string,
  input: ReportingScheduleInput,
) => {
  return simplePutCall(`${REPORTING_URI}/${id}/schedules/${scheduleId}`, input);
};

export const deleteReportingSchedule = (id: Reporting['reporting_id'], scheduleId: string) => {
  return simpleDelCall(`${REPORTING_URI}/${id}/schedules/${scheduleId}`);
};
