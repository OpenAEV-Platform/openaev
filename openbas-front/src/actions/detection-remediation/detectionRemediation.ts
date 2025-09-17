import { simplePostCall } from "../../utils/Action";
import { DetectionRemediationCreationInput, PayloadUpdateInput } from "../../utils/api-types";

export const postDetectionRemediationAIRulesCrowdstrike = (payloadInput: PayloadUpdateInput) => {
  const uri = '/api/detection-remediations/rules/crowdstrike';
  return simplePostCall(uri, payloadInput);
};

export const postDetectionRemediationAIRulesCrowdstrikeByIdRemediation = (detectionRemediationCreationInput:DetectionRemediationCreationInput) => {
  return simplePostCall(`/api/detection-remediations/inject/rules/crowdstrike`, detectionRemediationCreationInput);
};