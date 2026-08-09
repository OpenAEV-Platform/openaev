import { LoadingButton } from '@mui/lab';
import { Alert, Box, Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, SvgIcon, TextField } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { LogoXtmOneIcon } from 'filigran-icon';
import { type FunctionComponent, useEffect, useState } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type AgentOption, fetchAgentsForIntent } from '../../../../utils/ai/agentApi';
import AgentSelector from '../../../../utils/ai/AgentSelector';
import useAgentStream from '../../../../utils/ai/useAgentStream';
import useAI from '../../../../utils/hooks/useAI';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
import { isNotEmptyField } from '../../../../utils/utils';
import FiligranAiCguDialog from '../../ariane/FiligranAiCguDialog';
import EEChip from '../../common/entreprise_edition/EEChip';
import EETooltip from '../../common/entreprise_edition/EETooltip';

export interface PhishingAiGenerateButtonProps {
  /** XTM One catalog intent used to list eligible agents and for telemetry. */
  intent: string;
  /** Existing field content, appended to the prompt as reference context. */
  currentValue?: string;
  /** Trigger button label. Defaults to "Generate with AI". */
  label?: string;
  /** Placeholder for the free-text instruction field in the dialog. */
  promptPlaceholder?: string;
  /** Builds the base structured prompt sent to the agent. */
  buildPrompt: () => string;
  /** Applies the accepted result to the form. */
  onAccept: (content: string) => void;
  /** Extracts the target field(s) from the raw agent response. Defaults to identity. */
  parseResponse?: (raw: string) => string;
  disabled?: boolean;
}

