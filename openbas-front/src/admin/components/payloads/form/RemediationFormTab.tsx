import { Box, Tab, Tabs, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type SyntheticEvent, useContext, useEffect, useState } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

import { fetchCollectors } from '../../../../actions/Collector';
import type { CollectorHelper } from '../../../../actions/collectors/collector-helper';
import { fetchCollectorsForPayload } from '../../../../actions/payloads/payload-actions';
import CKEditor from '../../../../components/CKEditor';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { COLLECTOR_LIST } from '../../../../constants/Entities';
import { useHelper } from '../../../../store';
import { type Collector } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider';
import RestrictionAccess from '../../../../utils/permissions/RestrictionAccess';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';

interface RemediationFormTabProps { payloadId?: string }

const RemediationFormTab = ({ payloadId }: RemediationFormTabProps) => {
  const [tabs, setTabs] = useState<Collector[]>([]);
  const [activeTab, setActiveTab] = useState<number>(0);
  const { control } = useFormContext();
  const { t } = useFormatter();
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);
  const [loading, setLoading] = useState(false);

  const handleActiveTabChange = (_: SyntheticEvent, newValue: number) => {
    setActiveTab(newValue);
  };

  const hasPlatformSettingsCapabilities = ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS);

  const { collectors } = useHelper((helper: CollectorHelper) => ({ collectors: helper.getCollectors() }));
  useDataLoader(() => {
    if (hasPlatformSettingsCapabilities) {
      setLoading(true);
      dispatch(fetchCollectors()).finally(() => {
        setLoading(false);
      }); ;
    } else if (payloadId) {
      setLoading(true);
      dispatch(fetchCollectorsForPayload(payloadId)).finally(() => {
        setLoading(false);
      });
    }
  });

  useEffect(() => {
    if (collectors.length > 0) {
      const filteredCollectors = collectors.filter((collector: Collector) =>
        COLLECTOR_LIST.includes(collector.collector_type),
      ).sort((a: Collector, b: Collector) => a.collector_name.localeCompare(b.collector_name));
      setTabs(filteredCollectors);
    }
  }, [collectors]);

  return (
    <>
      <Typography variant="h5" gutterBottom>{t('Security platform')}</Typography>
      {loading && <Loader variant="inElement" />}
      {(hasPlatformSettingsCapabilities || payloadId) ? (
        <>
          {tabs.length === 0
            ? (
                <Typography>
                  {t('No collector configured.')}
                </Typography>
              )
            : (
                <>
                  <Tabs
                    value={activeTab}
                    onChange={handleActiveTabChange}
                    aria-label="tabs for payload form"
                  >
                    {tabs.map((tab, index) => (
                      <Tab
                        key={tab.collector_name}
                        label={(
                          <Box display="flex" alignItems="center">
                            <img
                              src={`/api/images/collectors/${tab.collector_type}`}
                              alt={tab.collector_type}
                              style={{
                                width: 20,
                                height: 20,
                                borderRadius: 4,
                                marginRight: theme.spacing(2),
                              }}
                            />
                            {tab.collector_name}
                          </Box>
                        )}
                        value={index}
                      />
                    ))}
                  </Tabs>
                  { tabs.map(tab => (
                    <div
                      key={tab.collector_type}
                      style={{
                        height: '250px',
                        position: 'relative',
                        display: tab.collector_type === tabs[activeTab].collector_type ? 'block' : 'none',
                      }}
                    >
                      <Controller
                        name={'remediations.' + tab.collector_type}
                        control={control}
                        defaultValue={{ content: '' }}
                        render={({ field: { onChange, value } }) => (
                          <CKEditor
                            id="payload-remediation-editor"
                            data={value?.content}
                            onChange={(_, editor) => {
                              const newValue: {
                                content: string;
                                remediationId?: string;
                              } = { content: editor.getData() };
                              if (value?.remediationId) {
                                newValue.remediationId = value?.remediationId;
                              }
                              onChange(newValue);
                            }}
                          />
                        )}
                      />
                    </div>
                  ))}
                </>
              )}
        </>
      ) : (<RestrictionAccess restrictedField="collectors" />)}
    </>
  );
};

export default RemediationFormTab;
