import { Box, Tab, Tabs, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type SyntheticEvent, useContext, useEffect, useRef, useState } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

import { fetchCollectors } from '../../../../actions/Collector';
import type { CollectorHelper } from '../../../../actions/collectors/collector-helper';
import { fetchCollectorsForPayload } from '../../../../actions/payloads/payload-actions';
import CKEditor from '../../../../components/CKEditor';
import { useFormatter } from '../../../../components/i18n';
import { COLLECTOR_LIST } from '../../../../constants/Entities';
import { useHelper } from '../../../../store';

import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider';
import RestrictionAccess from '../../../../utils/permissions/RestrictionAccess';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { width } from "@mui/system";
import { Collector, DetectionRemediation, DetectionRemediationForm } from "../../../../utils/api-types";
import DetectionRemediationInfo from "./DetectionRemediationInfo";
import DetectionRemediationUseAriane from "./DetectionRemediationUseAriane";

interface RemediationFormTabProps { payloadId?: string, payload?: string, initialValueRemediation?:any, submitFormByRemediationTab:()=>Promise<boolean>, onUpdate }

const RemediationFormTab = ({ payloadId, payload, initialValueRemediation, submitFormByRemediationTab, onUpdate }: RemediationFormTabProps) => {
  const [tabs, setTabs] = useState<Collector[]>([]);
  const [activeTab, setActiveTab] = useState<number>(0);
  const { control, setValue, getValues } = useFormContext();
  const { t } = useFormatter();
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);
  const [loading, setLoading] = useState(false);
  //todo use AUTHOR_AI
  const [authorRule, setAuthorRule] = useState<DetectionRemediation["author_rule"]>("HUMAN");


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
      });
    } else if (payloadId) {
      setLoading(true);
      dispatch(fetchCollectorsForPayload(payloadId)).finally(() => {
        setLoading(false);
      });
    }
  });

  useEffect(()=>{
      setAuthorRule(initialValueRemediation[tabs[activeTab]?.collector_type]?.authorRule)
  }, [initialValueRemediation, tabs, activeTab])

  useEffect(() => {
    if (collectors.length > 0) {
      const filteredCollectors = collectors.filter((collector: Collector) =>
        COLLECTOR_LIST.includes(collector.collector_type),
      ).sort((a: Collector, b: Collector) => a.collector_name.localeCompare(b.collector_name));
      setTabs(filteredCollectors);
    }
  }, [collectors]);

  const onClickUseAriane = async (AIRules: string) => {
    //todo update remediation content (i dont find where come from initial content remediation)
    //todo or reload content ? test: after rules generation quite Payload Update table and come back the rules are empty
    handleSubmitText(AIRules);
  };

  const editorRef = useRef<any>([]);

  const handleSubmitText = (submittedText: string) => {
    let i = 0;
    let currentDetection: DetectionRemediationForm = getValues('remediations.' + tabs[activeTab].collector_type);
    const editor = editorRef.current[tabs[activeTab].collector_type];
    //ANIMATION typing + AUTO SCROLl
    //TODO stop autoscroll if user scroll
    const typechar = () => {
      if (i < submittedText.length) {
        const char = submittedText[i];
        currentDetection.content = currentDetection.content + char;
        setValue('remediations.' + tabs[activeTab].collector_type, currentDetection)
        i++;
        const editingView = editor.editing.view;
        const domRoot = editingView.getDomRoot();
        if (domRoot) {
          domRoot.scrollTop = domRoot.scrollHeight;
        }
        setTimeout(typechar, 3);
      }else {
        payload.payload_detection_remediations.forEach((remediation) => {
          if(remediation.detection_remediation_collector_type == tabs[activeTab].collector_type) {
            remediation.detection_remediation_values = submittedText;
            remediation.author_rule = "AI";
          }
        })
        onUpdate(payload);
        setAuthorRule("AI")
      }

    };
    typechar();
  }

  return (
    <>
      <Typography variant="h5" gutterBottom>{t('Security platform')}</Typography>
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
                  <div style={{display: 'flex', justifyContent: 'space-between', alignItem: 'center'}}>
                    <DetectionRemediationInfo
                    content={getValues('remediations.' + tabs[activeTab].collector_type).content}
                    payloadUpdatedAt={payload.payload_updated_at}
                    //detectionRemediationUpdateAt={initialValueRemediation?.[tabs[activeTab].collector_type].updateAt}
                    authorRules={authorRule}
                    >
                    </DetectionRemediationInfo>

                    <DetectionRemediationUseAriane
                      collectorType={tabs[activeTab].collector_type}
                      updatePayload={true}
                      content={getValues('remediations.' + tabs[activeTab].collector_type).content}
                      t={t}
                      submitFormByRemediationTab={submitFormByRemediationTab}
                      onUpdate={onUpdate}
                      payloadFormFields={control._formValues}
                      onResult={onClickUseAriane}>
                    </DetectionRemediationUseAriane>

                  </div>
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
                        defaultValue={{content: ''}}
                        render={({field: {onChange, value}}) => (

                          <CKEditor
                            onReady={editor => {
                              editorRef.current[tab.collector_type] = editor;
                            }}
                            id={'payload-remediation-editor' + tab.collector_type}
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
