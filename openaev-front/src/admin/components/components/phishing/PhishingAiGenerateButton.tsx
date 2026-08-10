import { AutoAwesomeOutlined } from '@mui/icons-material';
import { LoadingButton } from '@mui/lab';
import { Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, LinearProgress, SvgIcon, TextField, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { LogoXtmOneIcon } from 'filigran-icon';
import { type FunctionComponent, type ReactNode, useEffect, useMemo, useState } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type AgentOption, fetchAgentsForIntent } from '../../../../utils/ai/agentApi';
import AgentSelector from '../../../../utils/ai/AgentSelector';
import useAgentStream from '../../../../utils/ai/useAgentStream';
import useAI from '../../../../utils/hooks/useAI';
import useAuth from '../../../../utils/hooks/useAuth';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
import { isNotEmptyField } from '../../../../utils/utils';
import FiligranAiCguDialog from '../../ariane/FiligranAiCguDialog';
import isXtmOneAvailable from '../../ariane/xtmOneAvailability';
import EEChip from '../../common/entreprise_edition/EEChip';
import EETooltip from '../../common/entreprise_edition/EETooltip';

export interface PhishingAiSuggestion {
  /** Short chip label shown to the operator. */
  label: string;
  /** Instruction text inserted into the free-text field when the chip is clicked. */
  instruction: string;
}

export interface PhishingAiGenerateButtonProps {
  /** XTM One catalog intent used to list eligible agents and for telemetry. */
  intent: string;
  /** Existing field content: drives create-vs-refine copy and is appended as reference. */
  currentValue?: string;
  /** Trigger button label. Defaults to "Generate with AI". */
  label?: string;
  /** Placeholder for the free-text instruction field in the dialog. */
  promptPlaceholder?: string;
  /** Builds the base structured prompt sent to the agent. */
  buildPrompt: () => string;
  /** Applies the accepted raw agent response to the form. */
  onAccept: (content: string) => void;
  /** One-click instruction presets (e.g. "Microsoft 365 sign-in", "Match our brand"). */
  suggestions?: PhishingAiSuggestion[];
  /** Rich renderer for the streamed/final result (live preview + code). Falls back to a raw text area. */
  renderResult?: (args: {
    raw: string;
    loading: boolean;
  }) => ReactNode;
  disabled?: boolean;
}

const PhishingAiGenerateButton: FunctionComponent<PhishingAiGenerateButtonProps> = ({
  intent,
  currentValue,
  label,
  promptPlaceholder,
  buildPrompt,
  onAccept,
  suggestions = [],
  renderResult,
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
  const { settings } = useAuth();

  const [open, setOpen] = useState(false);
  const [openValidateTermsOfUse, setOpenValidateTermsOfUse] = useState(false);
  const [userPrompt, setUserPrompt] = useState('');
  const [agentOptions, setAgentOptions] = useState<AgentOption[]>([]);
  const [selectedAgent, setSelectedAgent] = useState<AgentOption | null>(null);
  const [loadingAgents, setLoadingAgents] = useState(false);

  const { content, setContent, loading, error, execute, abort } = useAgentStream();

  const isRefine = isNotEmptyField(currentValue) && isNotEmptyField(currentValue?.trim());

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

  const hasResult = isNotEmptyField(content);
  const contextualPlaceholder = useMemo(() => {
    if (promptPlaceholder) return promptPlaceholder;
    return isRefine
      ? t('Refine the current design, e.g. match a brand or add a logo')
      : t('Describe what you want to generate');
  }, [promptPlaceholder, isRefine, t]);

  // Gate the button on the presence of XTM One, exactly like the top-bar
  // Ask Ariane / CTEM entry points (single source of truth): no XTM One
  // connection means no agentic generation is reachable, so the button must
  // not appear at all. Enterprise Edition gating still applies below (EE chip
  // + dialog) when XTM One is connected on a non-EE platform.
  if (!isXtmOneAvailable(settings)) {
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
    if (isRefine) {
      prompt += `\n\nExisting content to refine (keep what works, improve the rest):\n${currentValue?.trim()}`;
    }
    return prompt;
  };

  const handleGenerate = () => {
    if (!selectedAgent) return;
    execute(selectedAgent.slug, composePrompt(), intent);
  };

  const handleAccept = () => {
    onAccept(content);
    handleClose();
  };

  // Clicking a preset replaces the instruction field with that preset - the
  // chips are starting points, not additive fragments (appending stacked every
  // click into one run-on prompt).
  const applySuggestion = (instruction: string) => {
    setUserPrompt(instruction);
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
        <DialogTitle sx={{ paddingBottom: 1 }}>
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 2,
          }}
          >
            <Box sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1,
            }}
            >
              <SvgIcon component={LogoXtmOneIcon} inheritViewBox sx={{ color: 'ai.main' }} />
              <span>{btnLabel}</span>
            </Box>
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
            <div>
              <TextField
                label={t('Instructions')}
                placeholder={contextualPlaceholder}
                value={userPrompt}
                onChange={event => setUserPrompt(event.target.value)}
                multiline
                minRows={2}
                maxRows={4}
                fullWidth
                disabled={loading}
              />
              {suggestions.length > 0 && (
                <Box sx={{
                  display: 'flex',
                  flexWrap: 'wrap',
                  gap: 0.75,
                  mt: 1,
                }}
                >
                  {suggestions.map(suggestion => (
                    <Chip
                      key={suggestion.label}
                      label={suggestion.label}
                      size="small"
                      variant="outlined"
                      icon={<AutoAwesomeOutlined sx={{ fontSize: 14 }} />}
                      onClick={() => applySuggestion(suggestion.instruction)}
                      disabled={loading}
                      sx={{
                        'borderColor': alpha(theme.palette.ai.main, 0.4),
                        'color': 'text.secondary',
                        '& .MuiChip-icon': { color: 'ai.main' },
                        '&:hover': { backgroundColor: alpha(theme.palette.ai.main, 0.08) },
                      }}
                    />
                  ))}
                </Box>
              )}
            </div>

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
                {hasResult ? t('Regenerate') : t('Generate')}
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

            {loading && !hasResult && (
              <Box>
                <LinearProgress color="primary" />
                <Typography
                  variant="caption"
                  sx={{
                    display: 'block',
                    mt: 1,
                    color: 'text.secondary',
                  }}
                >
                  {t('Generating the preview...')}
                </Typography>
              </Box>
            )}

            {hasResult && (
              renderResult
                ? renderResult({
                    raw: content,
                    loading,
                  })
                : (
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
                  )
            )}
          </Box>
        </DialogContent>
        <DialogActions>
          <Button variant="outlined" color="primary" onClick={handleClose}>
            {t('Close')}
          </Button>
          <Button
            variant="contained"
            color="primary"
            disabled={loading || !hasResult}
            onClick={handleAccept}
          >
            {t('Accept')}
          </Button>
        </DialogActions>
      </Dialog>
    </div>
  );
};

export default PhishingAiGenerateButton;
