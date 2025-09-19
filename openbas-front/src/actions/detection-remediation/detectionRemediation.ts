import { simplePostCall } from "../../utils/Action";
import { DetectionRemediationCreationInput, PayloadInput } from "../../utils/api-types";

export const postDetectionRemediationAIRules = (payloadInput: PayloadInput) => {
  const uri = '/api/detection-remediations/ai/rules';
  return simplePostCall(uri, payloadInput);
};

export const postDetectionRemediationAIRulesCrowdstrikeByIdRemediation = (detectionRemediationCreationInput:DetectionRemediationCreationInput) => {
  return simplePostCall(`/api/detection-remediations/ai/inject/rules/crowdstrike`, detectionRemediationCreationInput);
};