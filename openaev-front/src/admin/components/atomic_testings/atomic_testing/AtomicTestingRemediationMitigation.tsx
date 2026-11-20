import { type RemediationMitigationCategory } from '../../remediation_mitigation/remediationMitigationMock';
import RemediationMitigationPanel from '../../remediation_mitigation/RemediationMitigationPanel';
import AtomicTestingMitigations from './AtomicTestingMitigations';
import useHostLevelMitigations from './useHostLevelMitigations';

interface Props {
  category: RemediationMitigationCategory;
  injectTitle?: string;
  injectType?: string;
}

// PROTOTYPE: the Remediation tab of an atomic testing. A remediation has no vendor, so its list is the
// groups of assets only (same grouping as the host-level part of the Mitigation tab).
const AtomicTestingRemediation = ({ injectTitle, injectType }: Omit<Props, 'category'>) => {
  const hostLevel = useHostLevelMitigations(injectType, injectTitle, 'remediation');
  return <RemediationMitigationPanel category="remediation" payloadName={injectTitle} hostLevel={hostLevel} />;
};

// PROTOTYPE: the "Mitigation" and "Remediation" tabs of an atomic testing (read-mostly).
const AtomicTestingRemediationMitigation = ({ category, injectTitle, injectType }: Props) => (
  category === 'mitigation'
    ? <AtomicTestingMitigations injectTitle={injectTitle} injectType={injectType} />
    : <AtomicTestingRemediation injectTitle={injectTitle} injectType={injectType} />
);

export default AtomicTestingRemediationMitigation;