const PhishingAiGenerateButton: FunctionComponent<PhishingAiGenerateButtonProps> = ({
  intent,
  currentValue,
  label,
  promptPlaceholder,
  buildPrompt,
  onAccept,
  parseResponse,
  disabled = false,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const {
    isValidated: isEnterpriseEdition,
    openDialog: openEnterpriseEditionDialog,
    setEEFeatureDetectedInfo,
  } = useEnterpriseEdition();
  const { enabled, isCguPending, configured, xtmOneConfigured } = useAI();

  const [open, setOpen] = useState(false);
  const [openValidateTermsOfUse, setOpenValidateTermsOfUse] = useState(false);
  const [userPrompt, setUserPrompt] = useState('');
  const [agentOptions, setAgentOptions] = useState<AgentOption[]>([]);
  const [selectedAgent, setSelectedAgent] = useState<AgentOption | null>(null);
  const [loadingAgents, setLoadingAgents] = useState(false);

  const { content, setContent, loading, error, execute, abort } = useAgentStream();

  // Load eligible agents when the dialog opens.
  useEffect(() => {
    if (!open || !xtmOneConfigured) return;
    setLoadingAgents(true);
    setSelectedAgent(null);
    setAgentOptions([]);
    fetchAgentsForIntent(intent)
      .then((agents) => {
        setAgentOptions(agents);
        if (agents.length > 0) setSelectedAgent(agents[0]);
      })
      .finally(() => setLoadingAgents(false));
  }, [open, xtmOneConfigured, intent]);

  // Hide entirely when AI is explicitly disabled.
  if (enabled === false) {
    return null;
  }

  const isAvailable = isEnterpriseEdition && enabled && (configured || xtmOneConfigured);
  const btnLabel = label ?? t('Generate with AI');
  const tooltip = isAvailable ? btnLabel : `${btnLabel} (EE)`;

  const handleOpen = () => {
    if (!isEnterpriseEdition) {
      setEEFeatureDetectedInfo(t('XTM One AI'));
      openEnterpriseEditionDialog();
      return;
    }
    if (isCguPending) {
      setOpenValidateTermsOfUse(true);
      return;
    }
    setUserPrompt('');
    setContent('');
    setOpen(true);
  };

  const handleClose = () => {
    abort();
    setOpen(false);
    setContent('');
  };

  const composePrompt = (): string => {
    let prompt = buildPrompt();
    if (isNotEmptyField(userPrompt.trim())) {
      prompt += `\n\nUser instructions: ${userPrompt.trim()}`;
    }
    if (isNotEmptyField(currentValue) && isNotEmptyField(currentValue?.trim())) {
      prompt += `\n\nExisting content for reference:\n${currentValue?.trim()}`;
    }
    return prompt;
  };

  const handleGenerate = () => {
    if (!selectedAgent) return;
    execute(selectedAgent.slug, composePrompt(), intent);
  };

  const handleAccept = () => {
    const parsed = parseResponse ? parseResponse(content) : content;
    onAccept(parsed);
    handleClose();
  };

  const noAgents = Boolean(xtmOneConfigured) && !loadingAgents && agentOptions.length === 0;

  const actionColor = isEnterpriseEdition ? 'ai.main' : 'action.disabled';
  const actionBorderColor = isEnterpriseEdition ? 'ai.main' : 'action.disabledBackground';

  return (
    <div style={{
      display: 'flex',
      justifyContent: 'flex-end',
    }}
    >
      <EETooltip forAi title={tooltip}>
        <span style={{
          display: 'flex',
          alignItems: 'center',
        }}
        >
          <Button
            type="button"
            variant="outlined"
            size="small"
            onClick={handleOpen}
            disabled={disabled}
            aria-label={btnLabel}
            startIcon={<SvgIcon component={LogoXtmOneIcon} fontSize="small" inheritViewBox />}
            endIcon={isEnterpriseEdition ? undefined : <span><EEChip /></span>}
            sx={{
              height: 36,
              whiteSpace: 'nowrap',
              color: actionColor,
              borderColor: actionBorderColor,
            }}
          >
            {btnLabel}
          </Button>
        </span>
      </EETooltip>

      {openValidateTermsOfUse && (
        <FiligranAiCguDialog
          open={openValidateTermsOfUse}
          onClose={() => setOpenValidateTermsOfUse(false)}
        />
      )}

      <Dialog
        PaperProps={{ elevation: 1 }}
        open={open}
        onClose={handleClose}
        fullWidth={true}
        maxWidth="md"
      >
        <DialogTitle>
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 2,
          }}
          >
            <span>{btnLabel}</span>
            {xtmOneConfigured && (
              <AgentSelector
                options={agentOptions}
                value={selectedAgent}
                onChange={setSelectedAgent}
                loading={loadingAgents}
                disabled={loading}
              />
            )}
          </Box>
        </DialogTitle>
        <DialogContent>
          <Box sx={{
            display: 'flex',
            flexDirection: 'column',
            gap: theme.spacing(2),
            mt: 1,
          }}
          >
            <TextField
              label={t('Instructions')}
              placeholder={promptPlaceholder}
              value={userPrompt}
              onChange={event => setUserPrompt(event.target.value)}
              multiline
              minRows={2}
              maxRows={4}
              fullWidth
              disabled={loading}
            />
            <Box sx={{
              display: 'flex',
              justifyContent: 'flex-start',
            }}
            >
              <LoadingButton
                variant="contained"
                color="primary"
                loading={loading}
                disabled={!selectedAgent || noAgents}
                onClick={handleGenerate}
                startIcon={<SvgIcon component={LogoXtmOneIcon} fontSize="small" inheritViewBox />}
              >
                {t('Generate')}
              </LoadingButton>
            </Box>

            {noAgents && (
              <Alert severity="info" variant="outlined">
                {t('No agent available for this action. Ask your administrator to configure XTM One.')}
              </Alert>
            )}
            {error && (
              <Alert severity="error" variant="outlined">
                {error}
              </Alert>
            )}
            {loading && !content && (
              <Box sx={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                py: 4,
              }}
              >
                <CircularProgress size={32} />
              </Box>
            )}
            {(content || (!loading && isNotEmptyField(content))) && (
              <TextField
                label={t('Result')}
                value={content}
                onChange={event => setContent(event.target.value)}
                multiline
                minRows={10}
                maxRows={20}
                fullWidth
                disabled={loading}
              />
            )}
          </Box>
        </DialogContent>
        <DialogActions>
          <Button variant="outlined" color="primary" onClick={handleClose}>
            {t('Close')}
          </Button>
          <LoadingButton
            variant="contained"
            color="primary"
            loading={loading}
            disabled={!isNotEmptyField(content)}
            onClick={handleAccept}
          >
            {t('Accept')}
          </LoadingButton>
        </DialogActions>
      </Dialog>
    </div>
  );
};

export default PhishingAiGenerateButton;
