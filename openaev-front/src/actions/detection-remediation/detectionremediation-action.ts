import { simplePostCall } from '../../utils/Action';
import { type PayloadInput } from '../../utils/api-types';

const DETECTION_REMEDIATION_URI = '/api/detection-remediations/ai';

export const postDetectionRemediationAIRulesByPayload = (collectorType: string, payloadInput: Partial<PayloadInput>) => {
  const uri = `${DETECTION_REMEDIATION_URI}/rules/${collectorType}`;
  return simplePostCall(uri, payloadInput);
};

export const postDetectionRemediationAIRulesByInject = (injectId: string, collectorType: string) => {
  const uri = `${DETECTION_REMEDIATION_URI}/rules/inject/${injectId}/collector/${collectorType}`;
  return simplePostCall(uri);
};
