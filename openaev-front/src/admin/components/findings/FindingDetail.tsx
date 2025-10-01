import { useEffect, useState } from 'react';

import { fetchCveByExternalId } from '../../../actions/cve-actions';
import type { Page } from '../../../components/common/queryable/Page';
import { type Header } from '../../../components/common/SortHeadersList';
import Tabs, { type TabsEntry } from '../../../components/common/tabs/Tabs';
import useTabs from '../../../components/common/tabs/useTabs';
import { useFormatter } from '../../../components/i18n';
import { type AggregatedFindingOutput, type CveOutput, type RelatedFindingOutput, type SearchPaginationInput } from '../../../utils/api-types';
import useEnterpriseEdition from '../../../utils/hooks/useEnterpriseEdition';
import { type CveStatus } from '../settings/cves/CveDetail';
import CveTabPanel from '../settings/cves/CveTabPanel';
import GeneralVulnerabilityInfoTab from '../settings/cves/GeneralVulnerabilityInfoTab';
import RelatedInjectsTab from '../settings/cves/RelatedInjectsTab';
import RemediationInfoTab from '../settings/cves/RemediationInfoTab';
import TabLabelWithEE from '../settings/cves/TabLabelWithEE';

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

  const [cve, setCve] = useState<CveOutput | null>(null);
  const [cveStatus, setCveStatus] = useState<CveStatus>('loading');

  useEffect(() => {
    if (!isCVE || !selectedFinding.finding_value) return;

    setCveStatus('loading');

    fetchCveByExternalId(selectedFinding.finding_value)
      .then((res) => {
        setCve(res.data);
        if (res.data?.cve_cvss_v31 && onCvssScore) {
          onCvssScore(res.data.cve_cvss_v31);
        }

        setCveStatus(res.data ? 'loaded' : 'notAvailable');
      })
      .catch(() => setCveStatus('notAvailable'));
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
          <CveTabPanel status={cveStatus} cve={cve}>
            <GeneralVulnerabilityInfoTab cve={cve!} />
          </CveTabPanel>
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
              <CveTabPanel status={cveStatus} cve={cve}>
                <RemediationInfoTab cve={cve!} />
              </CveTabPanel>
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
