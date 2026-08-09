import {
  ArrowBack,
  ArrowUpward,
  AutoAwesome,
  Diamond,
  Dns,
  ExpandMore,
  HowToReg,
  Hub,
  Key,
  Lock,
  MailOutline,
  MeetingRoom,
  Public,
  Shield,
  Storage,
  type SvgIconComponent,
  TrackChanges,
} from '@mui/icons-material';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Button,
  Card,
  CardActionArea,
  Skeleton,
  Stack,
  Step,
  StepLabel,
  Stepper,
  TextField,
  Typography,
} from '@mui/material';
import { alpha, type Theme, useTheme } from '@mui/material/styles';
import { useState } from 'react';

import {
  type AutonomousRunCreateInput,
  ORCHESTRATOR_AGENT_ID,
} from '../../../actions/autonomous/autonomous-types';
import { useFormatter } from '../../../components/i18n';
import useAuth from '../../../utils/hooks/useAuth';
import { toHttpUrl } from '../../../utils/url-helper';
import ScopeForm from '../chaining/ScopeForm';
import AutonomousAgentsSelector from './AutonomousAgentsSelector';
import { type AutonomousRunConfig, BUILTIN_AGENT_SLUG, type ScopeSelection } from './useAutonomousRunConfig';

// Maps the objective-template icon tokens seeded server-side (kebab-case, see
// AutonomousObjectiveTemplateService) to MUI icons. Unknown/empty tokens fall back to a generic
// "objective" target icon.
const OBJECTIVE_ICONS: Record<string, SvgIconComponent> = {
  'domain': Dns,
  'database': Storage,
  'shield': Shield,
  'door-open': MeetingRoom,
  'arrow-up': ArrowUpward,
  'key': Key,
  'mail': MailOutline,
  'network': Hub,
  'lock': Lock,
  'gem': Diamond,
  'globe': Public,
  'user-check': HowToReg,
};

// AI-accent styling for the top info banner, shared by every host of the config panel.
const aiAlertSx = (theme: Theme) => ({
  'color': theme.palette.ai.main,
  'borderColor': alpha(theme.palette.ai.main, 0.5),
  'backgroundColor': alpha(theme.palette.ai.main, 0.08),
  '& .MuiAlert-icon': { color: theme.palette.ai.main },
});

/**
 * Embeds the manual chained-scope picker body ({@link ScopeForm}, minus its own footer) for a single
 * list, wiring the one {@link ScopeSelection} state object to ScopeForm's per-kind change callbacks.
 */
const ScopeSection = ({
  mode,
  selection,
  onChange,
}: {
  mode: 'ALLOWLIST' | 'DENYLIST';
  selection: ScopeSelection;
  onChange: (next: ScopeSelection) => void;
}) => (
  <ScopeForm
    mode={mode}
    hideFooter
    selectedEndpointIds={selection.endpointIds}
    selectedAssetGroupIds={selection.assetGroupIds}
    selectedTeamIds={selection.teamIds}
    selectedPlayerIds={selection.playerIds}
    selectedCustomRules={selection.customRules}
    initialEndpointIds={selection.endpointIds}
    initialAssetGroupIds={selection.assetGroupIds}
    initialTeamIds={selection.teamIds}
    initialPlayerIds={selection.playerIds}
    initialCustomRules={selection.customRules}
    onEndpointIdsChange={ids => onChange({
      ...selection,
      endpointIds: ids,
    })}
    onAssetGroupIdsChange={ids => onChange({
      ...selection,
      assetGroupIds: ids,
    })}
    onTeamIdsChange={ids => onChange({
      ...selection,
      teamIds: ids,
    })}
    onPlayerIdsChange={ids => onChange({
      ...selection,
      playerIds: ids,
    })}
    onCustomRulesChange={rules => onChange({
      ...selection,
      customRules: rules,
    })}
  />
);

