import { useLocation, useNavigate } from 'react-router';

import RemediationMitigationPanel from '../../remediation_mitigation/RemediationMitigationPanel';
import useHostLevelMitigations from './useHostLevelMitigations';

interface Props {
  injectTitle?: string;
  injectType?: string;
}

// PROTOTYPE - MOCK DATA ONLY. Mitigation tab of an atomic testing: the vendors that can generate each
// control type are listed on the left, and so are the groups of assets (host-level), one group per
// distinct set of inputs of the request.
const AtomicTestingMitigations = ({ injectTitle, injectType }: Props) => {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const hostLevel = useHostLevelMitigations(injectType, injectTitle);

  // .../atomic_testings/<id>/mitigations -> .../atomic_testings/<id>/remediations
  const switchToRemediation = () => navigate(pathname.replace(/\/mitigations$/, '/remediations'));

  return (
    <RemediationMitigationPanel
      category="mitigation"
      payloadName={injectTitle}
      hostLevel={hostLevel}
      onSwitchToRemediation={switchToRemediation}
    />
  );
};

export default AtomicTestingMitigations;
