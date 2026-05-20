import { Button, IconButton, SvgIcon } from '@mui/material';
import { LogoXtmOneIcon } from 'filigran-icon';
import { useEffect, useState } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type AgentOption, fetchAgentsForIntent } from '../../../../utils/ai/agentApi';
import AgentSelector from '../../../../utils/ai/AgentSelector';
import useAI from '../../../../utils/hooks/useAI';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
import { isNotEmptyField } from '../../../../utils/utils';
import EEChip from '../../common/entreprise_edition/EEChip';
import EETooltip from '../../common/entreprise_edition/EETooltip';
import Loader from '../../payloads/Loader';
import useIsEligibleArianeCollector from '../hook/useIsEligibleArianeCollector';
import useIsEligibleArianePayloadType from '../hook/useIsEligibleArianePayloadType';
import { useSnapshotRemediation } from '../utils/useSnapshotRemediation';

export interface Props {
  collectorType: string;
  payloadType?: string | undefined;
  detectionRemediationContent?: string;
  onSubmit: (agentSlug?: string) => Promise<void>;
  isValidForm?: boolean;
}

const DetectionRemediationUseAriane = ({
  collectorType,
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
  const { enabled, configured, xtmOneConfigured } = useAI();
  const isAvailable = isEnterpriseEdition && enabled && (configured || xtmOneConfigured);

  const [loading, setLoading] = useState(false);
  const [agentOptions, setAgentOptions] = useState<AgentOption[]>([]);
  const [selectedAgent, setSelectedAgent] = useState<AgentOption | null>(null);
  const [loadingAgents, setLoadingAgents] = useState(false);
  const isEligibleArianeCollector = useIsEligibleArianeCollector(collectorType);
  const isEligibleArianePayload = useIsEligibleArianePayloadType(payloadType);
  const hasContent = isNotEmptyField(detectionRemediationContent);

  useEffect(() => {
    if (!xtmOneConfigured) return;
    setLoadingAgents(true);
    fetchAgentsForIntent('detection.generate')
      .then((agents) => {
        setAgentOptions(agents);
        if (agents.length > 0) setSelectedAgent(agents[0]);
      })
      .finally(() => setLoadingAgents(false));
  }, [xtmOneConfigured]);

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
  if (!isEligibleArianeCollector) {
    btnLabel = btnLabel + t(' is not available for current collector');
  } else if (!isEligibleArianePayload) {
    btnLabel = btnLabel + t(' is not available for current payload type');
  } else if (!isValidForm) {
    btnLabel = btnLabel + t(' is locked until required fields are filled.');
  } else if (hasContent) {
    btnLabel = btnLabel + t(' is only available for empty content');
  }

  const disabled = !isEligibleArianeCollector || !isAvailable || hasContent || !isValidForm || !isEligibleArianePayload;

  const isLoading = loading || snapshot?.get(collectorType)?.isLoading;

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
    if (xtmOneConfigured) {
      return (
        <IconButton
          onClick={handleClick}
          aria-label={t('Use XTM One')}
          disabled={disabled || loading || !selectedAgent}
          sx={{
            width: 36,
            height: 36,
            border: '1px solid',
            borderRadius: 1,
            color: actionColor,
            borderColor: actionBorderColor,
          }}
        >
          <SvgIcon component={LogoXtmOneIcon} fontSize="small" inheritViewBox />
        </IconButton>
      );
    }
    return (
      <Button
        type="button"
        variant="outlined"
        size="small"
        onClick={handleClick}
        aria-label={t('Use Ariane')}
        startIcon={<SvgIcon component={LogoXtmOneIcon} fontSize="small" inheritViewBox />}
        endIcon={isEnterpriseEdition ? <></> : <span><EEChip /></span>}
        disabled={disabled || loading}
        sx={{
          height: 36,
          color: actionColor,
          borderColor: actionBorderColor,
        }}
      >
        {t('Use Ariane')}
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
    </div>
  );
};
export default DetectionRemediationUseAriane;