export interface AutonomousRunConfigFieldsProps {
  config: AutonomousRunConfig;
  /**
   * Which stepper page to render: 0 = objective + specialist agents + time budget, 1 = allow list,
   * 2 = deny list. The scope lists are split across their own steps (the previous, preferred UX)
   * rather than crammed into accordions, so each picker gets the full drawer width.
   */
  activeStep: number;
  disabled?: boolean;
  /**
   * When the scenario is already defined (manually authored or AI-built) and is being launched in
   * autonomous mode, the sensible course is "execute what is defined, then iterate": the free-text
   * mission (pre-seeded by the hook) becomes the primary control and the objective-template gallery
   * is demoted into a collapsed accordion, signalling it is an override, not the normal flow. When
   * false (no plan / no logic yet), the gallery stays front-and-center to define the objective.
   */
  demoteTemplates?: boolean;
  /**
   * Hide the time-budget field entirely. Used by the AI builder (planning): plan mode is untimed
   * server-side and only ever takes a few minutes, so surfacing a budget is meaningless. A hidden
   * budget is never sent (the hook omits `timeout_seconds` in plan mode).
   */
  hideTimeBudget?: boolean;
  /**
   * Optional short note rendered just above the time-budget field. The autonomous launch drawer uses
   * it to warn that the default 24h budget overrides the scenario's own configured timeout - shown
   * only when they actually differ.
   */
  timeBudgetNote?: string;
}

/**
 * The autonomous-run configuration body for one stepper page, driven by {@link useAutonomousRunConfig}.
 * Step 0 is the objective gallery + free-text objective, the specialist-agents picker and the time
 * budget; steps 1 and 2 are the allow / deny scope lists (each reusing the manual chained-scope
 * picker at full width). The run is NOT labelled here - the scenario already exists and its name /
 * description are edited on the scenario itself - so there is no name / description field.
 */
