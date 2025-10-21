import { useEffect, useState } from 'react';

import { fetchVulnerabilityByExternalId } from '../../../actions/vulnerability-actions';
import type { Page } from '../../../components/common/queryable/Page';
import { type Header } from '../../../components/common/SortHeadersList';
import Tabs, { type TabsEntry } from '../../../components/common/tabs/Tabs';
import useTabs from '../../../components/common/tabs/useTabs';
import { useFormatter } from '../../../components/i18n';
import { type AggregatedFindingOutput, type RelatedFindingOutput, type SearchPaginationInput, type VulnerabilityOutput } from '../../../utils/api-types';
import useEnterpriseEdition from '../../../utils/hooks/useEnterpriseEdition';
import GeneralVulnerabilityInfoTab from '../settings/vulnerabilities/GeneralVulnerabilityInfoTab';
import RelatedInjectsTab from '../settings/vulnerabilities/RelatedInjectsTab';
import RemediationInfoTab from '../settings/vulnerabilities/RemediationInfoTab';
import TabLabelWithEE from '../settings/vulnerabilities/TabLabelWithEE';
import { type VulnerabilityStatus } from '../settings/vulnerabilities/VulnerabilityDetail';
import VulnerabilityTabPanel from '../settings/vulnerabilities/VulnerabilityTabPanel';

interface Props {
  searchFindings: (input: SearchPaginationInput) => Promise<{ data: Page<RelatedFindingOutput> }>;
  selectedFinding: AggregatedFindingOutput;
  additionalHeaders?: Header[];
  additionalFilterNames?: string[];
  contextId?: string;
  onCvssScore?: (score: number) => void;
}

const FindingDetail = ({
  searchFindings,
  selectedFinding,
  contextId,
  additionalHeaders = [],
  additionalFilterNames = [],
  onCvssScore,
}: Props) => {
  const { t } = useFormatter();

  const {
    isValidated: isEE,
    openDialog: openEEDialog,
    setEEFeatureDetectedInfo,
  } = useEnterpriseEdition();

  const isCVE = selectedFinding.finding_type === 'cve';

  const [vulnerability, setVulnerability] = useState<VulnerabilityOutput | null>(null);
  const [vulnerabilityStatus, setVulnerabilityStatus] = useState<VulnerabilityStatus>('loading');

  useEffect(() => {
    if (!isCVE || !selectedFinding.finding_value) return;

    setVulnerabilityStatus('loading');

    fetchVulnerabilityByExternalId(selectedFinding.finding_value)
      .then((res) => {
        setVulnerability(res.data);
        if (res.data?.vulnerability_cvss_v31 && onCvssScore) {
          onCvssScore(res.data.vulnerability_cvss_v31);
        }

        setVulnerabilityStatus(res.data ? 'loaded' : 'notAvailable');
      })
      .catch(() => setVulnerabilityStatus('notAvailable'));
  }, [selectedFinding, isCVE]);

  const tabEntries: TabsEntry[] = isCVE
    ? [{
        key: 'General',
        label: t('General'),
      }, {
        key: 'Related Injects',
        label: t('Related Injects'),
      }, {
        key: 'Remediation',
        label: <TabLabelWithEE label={t('Remediation')} />,
      }]
    : [{
        key: 'Related Injects',
        label: t('Related Injects'),
      }];
  const { currentTab, handleChangeTab } = useTabs(tabEntries[0].key);

  const renderTabPanels = () => {
    switch (currentTab) {
      case 'General':
        return (
          <VulnerabilityTabPanel status={vulnerabilityStatus} vulnerability={vulnerability}>
            <GeneralVulnerabilityInfoTab vulnerability={vulnerability!} />
          </VulnerabilityTabPanel>
        );
      case 'Related Injects':
        return (
          <RelatedInjectsTab
            searchFindings={searchFindings}
            contextId={contextId}
            finding={selectedFinding}
            additionalHeaders={additionalHeaders}
            additionalFilterNames={additionalFilterNames}
          />
        );
      case 'Remediation':
        return isEE
          ? (
              <VulnerabilityTabPanel status={vulnerabilityStatus} vulnerability={vulnerability}>
                <RemediationInfoTab vulnerability={vulnerability!} />
              </VulnerabilityTabPanel>
            )
          : null;
      default:
        return null;
    }
  };

  useEffect(() => {
    if (currentTab === 'Remediation' && !isEE) {
      handleChangeTab('General');
      setEEFeatureDetectedInfo(t('Remediation'));
      openEEDialog();
    }
  }, [currentTab, isEE]);

  return (
    <>
      <Tabs
        entries={tabEntries}
        currentTab={currentTab}
        onChange={newValue => handleChangeTab(newValue)}
      />
      {renderTabPanels()}
    </>
  );
};

export default FindingDetail;
