import { simpleCall, simplePatchCall, simplePostCall, simplePutCall } from '../../utils/Action';
import { type FindingArchiveSettingsInput, type SearchPaginationInput } from '../../utils/api-types';

const FINDING_URI = '/api/findings';
const STABLE_FINDING_URI = '/api/stable-findings';

export const fetchStableFinding = (findingId: string) => {
  return simpleCall(`${STABLE_FINDING_URI}/${findingId}`);
};

export const fetchStableFindingSummary = (findingId: string) => {
  return simpleCall(`${STABLE_FINDING_URI}/${findingId}/summary`);
};

export const fetchStableFindingLocations = (findingId: string) => {
  return simpleCall(`${STABLE_FINDING_URI}/${findingId}/locations`);
};

export const searchStableFindings = (searchPaginationInput: SearchPaginationInput) => {
  return simplePostCall(`${STABLE_FINDING_URI}/search`, searchPaginationInput);
};

export const searchStableFindingOccurrences = (
  findingId: string,
  searchPaginationInput: SearchPaginationInput,
) => {
  return simplePostCall(`${STABLE_FINDING_URI}/${findingId}/occurrences/search`, searchPaginationInput);
};

// -- ARCHIVE SETTINGS --

export const fetchFindingArchiveDays = () => {
  return simpleCall(`${FINDING_URI}/settings/archive-days`);
};

export const updateFindingArchiveDays = (data: FindingArchiveSettingsInput) => {
  return simplePutCall(`${FINDING_URI}/settings/archive-days`, data);
};

// -- BULK ARCHIVE --

export const archiveFindingsBulk = (data: {
  finding_ids: string[];
  archived: boolean;
}) => {
  return simplePatchCall(`${FINDING_URI}/archive/bulk`, data);
};

export const fetchFinding = (findingId: string) => {
  return simpleCall(`${FINDING_URI}/${findingId}`);
};

// Stable identity summary: true first/last seen and distinct impact counts across every
// occurrence, computed server-side.
export const fetchFindingSummary = (findingId: string) => {
  return simpleCall(`${FINDING_URI}/${findingId}/summary`);
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

// -- DISTINCT --

export const searchDistinctFindings = (searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${FINDING_URI}/search?distinct=true`;
  return simplePostCall(uri, data);
};

export const searchDistinctFindingsForInjects = (injectId: string, searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${FINDING_URI}/injects/${injectId}/search?distinct=true`;
  return simplePostCall(uri, data);
};

export const searchDistinctFindingsOnEndpoint = (endpointId: string, searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${FINDING_URI}/endpoints/${endpointId}/search?distinct=true`;
  return simplePostCall(uri, data);
};

export const searchDistinctFindingsForSimulations = (simulationId: string, searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${FINDING_URI}/exercises/${simulationId}/search?distinct=true`;
  return simplePostCall(uri, data);
};

export const searchDistinctFindingsForScenarios = (scenarioId: string, searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${FINDING_URI}/scenarios/${scenarioId}/search?distinct=true`;
  return simplePostCall(uri, data);
};

// -- LEGACY ALSO DETECTED ON --

// Kept for legacy Finding detail consumers. Stable Finding details use
// fetchStableFindingLocations(), because Location belongs to an occurrence.
export const searchFindingsAlsoDetectedOn = (findingId: string, searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${FINDING_URI}/${findingId}/also-detected-on/search`;
  return simplePostCall(uri, data);
};