export const AutonomousRunConfigFields = ({ config, activeStep, disabled, demoteTemplates, hideTimeBudget, timeBudgetNote }: AutonomousRunConfigFieldsProps) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { settings } = useAuth();
  const xtmOneUrl = toHttpUrl(settings.platform_xtm_one_url)?.replace(/\/+$/, '');

  const objectiveField = (
    <TextField
      value={config.objective}
      onChange={event => config.setObjective(event.target.value)}
      label={t('Objective (free text)')}
      placeholder={t('e.g. Reach the domain controller and prove domain admin from an initial foothold')}
      multiline
      minRows={3}
      fullWidth
      disabled={disabled}
    />
  );

  const templateGallery = (
    <Stack
      sx={{
        display: 'grid',
        gap: theme.spacing(1),
        gridTemplateColumns: 'repeat(2, 1fr)',
      }}
    >
      {config.loadingTemplates && config.templates.length === 0
        ? ['s1', 's2', 's3', 's4', 's5', 's6', 's7', 's8'].map(skeletonKey => (
            <Card key={skeletonKey} variant="outlined">
              <Box sx={{ padding: theme.spacing(1.5) }}>
                <Stack direction="row" spacing={1.5} alignItems="center">
                  <Skeleton variant="circular" width={20} height={20} sx={{ flexShrink: 0 }} />
                  <Box sx={{ flex: 1 }}>
                    <Skeleton variant="text" width="55%" height={18} />
                    <Skeleton variant="text" width="90%" height={14} />
                  </Box>
                </Stack>
              </Box>
            </Card>
          ))
        : config.templates.map((template) => {
            const isSelected = config.selectedTemplateKey === template.autonomous_objective_template_key;
            const ObjectiveIcon = OBJECTIVE_ICONS[template.autonomous_objective_template_icon ?? ''] ?? TrackChanges;
            return (
              <Card
                key={template.autonomous_objective_template_key}
                variant="outlined"
                sx={{
                  borderColor: isSelected ? theme.palette.ai.main : undefined,
                  borderWidth: isSelected ? 2 : 1,
                  backgroundColor: isSelected ? alpha(theme.palette.ai.main, 0.08) : undefined,
                }}
              >
                <CardActionArea
                  onClick={() => config.selectTemplate(template)}
                  disabled={disabled}
                  sx={{
                    padding: theme.spacing(1.5),
                    height: '100%',
                  }}
                >
                  <Stack direction="row" spacing={1.5} alignItems="center">
                    <ObjectiveIcon
                      fontSize="small"
                      sx={{
                        flexShrink: 0,
                        color: isSelected ? theme.palette.ai.main : theme.palette.text.secondary,
                      }}
                    />
                    <Box>
                      <Typography
                        variant="subtitle2"
                        sx={{
                          fontWeight: 'bold',
                          fontSize: '0.8125rem',
                        }}
                      >
                        {t(template.autonomous_objective_template_label)}
                      </Typography>
                      {template.autonomous_objective_template_description && (
                        <Typography variant="caption" color="text.secondary">
                          {t(template.autonomous_objective_template_description)}
                        </Typography>
                      )}
                    </Box>
                  </Stack>
                </CardActionArea>
              </Card>
            );
          })}
    </Stack>
  );

  return (
    <Stack sx={{ gap: theme.spacing(3) }}>
      {activeStep === 0 && (
        <>
          <Box>
            <Typography variant="h2" gutterBottom>
              {t('Objective')}
            </Typography>
            {demoteTemplates
              ? (
                  // Already-defined scenario launched autonomously: the run first executes what is
                  // defined, so the free-text mission leads and the template gallery is a demoted,
                  // collapsed override rather than the default course of action.
                  <>
                    <Alert
                      severity="info"
                      variant="outlined"
                      sx={{ marginBottom: theme.spacing(2) }}
                    >
                      {t('The run first executes the attack path already defined in this scenario, then the orchestrator keeps iterating from there. Adjust the mission below if needed.')}
                    </Alert>
                    {objectiveField}
                    <Accordion
                      variant="outlined"
                      disableGutters
                      sx={{
                        'marginTop': theme.spacing(2),
                        '&::before': { display: 'none' },
                      }}
                    >
                      <AccordionSummary expandIcon={<ExpandMore />}>
                        <Typography variant="subtitle2">
                          {t('Start from a pre-built objective template instead')}
                        </Typography>
                      </AccordionSummary>
                      <AccordionDetails>
                        <Typography
                          variant="caption"
                          color="text.secondary"
                          sx={{
                            display: 'block',
                            marginBottom: theme.spacing(1.5),
                          }}
                        >
                          {t('Replaces the mission above with a ready-made objective. Not the usual course when the scenario is already defined.')}
                        </Typography>
                        {templateGallery}
                      </AccordionDetails>
                    </Accordion>
                  </>
                )
              : (
                  <>
                    <Box sx={{ marginBottom: theme.spacing(2) }}>
                      {templateGallery}
                    </Box>
                    {objectiveField}
                  </>
                )}
          </Box>

          <Box>
            <AutonomousAgentsSelector
              title={t('Agents')}
              agents={config.availableAgents}
              enabledIds={config.selectedAgentIds}
              onToggle={config.toggleAgent}
              modes={config.selectedAgentModes}
              onModeChange={config.changeAgentMode}
              orchestrator={{
                id: ORCHESTRATOR_AGENT_ID,
                name: t('Autonomous orchestrator'),
                description: t('Plans and drives the entire attack path, and consults the agents below.'),
              }}
              builtinSlug={BUILTIN_AGENT_SLUG}
              loading={config.loadingAgents}
              disabled={disabled}
              createAgentUrl={xtmOneUrl ? `${xtmOneUrl}/agents/new` : undefined}
              infoTooltip={t('Specialist agents the orchestrator can consult during the attack (payload creation, code generation, recon, exploitation support). Prefilled from your tenant defaults; built-in agents are enabled by default but can be turned off or replaced for this run. Each agent\'s discovery mode controls how much it may create from recon: enrich existing entities only, stay within scope, or expand the perimeter.')}
            />
          </Box>

          {!hideTimeBudget && (
            <Box>
              <Typography variant="h2" gutterBottom>
                {t('Time budget')}
              </Typography>
              {timeBudgetNote && (
                <Alert
                  severity="info"
                  variant="outlined"
                  sx={{ marginBottom: theme.spacing(1.5) }}
                >
                  {timeBudgetNote}
                </Alert>
              )}
              <TextField
                type="number"
                value={config.timeoutHours}
                onChange={(event) => {
                  const parsed = Number(event.target.value);
                  config.setTimeoutHours(Number.isFinite(parsed) && parsed > 0 ? parsed : 1);
                }}
                label={t('Timeout (hours)')}
                slotProps={{
                  htmlInput: {
                    min: 1,
                    max: 720,
                    step: 1,
                  },
                }}
                helperText={t('Maximum duration of a live run. OpenAEV steers the orchestrator to converge a few minutes before this deadline, then hard-stops the run.')}
                fullWidth
                disabled={disabled}
              />
            </Box>
          )}
        </>
      )}

      {activeStep === 1 && (
        <Box>
          <Typography variant="h2" gutterBottom>
            {t('Allow list')}
          </Typography>
          <Typography
            variant="caption"
            color="text.secondary"
            sx={{
              display: 'block',
              marginBottom: theme.spacing(2),
            }}
          >
            {t('Optional. The perimeter the AI is authorized to attack: add assets, asset groups, teams, persons, or manual IPs / hostnames. Leave empty and the AI will ask you to choose targets before it attacks anything.')}
          </Typography>
          <ScopeSection mode="ALLOWLIST" selection={config.allowScope} onChange={config.setAllowScope} />
        </Box>
      )}

      {activeStep === 2 && (
        <Box>
          <Typography variant="h2" gutterBottom>
            {t('Deny list')}
          </Typography>
          <Typography
            variant="caption"
            color="text.secondary"
            sx={{
              display: 'block',
              marginBottom: theme.spacing(2),
            }}
          >
            {t('Optional. Explicit carve-outs the AI must never touch, even if they fall inside the allow-list. Deny always wins over allow.')}
          </Typography>
          <ScopeSection mode="DENYLIST" selection={config.denyScope} onChange={config.setDenyScope} />
        </Box>
      )}
    </Stack>
  );
};

