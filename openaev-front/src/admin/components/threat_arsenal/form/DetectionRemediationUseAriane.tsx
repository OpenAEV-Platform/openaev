import { Button, Dialog, DialogActions, DialogContent, DialogTitle, SvgIcon, Typography } from '@mui/material';
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
  // Fetch data
  const {
    isValidated: isEnterpriseEdition,
    openDialog: openEnterpriseEditionDialog,
    setEEFeatureDetectedInfo,
  } = useEnterpriseEdition();
  const { enabled, configured, xtmOneConfigured } = useAI();
  const isAvailable = isEnterpriseEdition && enabled && (configured || xtmOneConfigured);

  const [loading, setLoading] = useState(false);
  const [agentDialogOpen, setAgentDialogOpen] = useState(false);
  const [agentOptions, setAgentOptions] = useState<AgentOption[]>([]);
  const [selectedAgent, setSelectedAgent] = useState<AgentOption | null>(null);
  const [loadingAgents, setLoadingAgents] = useState(false);
  const isEligibleArianeCollector = useIsEligibleArianeCollector(collectorType);
  const isEligibleArianePayload = useIsEligibleArianePayloadType(payloadType);
  const hasContent = isNotEmptyField(detectionRemediationContent);

  useEffect(() => {
    if (!agentDialogOpen) return;
    setLoadingAgents(true);
    setSelectedAgent(null);
    setAgentOptions([]);
    fetchAgentsForIntent('detection.generate')
      .then((agents) => {
        setAgentOptions(agents);
        if (agents.length > 0) setSelectedAgent(agents[0]);
      })
      .finally(() => setLoadingAgents(false));
  }, [agentDialogOpen]);

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
      setAgentDialogOpen(true);
      return;
    }
    runOnSubmit();
  };

  const handleAgentConfirm = () => {
    if (!selectedAgent) return;
    setAgentDialogOpen(false);
    runOnSubmit(selectedAgent.slug);
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

  return (
    <>
      <EETooltip forAi title={btnLabel}>
        <span>
          {(loading || snapshot?.get(collectorType)?.isLoading) ? (
            <div style={{
              display: 'flex',
              alignItems: 'center',
              marginRight: '10px',
            }}
            >
              <Typography
                variant="body2"
                color="textSecondary"
                sx={{ padding: 2 }}
                gutterBottom
              >
                {t('AI in progress')}
              </Typography>
              <Loader />
            </div>
          ) : (
            <Button
              type="button"
              variant="outlined"
              sx={{
                marginLeft: 'auto',
                color: isEnterpriseEdition ? 'ai.main' : 'action.disabled',
                borderColor: isEnterpriseEdition ? 'ai.main' : 'action.disabledBackground',
              }}
              size="small"
              onClick={handleClick}
              startIcon={<SvgIcon component={LogoXtmOneIcon} fontSize="small" inheritViewBox />}
              endIcon={isEnterpriseEdition ? <></> : <span><EEChip /></span>}
              disabled={disabled || loading}
            >
              {t('Use XTM One ')}
            </Button>
          )}
        </span>
      </EETooltip>
      <Dialog
        open={agentDialogOpen}
        onClose={() => setAgentDialogOpen(false)}
        fullWidth
        maxWidth="xs"
        PaperProps={{ elevation: 1 }}
      >
        <DialogTitle>
          <div style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            width: '100%',
            gap: 16,
          }}
          >
            <span>{t('Use XTM One')}</span>
            <AgentSelector
              options={agentOptions}
              value={selectedAgent}
              onChange={setSelectedAgent}
              loading={loadingAgents}
            />
          </div>
        </DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="textSecondary">
            {t('Select the AI agent that will generate detection rules for this collector.')}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAgentDialogOpen(false)}>
            {t('Cancel')}
          </Button>
          <Button
            color="secondary"
            variant="contained"
            onClick={handleAgentConfirm}
            disabled={!selectedAgent || loadingAgents}
          >
            {t('Generate')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};
export default DetectionRemediationUseAriane;
