import { simplePostCall } from "../../utils/Action";
import { DetectionRemediationCreationInput, PayloadUpdateInput } from "../../utils/api-types";

export const postDetectionRemediationAIRulesCrowdstrike = (payloadInput: PayloadUpdateInput) => {
  const uri = '/api/detection-remediation/rules/crowdstrike';
  return simplePostCall(uri, payloadInput);
};

export const postDetectionRemediationAIRulesCrowdstrikeByIdRemediation = (detectionRemediationCreationInput:DetectionRemediationCreationInput) => {
  return simplePostCall(`/api/detection-remediation/inject/rules/crowdstrike`, detectionRemediationCreationInput);
};