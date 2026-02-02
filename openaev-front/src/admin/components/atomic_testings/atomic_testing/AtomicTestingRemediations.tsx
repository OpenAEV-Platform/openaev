import { Box, Paper, Tab, Tabs, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
// eslint-disable-next-line import/no-named-as-default
import DOMPurify from 'dompurify';
import { type SyntheticEvent, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useLocation, useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchCollectorsForAtomicTesting } from '../../../../actions/atomic_testings/atomic-testing-actions';
import { fetchCollectors } from '../../../../actions/Collector';
import type { CollectorHelper } from '../../../../actions/collectors/collector-helper';
import { postDetectionRemediationAIRulesByInject } from '../../../../actions/detection-remediation/detectionremediation-action';
import { fetchPayloadDetectionRemediationsByInject } from '../../../../actions/injects/inject-action';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { COLLECTOR_LIST } from '../../../../constants/Entities';
import { useHelper } from '../../../../store';
import {
  type Collector,
  type DetectionRemediationOutput,
  type InjectResultOverviewOutput,
} from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider';
import RestrictionAccess from '../../../../utils/permissions/RestrictionAccess';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { isNotEmptyField } from '../../../../utils/utils';
import DetectionRemediationInfo from '../../payloads/form/DetectionRemediationInfo';
import DetectionRemediationUseAriane from '../../payloads/form/DetectionRemediationUseAriane';
import { type SnapshotEditionRemediationType } from '../../payloads/utils/SnapshotRemediationContext';
import { useSnapshotRemediation } from '../../payloads/utils/useSnapshotRemediation';

const useStyles = makeStyles()(theme => ({
  paperContainer: {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr',
    gap: theme.spacing(3),
  },
  headerRemediation: {
    display: 'flex',
    marginTop: 20,
    width: '50%',
    justifyContent: 'space-between',
    marginBottom: 10,
  },

}));

const AtomicTestingRemediations = () => {
  const { injectId } = useParams() as { injectId: InjectResultOverviewOutput['inject_id'] };
  const dispatch = useAppDispatch();
  const { t } = useFormatter();
  const { classes } = useStyles();
  const theme = useTheme();
  const location = useLocation();
  const [tabs, setTabs] = useState<Collector[]>([]);
  const [activeTab, setActiveTab] = useState<number>(0);
  const [detectionRemediations, setDetectionRemediations] = useState<DetectionRemediationOutput[]>([]);
  const [hasFetchedRemediations, setHasFetchedRemediations] = useState(false);
  const ability = useContext(AbilityContext);

  const isRemediationTab = location.pathname.includes('/remediations');

  const hasPlatformSettingsCapabilities = ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS);
  const [loading, setLoading] = useState(false);

  const { collectors } = useHelper((helper: CollectorHelper) => ({ collectors: helper.getExistingCollectors() }));

  const { snapshot, setSnapshot } = useSnapshotRemediation();
  const [activeDetectionRemediation, setActiveDetectionRemediation] = useState<DetectionRemediationOutput>();

  const [displayedText, setDisplayedText] = useState<string>('');
  const [typing, setTyping] = useState<boolean>(!!snapshot?.get(tabs[activeTab]?.collector_type)?.isLoading);

  useDataLoader(() => {
    if (hasPlatformSettingsCapabilities) {
      setLoading(true);
      dispatch(fetchCollectors()).finally(() => {
        setLoading(false);
      });
    } else if (injectId) {
      setLoading(true);
      dispatch(fetchCollectorsForAtomicTesting(injectId)).finally(() => {
        setLoading(false);
      });
    }
  });

  // Filter valid collectors
  useEffect(() => {
    if (collectors.length > 0) {
      const filtered = collectors.filter((c: { collector_type: string }) =>
        COLLECTOR_LIST.includes(c.collector_type),
      ).sort((a: Collector, b: Collector) => a.collector_name.localeCompare(b.collector_name));
      setTabs(filtered);
    }
  }, [collectors]);

  useEffect(() => {
    if (isRemediationTab && injectId && !hasFetchedRemediations) {
      fetchPayloadDetectionRemediationsByInject(injectId).then((result) => {
        setDetectionRemediations(result.data);
        setHasFetchedRemediations(true);
      });
    }
  }, [isRemediationTab, injectId, hasFetchedRemediations]);

  useEffect(() => {
    if (activeTab >= tabs.length) {
      setActiveTab(0);
    }
    setTyping(!!snapshot?.get(tabs[activeTab]?.collector_type)?.isLoading);
  }, [tabs, activeTab]);

  const handleActiveTabChange = (_: SyntheticEvent, newValue: number) => {
    setActiveTab(newValue);
  };

  const activeCollectorRemediations = useMemo(() => {
    const activeCollector = tabs[activeTab];
    if (!activeCollector) return [];
    return detectionRemediations.filter(
      rem => rem.detection_remediation_collector === activeCollector.collector_type,
    );
  }, [tabs, activeTab, detectionRemediations]);

  useEffect(() => {
    setActiveDetectionRemediation(detectionRemediations.find((value) => {
      return value.detection_remediation_collector === tabs[activeTab]?.collector_type;
    }));
  }, [tabs, activeTab, detectionRemediations]);

  const updateSnapshot = useCallback((tabsData: Collector[], activeTabIndex: number, isLoading?: boolean) => {
    setSnapshot((prev) => {
      const map = new Map(prev || []);
      if (!tabsData || !tabsData[activeTabIndex]) return map;

      map.set(tabsData[activeTabIndex].collector_type, {
        ...map.get(tabsData[activeTabIndex].collector_type) || {},
        isLoading: isLoading,
      } as SnapshotEditionRemediationType);

      return map;
    });
  }, []);

  const updateSnapshotNewRemediation = useCallback((tabsData: Collector[], collectorType: string, AIRules: string, isLoading: boolean) => {
    setSnapshot((prev) => {
      const map = new Map(prev || []);
      if (!tabsData) return map;
      map.set(collectorType, {
        ...map.get(collectorType) || {},
        isLoading: isLoading,
        AIRules: AIRules,
      } as SnapshotEditionRemediationType);

      return map;
    });
  }, []);

  function addOrUpdateRemediation(newRemediation: DetectionRemediationOutput) {
    setDetectionRemediations((prev) => {
      const index = prev.findIndex(item => item.detection_remediation_collector === newRemediation.detection_remediation_collector);
      if (index === -1) {
        return [...prev, newRemediation];
      } else {
        const update = [...prev];
        update[index] = newRemediation;
        return update;
      }
    },
    );

    let i = 0;
    const text = newRemediation.detection_remediation_values;
    const interval = setInterval(() => {
      setDisplayedText(() => i === 0 ? (text[i]) : text.slice(0, i - 10) + (text[i]));
      i += 10;
      if (i >= text.length) {
        clearInterval(interval);
        setTyping(false);
      }
    }, 10);
  }

  async function onClickUseAriane() {
    updateSnapshot(tabs, activeTab, true);
    setTyping(true);
    const collectorType = tabs[activeTab].collector_type;
    return postDetectionRemediationAIRulesByInject(injectId, tabs[activeTab].collector_type).then((value) => {
      updateSnapshotNewRemediation(tabs, collectorType, value.data.detection_remediation_values, true);
      addOrUpdateRemediation(value.data);
    }).finally(() => {
      updateSnapshot(tabs, activeTab, false);
    });
  }

  return (
    <>
      <Typography variant="h5" gutterBottom>{t('Security platform')}</Typography>
      {loading && <Loader variant="inElement" />}
      {(hasPlatformSettingsCapabilities || injectId) ? (
        <>
          {tabs.length === 0
            ? (
                <Paper className={classes.paperContainer} variant="outlined">
                  <Typography variant="body2" color="textSecondary" sx={{ padding: 2 }}>
                    {t('No collector configured.')}
                  </Typography>
                </Paper>
              ) : (
                <>
                  <Tabs value={activeTab} onChange={handleActiveTabChange} aria-label="collector tabs">
                    {tabs.map((tab, index) => (
                      <Tab
                        key={tab.collector_type}
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
                  <div className={classes.headerRemediation}>
                    {isNotEmptyField(activeDetectionRemediation?.detection_remediation_values)
                      && <DetectionRemediationInfo author_rule={activeDetectionRemediation?.detection_remediation_author_rule} />}
                  </div>
                  <Paper className={classes.paperContainer} variant="outlined">
                    {activeCollectorRemediations.length === 0 ? (
                      <>
                        <div style={{
                          display: 'flex',
                          alignItems: 'center',
                        }}
                        >
                          {!(snapshot?.get(tabs[activeTab].collector_type)?.isLoading) && (
                            <Typography sx={{ padding: 2 }} variant="body2" color="textSecondary" gutterBottom>
                              {t('No detection rule available for this security platform yet.')}
                            </Typography>
                          )}
                          <DetectionRemediationUseAriane
                            key={tabs[activeTab].collector_type}
                            collectorType={tabs[activeTab].collector_type}
                            detectionRemediationContent={activeDetectionRemediation?.detection_remediation_values}
                            onSubmit={onClickUseAriane}
                          />
                        </div>
                      </>
                    ) : (
                      activeCollectorRemediations.map((rem) => {
                        const content = (snapshot?.get(tabs[activeTab].collector_type)?.AIRules) != null
                          ? (snapshot?.get(tabs[activeTab].collector_type)?.AIRules)
                          : rem.detection_remediation_values?.trim();

                        return (
                          <div key={'paper.' + rem.detection_remediation_id}>
                            {content ? (
                              <>
                                <Box sx={{ padding: 2 }} key={rem.detection_remediation_id}>
                                  <Typography
                                    sx={{ paddingBottom: 2 }}
                                    variant="body2"
                                    fontWeight="bold"
                                    gutterBottom
                                  >
                                    {`${t('Detection Rule')}: `}
                                  </Typography>
                                  {
                                    (() => {
                                      const collector = rem?.detection_remediation_collector;
                                      const entry = collector ? snapshot?.get?.(collector) : undefined;
                                      const aiRules = entry?.AIRules;
                                      let raw: string;

                                      if (typing) {
                                        raw = displayedText ?? '';
                                      } else if (aiRules != null) {
                                        raw = String(aiRules);
                                      } else {
                                        raw = rem?.detection_remediation_values ?? '';
                                      }

                                      const html = DOMPurify.sanitize(raw.replace(/\n/g, ''));

                                      return <div dangerouslySetInnerHTML={{ __html: html }} />;
                                    })()
                                  }
                                </Box>
                              </>
                            ) : (
                              <>

                                <div style={{
                                  display: 'flex',
                                  alignItems: 'center',
                                }}
                                >
                                  {!(snapshot?.get(tabs[activeTab].collector_type)?.isLoading) && (
                                    <Typography sx={{ padding: 2 }} variant="body2" color="textSecondary" gutterBottom>
                                      {t('No detection rule available for this security platform yet.')}
                                    </Typography>
                                  )}

                                  <DetectionRemediationUseAriane
                                    collectorType={tabs[activeTab].collector_type}
                                    detectionRemediationContent={activeDetectionRemediation?.detection_remediation_values}
                                    onSubmit={onClickUseAriane}
                                  />
                                </div>
                              </>
                            )}
                          </div>
                        );
                      })
                    )}
                  </Paper>
                </>
              )}
        </>
      ) : (<RestrictionAccess restrictedField="collectors" />)}
    </>
  );
};

export default AtomicTestingRemediations;
