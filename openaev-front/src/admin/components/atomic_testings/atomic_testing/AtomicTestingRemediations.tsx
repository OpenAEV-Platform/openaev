import { PolicyOutlined, ShieldOutlined } from '@mui/icons-material';
import { Box, Paper, Tab, Tabs, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
// eslint-disable-next-line import/no-named-as-default
import DOMPurify from 'dompurify';
import { type SyntheticEvent, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useLocation, useParams } from 'react-router';

import { fetchCollectorsForAtomicTesting } from '../../../../actions/atomic_testings/atomic-testing-actions';
import { fetchCollectors } from '../../../../actions/Collector';
import type { CollectorHelper } from '../../../../actions/collectors/collector-helper';
import { postDetectionRemediationAIRulesByInject } from '../../../../actions/detection-remediation/detectionremediation-action';
import { fetchPayloadDetectionRemediationsByInject } from '../../../../actions/injects/inject-action';
import { SECTION_LABEL_SX } from '../../../../components/common/detail/detailStyles';
import Empty from '../../../../components/Empty';
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
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import RestrictionAccess from '../../../../utils/permissions/RestrictionAccess';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { buildTenantApiPath } from '../../../../utils/url-helper';
import DetectionRemediationInfo from '../../threat_arsenal/form/DetectionRemediationInfo';
import DetectionRemediationUseAriane from '../../threat_arsenal/form/DetectionRemediationUseAriane';
import { type SnapshotEditionRemediationType } from '../../threat_arsenal/utils/SnapshotRemediationContext';
import { useSnapshotRemediation } from '../../threat_arsenal/utils/useSnapshotRemediation';

const AtomicTestingRemediations = () => {
  const { injectId } = useParams() as { injectId: InjectResultOverviewOutput['inject_id'] };
  const dispatch = useAppDispatch();
  const { t } = useFormatter();
  const theme = useTheme();
  const location = useLocation();
  const [tabs, setTabs] = useState<Collector[]>([]);
  const [activeTab, setActiveTab] = useState<number>(0);
  const [detectionRemediations, setDetectionRemediations] = useState<DetectionRemediationOutput[]>([]);
  const [hasFetchedRemediations, setHasFetchedRemediations] = useState(false);
  const ability = useContext(AbilityContext);

  const isRemediationTab = location.pathname.includes('/remediations');

  const hasSecurityPlatformsAccess = ability.can(ACTIONS.ACCESS, SUBJECTS.SECURITY_PLATFORMS);
  const [loading, setLoading] = useState(false);

  const { collectors } = useHelper((helper: CollectorHelper) => ({ collectors: helper.getExistingCollectors() }));

  const { snapshot, setSnapshot } = useSnapshotRemediation();
  const [activeDetectionRemediation, setActiveDetectionRemediation] = useState<DetectionRemediationOutput>();

  const [displayedText, setDisplayedText] = useState<string>('');
  const [typing, setTyping] = useState<boolean>(!!snapshot?.get(tabs[activeTab]?.collector_type)?.isLoading);

  useDataLoader(() => {
    if (hasSecurityPlatformsAccess) {
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

  async function onClickUseAriane(agentSlug?: string) {
    updateSnapshot(tabs, activeTab, true);
    setTyping(true);
    const collectorType = tabs[activeTab].collector_type;
    return postDetectionRemediationAIRulesByInject(
      injectId,
      tabs[activeTab].collector_type,
      agentSlug,
    ).then((value) => {
      updateSnapshotNewRemediation(tabs, collectorType, value.data.detection_remediation_values, true);
      addOrUpdateRemediation(value.data);
    }).finally(() => {
      updateSnapshot(tabs, activeTab, false);
    });
  }

  const activeCollector = tabs[activeTab];

  // Resolves the rule text to display for a remediation, honouring the live
  // typing animation and any AI snapshot override, falling back to the stored value.
  const resolveRuleHtml = (rem: DetectionRemediationOutput) => {
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
    return DOMPurify.sanitize(raw.replace(/\n/g, ''));
  };

  const renderEmptyRule = () => (
    <Paper
      variant="outlined"
      sx={{
        borderRadius: 1,
        padding: 4,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 1.5,
        textAlign: 'center',
        flex: 1,
      }}
    >
      <ShieldOutlined sx={{
        fontSize: 44,
        color: 'text.disabled',
      }}
      />
      <Typography variant="body2" color="text.secondary">
        {t('No detection rule available for this security platform yet.')}
      </Typography>
      {activeCollector && (
        <DetectionRemediationUseAriane
          key={activeCollector.collector_type}
          collectorType={activeCollector.collector_type}
          detectionRemediationContent={activeDetectionRemediation?.detection_remediation_values}
          onSubmit={onClickUseAriane}
        />
      )}
    </Paper>
  );

  const renderRuleBody = () => {
    if (activeCollectorRemediations.length === 0) {
      return renderEmptyRule();
    }
    return activeCollectorRemediations.map((rem) => {
      const content = (snapshot?.get(activeCollector.collector_type)?.AIRules) != null
        ? (snapshot?.get(activeCollector.collector_type)?.AIRules)
        : rem.detection_remediation_values?.trim();
      if (!content) {
        return <Box key={'empty.' + rem.detection_remediation_id} sx={{ display: 'flex' }}>{renderEmptyRule()}</Box>;
      }
      return (
        <Paper
          key={'rule.' + rem.detection_remediation_id}
          variant="outlined"
          sx={{
            borderRadius: 1,
            padding: 2,
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
            gap: 1,
          }}
        >
          <Typography sx={SECTION_LABEL_SX}>{t('Detection Rule')}</Typography>
          <Box
            sx={{
              'fontFamily': '"IBM Plex Mono", "Roboto Mono", monospace',
              'fontSize': 13,
              'lineHeight': 1.6,
              'color': 'text.primary',
              'backgroundColor': alpha(theme.palette.text.primary, 0.03),
              'borderRadius': 1,
              'padding': 1.5,
              'overflowX': 'auto',
              'whiteSpace': 'pre-wrap',
              'wordBreak': 'break-word',
              '& p': { margin: 0 },
            }}
            dangerouslySetInnerHTML={{ __html: resolveRuleHtml(rem) }}
          />
        </Paper>
      );
    });
  };

  if (!(hasSecurityPlatformsAccess || injectId)) {
    return <RestrictionAccess restrictedField="collectors" />;
  }

  if (loading) {
    return <Loader variant="inElement" />;
  }

  if (tabs.length === 0) {
    return (
      <Paper
        variant="outlined"
        sx={{
          borderRadius: 1,
          padding: 3,
        }}
      >
        <Empty message={t('No collector configured.')} />
      </Paper>
    );
  }

  return (
    <Box sx={{
      display: 'flex',
      gap: 2,
      alignItems: 'stretch',
      minHeight: 340,
    }}
    >
      <Tabs
        orientation="vertical"
        variant="scrollable"
        value={activeTab}
        onChange={handleActiveTabChange}
        aria-label={t('Security platforms')}
        sx={{
          'minWidth': 220,
          'flexShrink': 0,
          'borderRight': `1px solid ${theme.palette.divider}`,
          '& .MuiTabs-indicator': {
            left: 0,
            width: 2,
          },
          '& .MuiTab-root': {
            // The theme forces `display: inline-block` + lowercase on MuiTab for
            // its `::first-letter` trick; restore the flex row so the platform
            // logo and name align, and keep the collector name capitalised.
            display: 'flex',
            flexDirection: 'row',
            textTransform: 'none',
            alignItems: 'center',
            justifyContent: 'flex-start',
            textAlign: 'left',
            minHeight: 48,
            gap: 1,
            paddingX: 2,
          },
        }}
      >
        {tabs.map((tab, index) => (
          <Tab
            key={tab.collector_type}
            value={index}
            iconPosition="start"
            icon={(
              <img
                src={buildTenantApiPath(`/api/collectors/${tab.collector_type}/image`)}
                alt={tab.collector_type}
                style={{
                  width: 20,
                  height: 20,
                  borderRadius: 4,
                }}
              />
            )}
            label={<span style={{ textTransform: 'capitalize' }}>{tab.collector_name}</span>}
          />
        ))}
      </Tabs>

      <Box sx={{
        flex: 1,
        minWidth: 0,
        display: 'flex',
        flexDirection: 'column',
        gap: 1.5,
      }}
      >
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          minHeight: 34,
        }}
        >
          <PolicyOutlined fontSize="small" sx={{ color: 'text.secondary' }} />
          <Typography sx={{
            fontSize: 15,
            fontWeight: 600,
            textTransform: 'capitalize',
          }}
          >
            {activeCollector?.collector_name}
          </Typography>
          {activeDetectionRemediation?.detection_remediation_values && (
            <DetectionRemediationInfo author_rule={activeDetectionRemediation?.detection_remediation_author_rule} />
          )}
        </Box>
        {renderRuleBody()}
      </Box>
    </Box>
  );
};

export default AtomicTestingRemediations;
