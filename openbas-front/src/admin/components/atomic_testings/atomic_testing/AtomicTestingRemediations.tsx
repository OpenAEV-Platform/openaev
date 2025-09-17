import { Box, Paper, Tab, Tabs, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
// eslint-disable-next-line import/no-named-as-default
import DOMPurify from 'dompurify';
import { type SyntheticEvent, useContext, useEffect, useMemo, useState } from 'react';
import { useLocation, useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchCollectorsForAtomicTesting } from '../../../../actions/atomic_testings/atomic-testing-actions';
import { fetchCollectors } from '../../../../actions/Collector';
import type { CollectorHelper } from '../../../../actions/collectors/collector-helper';
import { fetchPayloadDetectionRemediationsByInject } from '../../../../actions/injects/inject-action';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { COLLECTOR_LIST } from '../../../../constants/Entities';
import { useHelper } from '../../../../store';
import {
  type Collector,
  type DetectionRemediationOutput,
  type InjectResultOverviewOutput
} from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider';
import RestrictionAccess from '../../../../utils/permissions/RestrictionAccess';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import DetectionRemediationUseAriane from "../../payloads/form/DetectionRemediationUseAriane";
import DetectionRemediationInfo from "../../payloads/form/DetectionRemediationInfo";

const useStyles = makeStyles()(theme => ({
  paperContainer: {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr',
    gap: theme.spacing(3),
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

  const { collectors } = useHelper((helper: CollectorHelper) => ({ collectors: helper.getCollectors() }));
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

  const[activeDetectionRemediation, setActiveDetectionRemediation] = useState<DetectionRemediationOutput>(null)

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
    setActiveDetectionRemediation(detectionRemediations.find(value =>
      value.detection_remediation_collector == tabs[activeTab]?.collector_type))
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
  let [newAIRules, setNewAiRules] = useState<Map<string,string>>(new Map());
  const onClickUseAriane = async (AIRules: string) => {
    console.log("onClickUseAriane")
    //todo update remediation content (i dont find where come from initial content remediation)
    //todo or reload content ? test: after rules generation quite Payload Update table and come back the rules are empty
    console.log(AIRules)
    handleSubmitText(AIRules)

    console.log(tabs[activeTab])
  };

  const handleSubmitText = (submittedText: string, collectorName:string) => {
    console.log("handle")

    let i = 0;
    //let currentDetection: DetectionRemediationForm = getValues('remediations.' + tabs[activeTab].collector_type);
    //const editor = editorRef.current[tabs[activeTab].collector_type];
    //ANIMATION typing + AUTO SCROLl
    //TODO stop autoscroll if user scroll
    const typechar = () => {
      if (i < submittedText.length) {
        const char = submittedText[i];
        setNewAiRules(prevNewAIRules =>{
          const newMap = new Map(prevNewAIRules ?prevNewAIRules : "");
          newMap.set(collectorName, (prevNewAIRules.get(collectorName) ?
            prevNewAIRules.get(collectorName): "")  + char)
          return newMap;
        })
        i++;
        setTimeout(typechar, 30);
      }
    };
    typechar();
    console.log(newAIRules)
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

                  <Paper className={classes.paperContainer} variant="outlined">
                    {activeCollectorRemediations.length === 0 ? (
                      <>
                        <div style={{display:'flex', flexDirection: 'column', alignItems: 'start', padding:16}}>
                          <Typography  variant="body2" color="textSecondary" gutterBottom>
                            {t('No detection rule available for this security platform yet.eee')}
                          </Typography>

                        <DetectionRemediationUseAriane
                          collectorType={tabs[activeTab].collector_type}
                          updatePayload={false}
                          remediationId={activeDetectionRemediation? activeDetectionRemediation.detection_remediation_id : ''}
                          injectId={injectId}
                          content={activeDetectionRemediation ? activeDetectionRemediation.detection_remediation_values:newAIRules[tabs[activeTab]?.collector_type]? newAIRules[tabs[activeTab]?.collector_type] :''}
                          t={t}
                          onResult={onClickUseAriane}>
                        </DetectionRemediationUseAriane>
                          <p>{newAIRules}</p>
                          <p>{tabs[activeTab]?.collector_type}</p>
                          <div dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(newAIRules[tabs[activeTab]?.collector_type]?.replace(/\n/g, '<br />')) }}>
                          </div>
                      </div>
                      </>
                    ) : (
                      activeCollectorRemediations.map((rem) => {
                        const content = rem.detection_remediation_values?.trim();

                        return (
                          <Box sx={{ padding: 2 }} key={rem.detection_remediation_id}>
                            {content ? (
                              <>
                                <div style={{display:'flex'}}>
                                <Typography
                                  sx={{ paddingBottom: 2 }}
                                  variant="body2"
                                  fontWeight="bold"
                                  gutterBottom
                                >
                                  {`${t('Detection Rule')}: `}
                                </Typography>

                                  <DetectionRemediationInfo content={rem.detection_remediation_values}
                                                            authorRules={rem.detection_remediation_author_rule}
                                                            collectorType={rem.detection_remediation_collector_type}
                                                            //todo import payloadUpdatedAt after fix date update
                                                            payloadUpdatedAt={rem.detection_remediation_updated_at}
                                                            detectionRemediationUpdateAt={rem.detection_remediation_updated_at}>
                                  </DetectionRemediationInfo>
                              </div>
                                <div
                                  dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(rem.detection_remediation_values.replace(/\n/g, '<br />')) }}
                                >
                                  {newAIRules}
                                </div>
                              </>
                            ) : (
                              <>
                              <div style={{display:'flex', flexDirection: 'column', alignItems: 'start'}}>
                                <Typography variant="body2" color="textSecondary" gutterBottom>
                                  {t('No detection rule available for this security platform yet.hhh')}
                                </Typography>
                                <DetectionRemediationUseAriane
                                  collectorType={tabs[activeTab].collector_type}
                                  updatePayload={false}
                                  idRemediation={activeDetectionRemediation? activeDetectionRemediation.detection_remediation_id : ''}
                                  content={activeDetectionRemediation ? activeDetectionRemediation.detection_remediation_values:''}
                                  t={t}
                                  onResult={onClickUseAriane}>
                                </DetectionRemediationUseAriane>
                              </div>
                              </>
                            )}
                          </Box>
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
