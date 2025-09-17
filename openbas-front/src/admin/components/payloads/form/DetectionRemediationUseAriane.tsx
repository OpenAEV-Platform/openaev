import { FunctionComponent, useEffect, useState } from "react";
import { Button, SvgIcon, Theme } from "@mui/material";
import {
  DetectionRemediationCreationInput,
  DetectionRemediationForm,
  PayloadUpdateInput,
  PlatformSettings
} from "../../../../utils/api-types";
import { Translate } from "../../../../components/i18n";
import { FiligranLoader, LogoXtmOneIcon } from "filigran-icon";
import EEChip from "../../common/entreprise_edition/EEChip";
import EETooltip from "../../common/entreprise_edition/EETooltip";
import useEnterpriseEdition from "../../../../utils/hooks/useEnterpriseEdition";
import useAI from "../../../../utils/hooks/useAI";
import { COLLECTOR_LIST_AI } from "../../../../constants/Entities";
import { FieldValue } from "react-hook-form";
import { useHelper } from "../../../../store";
import { LoggedHelper } from "../../../../actions/helper";
import { useTheme } from "@mui/material/styles";
import {
  postDetectionRemediationAIRulesCrowdstrike, postDetectionRemediationAIRulesCrowdstrikeByIdRemediation
} from "../../../../actions/detection-remediation/detectionRemediation";
import PayloadSubmitFormDialog from "./PayloadSubmitFormDialog";
import { CircularProgress } from "@mui/material";
export interface Props {
  collectorType?:string,
  updatePayload?:boolean,
  remediationId?:string,
  injectId?:string
  content?:string,
  t:Translate,
  submitFormByRemediationTab?:()=>Promise<boolean>,
  onUpdate
  payloadFormFields?:FieldValue<any>,
  onResult: (AIRules: string) => void
}


const DetectionRemediationUseAriane = ({
                                         collectorType,
                                         updatePayload,
                                         remediationId,
                                         injectId,
                                         content,
                                         t,
                                         submitFormByRemediationTab,
                                         onUpdate,
                                         payloadFormFields,
                                         onResult
                                       }: Props) => {
  const theme = useTheme();
  const {
    isValidated: isEnterpriseEdition,
    openDialog: openEnterpriseEditionDialog,
    setEEFeatureDetectedInfo,
  } = useEnterpriseEdition();
  const { settings }: { settings: PlatformSettings } = useHelper((helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }));

  const { enabled, configured } = useAI();
  const isAvailable = isEnterpriseEdition && enabled && configured;
  const [isCollectorUseArianeAvailable, setCollectorUseArianeAvailable] = useState(false);
  const [loading, setLoading] = useState(false);
  const hasFiligranLoader = theme && !(settings?.platform_license?.license_is_validated && settings?.platform_whitemark);

  useEffect(() => {
    //todo await tabs completed? (bug button not available)
      const isAvailable = collectorType ? COLLECTOR_LIST_AI.includes(collectorType) : false;
      setCollectorUseArianeAvailable(isAvailable);
  }, [collectorType]);

  const onUseArianeClick = () => {
    if (!isEnterpriseEdition) {
      setEEFeatureDetectedInfo(t('Ariane AI'));
      openEnterpriseEditionDialog();
    } else if(updatePayload) {
      setOpenConfirmSavedPayloadDialog(true);
    }else {
      //todo if no id remediation?
      console.log( " remediationId "+remediationId)
      console.log( " idInject "+injectId)
      console.log("collectorType" + collectorType)
      onUseArianeById()
    }
  };
  //Todo create service ?
  const onSubmitCallAIDetectionRemediation = async (payload: any): Promise<string> => {
    try {
      const value = await postDetectionRemediationAIRulesCrowdstrike(payload);
      return value.data.rules;
    } finally {
      setLoading(false);
    }
  };

