import { simpleCall, simplePostCall } from '../../utils/Action';
import { type SearchPaginationInput } from '../../utils/api-types';

const FINDING_URI = '/api/findings';

export const fetchFinding = (findingId: string) => {
  return simpleCall(`${FINDING_URI}/${findingId}`);
};

export const searchFindings = (searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${FINDING_URI}/search`;
  return simplePostCall(uri, data);
};

export const searchFindingsForInjects = (injectId: string, searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${FINDING_URI}/injects/${injectId}/search`;
  return simplePostCall(uri, data);
};

export const searchFindingsOnEndpoint = (endpointId: string, searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${FINDING_URI}/endpoints/${endpointId}/search`;
  return simplePostCall(uri, data);
};

export const searchFindingsForSimulations = (simulationId: string, searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${FINDING_URI}/exercises/${simulationId}/search`;
  return simplePostCall(uri, data);
};

export const searchFindingsForScenarios = (scenarioId: string, searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${FINDING_URI}/scenarios/${scenarioId}/search`;
  return simplePostCall(uri, data);
};

// -- OCCURRENCES --

export const searchFindingOccurrences = (findingId: string, searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${FINDING_URI}/${findingId}/occurrences/search`;
  return simplePostCall(uri, data);
};
