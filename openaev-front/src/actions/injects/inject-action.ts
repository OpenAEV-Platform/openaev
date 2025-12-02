import { type Page } from '../../components/common/queryable/Page';
import { simpleCall, simplePostCall } from '../../utils/Action';
import {
  type DetectionRemediationOutput,
  type Document,
  type ExecutionTraceOutput,
  type InjectExportFromSearchRequestInput,
  type InjectExportRequestInput,
  type InjectIndividualExportRequestInput,
  type InjectStatusOutput,
  type InjectTarget,
  type Option,
  type SearchPaginationInput,
} from '../../utils/api-types';
import { MESSAGING$ } from '../../utils/Environment';

export const INJECT_URI = '/api/injects';

export const exportInjectSearch = (data: InjectExportFromSearchRequestInput) => {
  const uri = '/api/injects/search/export';
  return simplePostCall<ArrayBuffer>(uri, data, { responseType: 'arraybuffer' }).catch((error) => {
    MESSAGING$.notifyError('Could not request export of injects');
    throw error;
  });
};

export const exportInjects = (data: InjectExportRequestInput) => {
  const uri = '/api/injects/export';
  return simplePostCall<ArrayBuffer>(uri, data, { responseType: 'arraybuffer' }).catch((error) => {
    MESSAGING$.notifyError('Could not request export of injects');
    throw error;
  });
};

export const exportInject = (injectId: string, data: InjectIndividualExportRequestInput) => {
  const uri = `/api/injects/${injectId}/inject_export`;
  return simplePostCall<ArrayBuffer>(uri, data, { responseType: 'arraybuffer' }).catch((error) => {
    MESSAGING$.notifyError('Could not request export of inject');
    throw error;
  });
};

// -- TARGETS --

export const searchTargets = (injectId: string, targetType: string, searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `/api/injects/${injectId}/targets/${targetType}/search`;
  return simplePostCall<Page<InjectTarget>>(uri, data);
};

export const searchTargetOptions = (injectId: string, targetType: string, searchText = '') => {
  const params = { searchText };
  const uri = `/api/injects/${injectId}/targets/${targetType}/options`;
  return simpleCall<Option[]>(uri, { params });
};

export const searchTargetOptionsById = (targetType: string, ids: string[]) => {
  const data = ids;
  const uri = `/api/injects/targets/${targetType}/options`;
  return simplePostCall<Option[]>(uri, data);
};

// -- OPTION --

export const searchInjectLinkedToFindingsAsOption = (searchText: string = '', sourceId: string = '') => {
  const params = {
    searchText,
    sourceId,
  };
  return simpleCall<Option[]>(`${INJECT_URI}/findings/options`, { params });
};

export const searchInjectByIdAsOption = (ids: string[]) => {
  return simplePostCall<Option[]>(`${INJECT_URI}/options`, ids);
};

// -- EXECUTION TRACES --

export const getInjectTracesFromInjectAndTarget = (injectId: string = '', targetId: string = '', targetType: string = '') => {
  const params = {
    injectId,
    targetId,
    targetType,
  };
  return simpleCall<ExecutionTraceOutput[]>(`${INJECT_URI}/execution-traces`, { params });
};
export const getInjectStatusWithGlobalExecutionTraces = (injectId: string = '') => {
  const params = { injectId };
  return simpleCall<InjectStatusOutput>(`${INJECT_URI}/status`, { params });
};

// Detection Remediation
export const fetchPayloadDetectionRemediationsByInject = (injectId: string) => {
  const uri = `${INJECT_URI}/detection-remediations/${injectId}`;
  return simpleCall<DetectionRemediationOutput[]>(uri);
};

// Documents
export const fetchDocumentsPayloadByInject = async (injectId: string, payloadId: string | undefined) => {
  const uri = `${INJECT_URI}/${injectId}/payload/${payloadId}/documents`;
  const result = await simpleCall<Document[]>(uri);
  return result.data;
};
