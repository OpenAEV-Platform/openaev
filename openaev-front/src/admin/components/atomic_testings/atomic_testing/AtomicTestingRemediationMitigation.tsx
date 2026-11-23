import RemediationMitigationTabs from '../../remediation_mitigation/RemediationMitigationTabs';
import useActionContext from './useActionContext';

interface Props {
  injectTitle?: string;
  injectType?: string;
}

// PROTOTYPE: the Remediation tab of an atomic testing, with its three views: the remediation (with the findings it
// answers and the hosts it must be applied on), the vendor-specific mitigations and the generic ones.
const AtomicTestingRemediationMitigation = ({ injectTitle, injectType }: Props) => {
  const { affectedHosts, findingTypes } = useActionContext(injectType, injectTitle);
  return (
    <RemediationMitigationTabs
      payloadName={injectTitle}
      findingTypes={findingTypes}
      affectedHosts={affectedHosts}
    />
  );
};

export default AtomicTestingRemediationMitigation;