export interface AutonomousRunConfigPanelProps {
  config: AutonomousRunConfig;
  submitting?: boolean;
  error?: string | null;
  /** AI-accent info banner shown at the top; omit to hide it (e.g. when a host already explains). */
  infoText?: string;
  /** Demote the objective-template gallery into a collapsed accordion (already-defined scenario
   *  launched autonomously - see {@link AutonomousRunConfigFieldsProps.demoteTemplates}). */
  demoteTemplates?: boolean;
  /** Hide the time-budget field entirely (AI builder / plan mode - see
   *  {@link AutonomousRunConfigFieldsProps.hideTimeBudget}). */
  hideTimeBudget?: boolean;
  /** Short note above the time-budget field (24h-overrides-scenario-config warning - see
   *  {@link AutonomousRunConfigFieldsProps.timeBudgetNote}). */
  timeBudgetNote?: string;
  /**
   * Show the "Save for later" action: a neutral secondary button that persists the configuration
   * WITHOUT starting anything (the scenario AI builder). Built with plan_mode so the saved input is
   * mode-agnostic; the host decides Build vs Launch at action time.
   */
  showSave?: boolean;
  showLaunch?: boolean;
  saveLabel?: string;
  launchLabel?: string;
  onSave?: (input: AutonomousRunCreateInput) => void;
  onLaunch?: (input: AutonomousRunCreateInput) => void;
  onCancel: () => void;
  cancelLabel?: string;
}

