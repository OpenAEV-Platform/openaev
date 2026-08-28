import { Button, SvgIcon } from '@mui/material';
import { LogoXtmOneIcon } from 'filigran-icon';
import { useEffect, useState } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type AgentOption, fetchAgentsForIntent } from '../../../../utils/ai/agentApi';
import AgentSelector from '../../../../utils/ai/AgentSelector';
import useAI from '../../../../utils/hooks/useAI';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
import { isNotEmptyField } from '../../../../utils/utils';
import FiligranAiCguDialog from '../../ariane/FiligranAiCguDialog';
import EEChip from '../../common/entreprise_edition/EEChip';
import EETooltip from '../../common/entreprise_edition/EETooltip';
import Loader from '../../payloads/Loader';
import useIsEligibleArianePayloadType from '../hook/useIsEligibleArianePayloadType';
import useIsEligibleArianeSecurityPlatform from '../hook/useIsEligibleArianeSecurityPlatform';
import { useSnapshotRemediation } from '../utils/useSnapshotRemediation';

export interface Props {
  securityPlatformId: string;
  securityPlatformName?: string;
  payloadType?: string | undefined;
  detectionRemediationContent?: string;
  onSubmit: (agentSlug?: string) => Promise<void>;
  isValidForm?: boolean;
}

const DetectionRemediationUseAriane = ({
  securityPlatformId,
  securityPlatformName,
  payloadType,
  detectionRemediationContent,
  onSubmit,
  isValidForm = true,
}: Props) => {
  const { snapshot } = useSnapshotRemediation();
  const { t } = useFormatter();
  const {
    isValidated: isEnterpriseEdition,
    openDialog: openEnterpriseEditionDialog,
    setEEFeatureDetectedInfo,
  } = useEnterpriseEdition();
  const { enabled, isCguPending, configured, xtmOneConfigured } = useAI();

  const [openValidateTermsOfUse, setOpenValidateTermsOfUse] = useState(false);
  const [loading, setLoading] = useState(false);
  const [agentOptions, setAgentOptions] = useState<AgentOption[]>([]);
  const [selectedAgent, setSelectedAgent] = useState<AgentOption | null>(null);
  const [loadingAgents, setLoadingAgents] = useState(false);
  const isEligibleSecurityPlatform = useIsEligibleArianeSecurityPlatform(securityPlatformName);
  const isEligibleArianePayload = useIsEligibleArianePayloadType(payloadType);
  const hasContent = isNotEmptyField(detectionRemediationContent);

  useEffect(() => {
    if (!xtmOneConfigured) return;
    setLoadingAgents(true);
    fetchAgentsForIntent('aev.detection_rules_generator')
      .then((agents) => {
        setAgentOptions(agents);
        if (agents.length > 0) setSelectedAgent(agents[0]);
      })
      .finally(() => setLoadingAgents(false));
  }, [xtmOneConfigured]);

  // Hide entirely when AI is explicitly disabled
  if (enabled === false) {
    return null;
  }

  const isAvailable = isEnterpriseEdition && enabled && (configured || xtmOneConfigured);

  const runOnSubmit = (agentSlug?: string) => {
    setLoading(true);
    onSubmit(agentSlug).finally(() => setLoading(false));
  };

  const handleClick = async () => {
    if (!isEnterpriseEdition) {
      setEEFeatureDetectedInfo(t('XTM One AI'));
      openEnterpriseEditionDialog();
      return;
    }
    if (isCguPending) {
      setOpenValidateTermsOfUse(true);
      return;
    }
    if (xtmOneConfigured) {
      runOnSubmit(selectedAgent?.slug);
      return;
    }
    runOnSubmit();
  };

  let btnLabel = t('Use XTM One');
  if (!isAvailable) {
    btnLabel = btnLabel + ' (EE)';
  }
  if (!isEligibleSecurityPlatform) {
    btnLabel = btnLabel + t(' is not available for this security platform');
  } else if (!isEligibleArianePayload) {
    btnLabel = btnLabel + t(' is not available for current payload type');
  } else if (!isValidForm) {
    btnLabel = btnLabel + t(' is locked until required fields are filled.');
  } else if (hasContent) {
    btnLabel = btnLabel + t(' is only available for empty content');
  }

  const disabled = !isEligibleSecurityPlatform || (!isAvailable && !isCguPending) || hasContent || !isValidForm || !isEligibleArianePayload;

  const isLoading = loading || snapshot?.get(securityPlatformId)?.isLoading;

  const actionColor = isEnterpriseEdition ? 'ai.main' : 'action.disabled';
  const actionBorderColor = isEnterpriseEdition ? 'ai.main' : 'action.disabledBackground';

  const renderAction = () => {
    if (isLoading) {
      return (
        <div style={{
          display: 'flex',
          alignItems: 'center',
          marginRight: '10px',
        }}
        >
          <Loader />
        </div>
      );
    }
    return (
      <Button
        type="button"
        variant="outlined"
        size="small"
        onClick={handleClick}
        aria-label={xtmOneConfigured ? t('Generate with AI') : t('Use Ariane')}
        startIcon={<SvgIcon component={LogoXtmOneIcon} fontSize="small" inheritViewBox />}
        endIcon={isEnterpriseEdition ? undefined : <span><EEChip /></span>}
        disabled={disabled || loading || (!!xtmOneConfigured && !selectedAgent)}
        sx={{
          height: 36,
          whiteSpace: 'nowrap',
          color: actionColor,
          borderColor: actionBorderColor,
        }}
      >
        {xtmOneConfigured ? t('Generate with AI') : t('Use Ariane')}
      </Button>
    );
  };

  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'flex-end',
      gap: 8,
      marginLeft: 'auto',
    }}
    >
      {xtmOneConfigured && (
        <div style={{
          display: 'flex',
          alignItems: 'center',
        }}
        >
          <AgentSelector
            options={agentOptions}
            value={selectedAgent}
            onChange={setSelectedAgent}
            loading={loadingAgents}
            disabled={isLoading}
          />
        </div>
      )}
      <EETooltip forAi title={btnLabel}>
        <span style={{
          display: 'flex',
          alignItems: 'center',
        }}
        >
          {renderAction()}
        </span>
      </EETooltip>
      {openValidateTermsOfUse && (
        <FiligranAiCguDialog
          open={openValidateTermsOfUse}
          onClose={() => setOpenValidateTermsOfUse(false)}
        />
      )}
    </div>
  );
};
export default DetectionRemediationUseAriane;
