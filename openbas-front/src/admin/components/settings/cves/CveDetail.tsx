import { useEffect, useState } from 'react';

import { fetchCve } from '../../../../actions/cve-actions';
import Tabs, { type TabsEntry } from '../../../../components/common/tabs/Tabs';
import useTabs from '../../../../components/common/tabs/useTabs';
import { useFormatter } from '../../../../components/i18n';
import { type CveOutput, type CveSimple } from '../../../../utils/api-types';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
import CveTabPanel from './CveTabPanel';
import GeneralVulnerabilityInfoTab from './GeneralVulnerabilityInfoTab';
import RemediationInfoTab from './RemediationInfoTab';
import TabLabelWithEE from './TabLabelWithEE';

interface Props { selectedCve: CveSimple }

export type CveStatus = 'loading' | 'loaded' | 'notAvailable';

const CveDetail = ({ selectedCve }: Props) => {
  const { t } = useFormatter();

  const {
    isValidated: isEE,
    openDialog: openEEDialog,
    setEEFeatureDetectedInfo,
  } = useEnterpriseEdition();

  const [cve, setCve] = useState<CveOutput | null>(null);
  const [cveStatus, setCveStatus] = useState<CveStatus>('loading');

  useEffect(() => {
    if (!selectedCve.cve_id) return;

    setCveStatus('loading');

    fetchCve(selectedCve.cve_id)
      .then((res) => {
        setCve(res.data);
        setCveStatus(res.data ? 'loaded' : 'notAvailable');
      })
      .catch(() => setCveStatus('notAvailable'));
  }, [selectedCve]);

  const tabEntries: TabsEntry[] = [{
    key: 'General',
    label: t('General'),
  }, {
    key: 'Remediation',
    label: <TabLabelWithEE label={t('Remediation')} />,
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
      case 'Remediation':
        return (
          <CveTabPanel status={cveStatus} cve={cve}>
            <RemediationInfoTab cve={cve!} />
          </CveTabPanel>
        );
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

export default CveDetail;
