import { useLocation, useNavigate } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
import { type RemediationMitigationCategory } from '../../remediation_mitigation/remediationMitigationMock';
import RemediationMitigationPanel from '../../remediation_mitigation/RemediationMitigationPanel';
import useActionContext from './useActionContext';

interface Props {
  category: RemediationMitigationCategory;
  // The result the tab is opened on: the mitigation is tied to it, the remediation to the action.
  injectId?: string;
  injectTitle?: string;
  injectType?: string;
}

// PROTOTYPE: the "Mitigation" and "Remediation" tabs of an atomic testing (read-mostly). Mitigation has its own
// Vendor-specific / Generic tabs; the two tabs link to each other, on the same inject.
const AtomicTestingRemediationMitigation = ({ category, injectId, injectTitle, injectType }: Props) => {
  const { t } = useFormatter();
  const { affectedHosts, findingTypes } = useActionContext(injectType, injectTitle);
  const location = useLocation();
  const navigate = useNavigate();
  const {
    isValidated: isValidatedEnterpriseEdition,
    openDialog: openEnterpriseEditionDialog,
    setEEFeatureDetectedInfo,
  } = useEnterpriseEdition();
  // Both tabs are routes of the same page: only the last segment changes.
  const goTo = (segment: 'mitigations' | 'remediations') => {
    // The Remediation tab is Enterprise Edition: the link inside the page goes through the same gate as the tab.
    if (segment === 'remediations' && !isValidatedEnterpriseEdition) {
      setEEFeatureDetectedInfo(t('Remediation'));
      openEnterpriseEditionDialog();
      return;
    }
    navigate({
      pathname: location.pathname.replace(/\/(mitigations|remediations)$/, `/${segment}`),
      search: location.search,
    });
  };
  return (
    <RemediationMitigationPanel
      category={category}
      payloadName={injectTitle}
      resultId={injectId}
      findingTypes={findingTypes}
      affectedHosts={affectedHosts}
      onSwitchToRemediation={() => goTo('remediations')}
      onSwitchToMitigation={() => goTo('mitigations')}
    />
  );
};

export default AtomicTestingRemediationMitigation;
