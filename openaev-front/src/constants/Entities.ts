// General Names

export const INJECT = 'INJECT';
export const SIMULATION = 'SIMULATION';
export const SCENARIO = 'SCENARIO';

// Detection remediation AI eligibility: the AI rule generator only knows how to
// produce (and parse) rules for these vendors. Remediations are keyed by security
// platform (including manual ones), so eligibility is inferred from the platform name.
export const SECURITY_PLATFORM_AI_KEYWORDS = ['crowdstrike', 'splunk'];
export const isAiEligibleSecurityPlatformName = (name?: string | null): boolean => {
  if (!name) return false;
  const lower = name.toLowerCase();
  return SECURITY_PLATFORM_AI_KEYWORDS.some(keyword => lower.includes(keyword));
};
export const PAYLOAD_TYPE_LIST_AI = ['DnsResolution', 'Command'];
