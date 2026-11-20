import { type CollectorType, type ControlType } from './remediationMitigationMock';
import { type MockPayload, type Verdict } from './remediationMitigationStore';

// PROTOTYPE - nothing is sent. Builds the JSON that WOULD be sent to XTM One for the current
// context, to discuss what each request carries. The raw finding value is never part of it.

export interface RequestContext {
  findingType?: string;
  verdict?: {
    name: 'prevention' | 'detection' | 'vulnerability';
    value: Verdict;
  };
}

interface BuildInput {
  payload: MockPayload;
  includeCommand: boolean;
  context?: RequestContext;
  controlType?: Extract<ControlType, 'detection' | 'prevention'>;
  collector?: CollectorType;
}

const common = ({ payload, includeCommand, context }: BuildInput) => ({
  name: payload.name,
  description: payload.description,
  platform: payload.platform,
  arch: payload.arch,
  ...(includeCommand ? { command: payload.command } : {}),
  attack_techniques: payload.attack_techniques,
  elevation_required: payload.elevation_required,
  agent_privilege: payload.agent_privilege,
  ...(context?.findingType ? { finding_type: context.findingType } : {}),
  ...(context?.verdict ? { verdict: { [context.verdict.name]: context.verdict.value } } : {}),
});

// No collector_type, no control_type: a remediation depends on the target platform, never on a tool.
export const buildRemediationRequest = (input: BuildInput) => ({
  kind: 'remediation',
  ...common(input),
});

// control_type always, collector_type only when the rule is vendor-specific.
export const buildMitigationRequest = (input: BuildInput) => ({
  kind: 'mitigation',
  ...common(input),
  control_type: input.controlType ?? 'detection',
  ...(input.collector ? { collector_type: input.collector } : {}),
});

export const REMEDIATION_PROMPT = 'You are a security hardening advisor inside OpenAEV. You receive ONE weakness exploited in a simulation. Recommend how to PERMANENTLY remove or reduce its root cause. Remediation = patch, upgrade, configuration/hardening change, credential or access hygiene. Do NOT recommend detection rules, alerts, monitoring or blocking tools: those are mitigations. Use the command only to understand which weakness was exploited, never to block the tool. Give steps only for the given target platform. Do not invent GPO names, registry keys, cmdlets, KB numbers or settings; if unsure, describe it generally and set verify=true. Max 5 actions, ordered by impact, each with prerequisites and change risk. List your assumptions. Never invent URLs. This is starting-point guidance; never claim it guarantees a fix. Output JSON only.';

export const MITIGATION_PROMPT = 'You are a detection and prevention content engineer inside OpenAEV. You receive ONE simulated attack behavior, a control_type (detection or prevention) and, when vendor-specific, a target security tool. Produce a compensating control. Do NOT recommend patching or hardening the weakness. Use only fields and syntax that exist in the target tool; if the tool or control_type is unsupported, return unsupported=true with a reason. Keep the logic as narrow as the behavior allows and state what it covers and misses (coverage and false-positive notes). For prevention, recommend monitor mode first. If the finding type is provided, focus on the observed outcome but note common variants. If your recommendation blocks or detects behavior but does not remove the weakness, keep it as mitigation and state that the weakness remains. Never output working attack code.';
