import { isAiEligibleSecurityPlatformName } from '../../../../constants/Entities';

// Remediations are keyed by security platform (manual platforms included): AI rule
// generation is only wired for vendors the generator understands, inferred from the
// platform name (e.g. "CrowdStrike Falcon", "Splunk Enterprise Security").
const useIsEligibleArianeSecurityPlatform = (securityPlatformName?: string) => {
  return isAiEligibleSecurityPlatformName(securityPlatformName);
};

export default useIsEligibleArianeSecurityPlatform;