/**
 * The AI-accent banner + {@link AutonomousRunConfigFields} + a Save / Launch footer, with NO drawer
 * chrome, so it can be dropped either inside {@link AutonomousRunConfigDrawer} or straight into
 * another surface (e.g. the scenario-creation drawer's "Generate with AI" step) without nesting
 * drawers. The caller owns the {@link useAutonomousRunConfig} instance and the submit handlers.
 */
export const AutonomousRunConfigPanel = ({
  config,
  submitting = false,
  error,
  infoText,
  demoteTemplates,
  hideTimeBudget,
  timeBudgetNote,
  showSave = false,
  showLaunch = true,
  saveLabel,
  launchLabel,
  onSave,
  onLaunch,
  onCancel,
  cancelLabel,
}: AutonomousRunConfigPanelProps) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const canSubmit = config.canSubmit && !submitting;
  // Three-step launch flow (the preferred UX over accordions): objective + agents + time budget,
  // then the allow-list, then the deny-list. Scope is optional, so Save / Launch stay
  // available on every step and the operator can skip straight past the scope steps.
  const [activeStep, setActiveStep] = useState(0);
  const stepLabels = [
    t('Objective'),
    config.allowCount > 0 ? `${t('Allow list')} (${config.allowCount})` : t('Allow list'),
    config.denyCount > 0 ? `${t('Deny list')} (${config.denyCount})` : t('Deny list'),
  ];
  const lastStep = stepLabels.length - 1;

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(3),
    }}
    >
      {infoText && (
        <Alert
          severity="info"
          variant="outlined"
          icon={<AutoAwesome fontSize="inherit" />}
          sx={aiAlertSx(theme)}
        >
          {infoText}
        </Alert>
      )}

      <Stepper activeStep={activeStep}>
        {stepLabels.map((label, index) => (
          <Step key={label} completed={false}>
            <StepLabel
              onClick={() => setActiveStep(index)}
              sx={{ cursor: 'pointer' }}
            >
              {label}
            </StepLabel>
          </Step>
        ))}
      </Stepper>

      <AutonomousRunConfigFields
        config={config}
        activeStep={activeStep}
        disabled={submitting}
        demoteTemplates={demoteTemplates}
        hideTimeBudget={hideTimeBudget}
        timeBudgetNote={timeBudgetNote}
      />

      {error && <Alert severity="error">{error}</Alert>}

      <Box sx={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        gap: theme.spacing(1),
      }}
      >
        <Button onClick={onCancel} disabled={submitting}>
          {cancelLabel ?? t('Cancel')}
        </Button>
        <Box sx={{
          display: 'flex',
          gap: theme.spacing(1),
        }}
        >
          {activeStep > 0 && (
            <Button
              onClick={() => setActiveStep(step => step - 1)}
              startIcon={<ArrowBack />}
              disabled={submitting}
            >
              {t('Back')}
            </Button>
          )}
          {activeStep < lastStep && (
            <Button
              onClick={() => setActiveStep(step => step + 1)}
              variant="outlined"
              disabled={submitting}
            >
              {t('Next')}
            </Button>
          )}
          {showSave && onSave && (
            <Button
              onClick={() => onSave(config.buildInput(true))}
              variant="contained"
              disabled={!canSubmit}
              data-testid="button-autonomous-save"
            >
              {saveLabel ?? t('Save for later')}
            </Button>
          )}
          {showLaunch && onLaunch && (
            <Button
              onClick={() => onLaunch(config.buildInput(false))}
              variant="contained"
              disabled={!canSubmit}
              startIcon={<AutoAwesome />}
              data-testid="button-autonomous-launch"
              sx={{
                'backgroundColor': theme.palette.ai.main,
                'color': theme.palette.ai.contrastText,
                '&:hover': { backgroundColor: theme.palette.ai.dark },
              }}
            >
              {launchLabel ?? t('Launch now')}
            </Button>
          )}
        </Box>
      </Box>
    </Box>
  );
};
