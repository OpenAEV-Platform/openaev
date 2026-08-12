import { Box } from '@mui/material';
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
import FindingComments from './FindingComments';
import FindingTriageHistory from './FindingTriageHistory';
import OCSFRemediationTab from './OCSFRemediationTab';

interface Props {
  searchFindings: (input: SearchPaginationInput) => Promise<{ data: Page<RelatedFindingOutput> }>;
  selectedFinding: AggregatedFindingOutput;
  additionalHeaders?: Header[];
  additionalFilterNames?: string[];
  contextId?: string;
  onCvssScore?: (score: number) => void;
  /** See FindingTriageHistory's `refreshKey` prop - bumped by FindingOverview whenever a
   * triage change is confirmed, so the history tab refreshes immediately if it is already
   * mounted (not just on next tab switch). */
  triageRefreshKey?: number;
}

const FindingDetail = ({
  searchFindings,
  selectedFinding,
  contextId,
  additionalHeaders = [],
  additionalFilterNames = [],
  onCvssScore,
  triageRefreshKey,
}: Props) => {
  const { t } = useFormatter();

  const {
    isValidated: isEE,
    openDialog: openEEDialog,
    setEEFeatureDetectedInfo,
  } = useEnterpriseEdition();

  const isCVE = selectedFinding.finding_type === 'cve';
  const isOCSF = selectedFinding.finding_type === 'ocsf';

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

  let tabEntries: TabsEntry[];
  if (isCVE) {
    tabEntries = [{
      key: 'General',
      label: t('General'),
    }, {
      key: 'Related Injects',
      label: t('Related Injects'),
    }, {
      key: 'Remediation',
      label: <TabLabelWithEE label={t('Remediation')} />,
    }, {
      key: 'Comments',
      label: t('Comments'),
    }, {
      key: 'Triage History',
      label: t('Triage History'),
    }];
  } else if (isOCSF) {
    tabEntries = [{
      key: 'Related Injects',
      label: t('Related Injects'),
    }, {
      key: 'Remediation',
      // OCSF findings are produced by the Prowler injector, which is planned to become an
      // Enterprise Edition-gated integration: once that gate lands, only EE-licensed tenants
      // will ever generate OCSF findings in the first place. This badge (and the redirect below)
      // still matter for the edge case of a tenant whose EE license lapses after such findings
      // were already ingested - the data remains in the DB, but the feature should read as EE.
      label: <TabLabelWithEE label={t('Remediation')} />,
    }, {
      key: 'Comments',
      label: t('Comments'),
    }, {
      key: 'Triage History',
      label: t('Triage History'),
    }];
  } else {
    tabEntries = [{
      key: 'Related Injects',
      label: t('Related Injects'),
    }, {
      key: 'Comments',
      label: t('Comments'),
    }, {
      key: 'Triage History',
      label: t('Triage History'),
    }];
  }
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
        if (isOCSF) {
          // See the isOCSF tabEntries comment above: gated the same way as CVE remediation below,
          // since OCSF findings will only be reachable via the (future) EE-gated Prowler injector.
          return isEE ? <OCSFRemediationTab remediation={selectedFinding.finding_remediation} /> : null;
        }
        return isEE
          ? (
              <VulnerabilityTabPanel status={vulnerabilityStatus} vulnerability={vulnerability}>
                <RemediationInfoTab vulnerability={vulnerability!} />
              </VulnerabilityTabPanel>
            )
          : null;
      case 'Comments':
        return <FindingComments findingId={selectedFinding.finding_id} />;
      case 'Triage History':
        return <FindingTriageHistory findingId={selectedFinding.finding_id} refreshKey={triageRefreshKey} />;
      default:
        return null;
    }
  };

  useEffect(() => {
    if ((isCVE || isOCSF) && currentTab === 'Remediation' && !isEE) {
      // CVE findings fall back to the "General" tab (their first entry); OCSF findings have no
      // "General" tab, so they fall back to "Related Injects" instead - see the tabEntries lists
      // above for each type's first entry.
      handleChangeTab(isCVE ? 'General' : 'Related Injects');
      setEEFeatureDetectedInfo(t('Remediation'));
      openEEDialog();
    }
  }, [currentTab, isEE, isCVE, isOCSF]);

  // A lone tab (non-CVE findings only have "Related Injects") carries no navigation value: render
  // the tab bar only when there is a choice. The panel is separated from the tab bar only when the
  // bar is shown - otherwise the panel sits flush under the section label, with no trailing gap.
  const hasTabs = tabEntries.length > 1;

  return (
    <>
      {hasTabs && (
        <Tabs
          entries={tabEntries}
          currentTab={currentTab}
          onChange={newValue => handleChangeTab(newValue)}
        />
      )}
      <Box sx={{ marginTop: hasTabs ? 2 : 0 }}>{renderTabPanels()}</Box>
    </>
  );
};

export default FindingDetail;
