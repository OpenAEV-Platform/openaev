import { simplePostCall } from '../../utils/Action';
import { type PayloadInput } from '../../utils/api-types';

const DETECTION_REMEDIATION_URI = '/api/detection-remediations/ai';

export const postDetectionRemediationAIRulesByPayload = (
  securityPlatformId: string,
  payloadInput: Partial<PayloadInput>,
  agentSlug?: string,
) => {
  const uri = `${DETECTION_REMEDIATION_URI}/rules/${securityPlatformId}`;
  const body: Partial<PayloadInput> & { agent_slug?: string } = { ...payloadInput };
  if (agentSlug) {
    body.agent_slug = agentSlug;
  }
  return simplePostCall(uri, body);
};

export const postDetectionRemediationAIRulesByInject = (
  injectId: string,
  securityPlatformId: string,
  agentSlug?: string,
) => {
  const slugQuery = agentSlug ? `?agent_slug=${encodeURIComponent(agentSlug)}` : '';
  const uri = `${DETECTION_REMEDIATION_URI}/rules/inject/${injectId}/security-platform/${securityPlatformId}${slugQuery}`;
  return simplePostCall(uri);
};