//DIALOG
  const [openConfirmSavedPayloadDialog, setOpenConfirmSavedPayloadDialog] = useState(false);
  const onCloseConfirmSavedPayloadDialog = () => {
    setOpenConfirmSavedPayloadDialog(false);
  };
  const onSubmitConfirmSavedPayloadDialog = async (isValid:boolean) => {
    setLoading(true);
    setOpenConfirmSavedPayloadDialog(false);
    if(isValid) {
      let payloadInput: PayloadUpdateInput = getPayloadInputFromForm(payloadFormFields);
      //todo erased content should nor provoque error due to content into rules editor ...
      onSubmitCallAIDetectionRemediation(payloadInput).then(value => {
        setLoading(false);
        onResult(value)
      })
    }
  };

  function getPayloadInputFromForm(payload:FieldValue<any>):PayloadUpdateInput{
    return {
      ...payload,
      payload_execution_arch:payload.payload_execution_arch,
      payload_expectations: payload.payload_expectations,
      payload_name: payload.payload_name,
      payload_platforms: payload.payload_platforms,
      payload_tags: payload.payload_tags,
      payload_attack_patterns:payload.payload_attack_patterns,
      executable_file:payload.executable_file,
      payload_cleanup_executor: handleCleanupExecutorValue(payload.payload_cleanup_executor, payload.payload_cleanup_command),
      payload_cleanup_command: handleCleanupCommandValue(payload.payload_cleanup_command),
      payload_detection_remediations: Object.entries(payload.remediations ?? {})
        .filter(([, remediation]) => remediation != null && 'content' in (remediation as any))
        .map(([collector, remediation]) => ({
          detection_remediation_collector: collector,
          detection_remediation_values: (remediation as DetectionRemediationForm).content,
          detection_remediation_id: (remediation as DetectionRemediationForm).remediationId,
        }))
    }
  }

  function handleCleanupCommandValue(payload_cleanup_command: string) {
    return payload_cleanup_command === '' ? null : payload_cleanup_command;
  }

  function handleCleanupExecutorValue(payload_cleanup_executor: string, payload_cleanup_command: string) {
    if (payload_cleanup_executor !== '' && handleCleanupCommandValue(payload_cleanup_command) !== null) {
      return payload_cleanup_executor;
    }
    return null;
  }

  //By id remediation
  const onUseArianeById = () => {
    if (!isEnterpriseEdition) {
      setEEFeatureDetectedInfo(t('Ariane AI'));
      openEnterpriseEditionDialog();
    } else if(remediationId || injectId) {
      postRules(remediationId, injectId)
    }
  };

  const postRules = (remediationId:string, injectId:injectId)=>{
    setLoading(true);
    onSubmitCallAIDetectionRemediationId(remediationId, injectId).then(value => {
        onResult(value);
    })
  }
  const onSubmitCallAIDetectionRemediationId = async (remediationId: string, injectId:string): Promise<string> => {
    const detectionRemediationCreationInput:DetectionRemediationCreationInput = {
      remediationId:remediationId,
      injectId:injectId
    }
    try {
      const value = await postDetectionRemediationAIRulesCrowdstrikeByIdRemediation(detectionRemediationCreationInput);
      return value.data.rules;
    } finally {
      setLoading(false);
    }
  };
  return(
    <>
      <div style={{
        display: loading ? "flex" : "none" ,
        marginRight: "10px",
        justifyContent: "flex-end"
      }}>
        {!hasFiligranLoader ? (
          <FiligranLoader height={24} color={theme?.palette?.grey.A100}></FiligranLoader>
        ) : (
          <CircularProgress
            size={24}
            thickness={1}
          />
        )}
      </div>
      <EETooltip
        style={{ whiteSpace: 'pre-line', textAlign:'center' }}
        forAi
        title={`${t('Ask AI')}${!isAvailable ? ' (EE)':''}
                      ${(!isCollectorUseArianeAvailable) ? 'Not available for current collector' :
          content != '' ? "Only available for empty content": ''}`
        }
      >
                      <span style={{alignContent:'center'}}>
                        <Button
                          variant="outlined"
                          sx={{
                            marginLeft: 'auto',
                            color: isEnterpriseEdition ? 'ai.main' : 'action.disabled',
                            borderColor: isEnterpriseEdition ? 'ai.main' : 'action.disabledBackground',
                          }}
                          size="small"
                          onClick={onUseArianeClick}
                          startIcon={<SvgIcon component={LogoXtmOneIcon} fontSize="small" inheritViewBox/>}
                          style={{"display": loading ? "none" : "flex"}}
                          endIcon={isEnterpriseEdition ? <></> : <span><EEChip/></span>}
                          disabled={!isCollectorUseArianeAvailable || !isAvailable ||
                            content != ''}
                        >
                          {t('Use Ariane ')}
                        </Button>
                      </span>
      </EETooltip>

      <PayloadSubmitFormDialog
      open={openConfirmSavedPayloadDialog}
      onClose={onCloseConfirmSavedPayloadDialog}
      onSubmit={onSubmitConfirmSavedPayloadDialog}
      theme={theme}
      submitFormByRemediationTab={submitFormByRemediationTab}
      settings={settings}
      t={t}
    >

    </PayloadSubmitFormDialog>
    </>
    )
}
export default DetectionRemediationUseAriane;