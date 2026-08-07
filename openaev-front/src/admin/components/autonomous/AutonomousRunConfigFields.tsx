import {
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
  TextField,
  Typography,
} from '@mui/material';
import { alpha, type Theme, useTheme } from '@mui/material/styles';

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
  disabled?: boolean;
}

/**
 * The full autonomous-run configuration body, driven by {@link useAutonomousRunConfig}: an objective
 * gallery + free-text objective, the specialist-agents picker, an optional run label, the time
 * budget, and the allow / deny scope lists (rendered as accordions so the whole thing embeds inside
 * any drawer or creation form).
 */
export const AutonomousRunConfigFields = ({ config, disabled }: AutonomousRunConfigFieldsProps) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { settings } = useAuth();
  const xtmOneUrl = toHttpUrl(settings.platform_xtm_one_url)?.replace(/\/+$/, '');

  return (
    <Stack sx={{ gap: theme.spacing(3) }}>
      <Box>
        <Typography variant="h2" gutterBottom>
          {t('Objective')}
        </Typography>
        <Stack
          sx={{
            display: 'grid',
            gap: theme.spacing(1),
            gridTemplateColumns: 'repeat(2, 1fr)',
            marginBottom: theme.spacing(2),
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

      <Box>
        <Typography variant="h2" gutterBottom>
          {t('Label (optional)')}
        </Typography>
        <Stack sx={{ gap: theme.spacing(2) }}>
          <TextField
            value={config.name}
            onChange={event => config.setName(event.target.value)}
            label={t('Name')}
            placeholder={t('Auto-generated if left empty')}
            fullWidth
            disabled={disabled}
          />
          <TextField
            value={config.description}
            onChange={event => config.setDescription(event.target.value)}
            label={t('Description')}
            placeholder={t('Auto-generated from the objective if left empty')}
            multiline
            minRows={2}
            fullWidth
            disabled={disabled}
          />
        </Stack>
      </Box>

      <Box>
        <Typography variant="h2" gutterBottom>
          {t('Time budget')}
        </Typography>
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
          helperText={t('Maximum run duration. OpenAEV steers the orchestrator to converge a few minutes before this deadline, then hard-stops the run. Default is 24 hours.')}
          fullWidth
          disabled={disabled}
        />
      </Box>

      <Accordion
        variant="outlined"
        sx={{
          '&:before': { display: 'none' },
          'borderRadius': 1,
        }}
      >
        <AccordionSummary expandIcon={<ExpandMore />}>
          <Typography variant="h2" sx={{ margin: 0 }}>
            {config.allowCount > 0 ? `${t('Allow list')} (${config.allowCount})` : t('Allow list')}
          </Typography>
        </AccordionSummary>
        <AccordionDetails>
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
        </AccordionDetails>
      </Accordion>

      <Accordion
        variant="outlined"
        sx={{
          '&:before': { display: 'none' },
          'borderRadius': 1,
        }}
      >
        <AccordionSummary expandIcon={<ExpandMore />}>
          <Typography variant="h2" sx={{ margin: 0 }}>
            {config.denyCount > 0 ? `${t('Deny list')} (${config.denyCount})` : t('Deny list')}
          </Typography>
        </AccordionSummary>
        <AccordionDetails>
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
        </AccordionDetails>
      </Accordion>
    </Stack>
  );
};

export interface AutonomousRunConfigPanelProps {
  config: AutonomousRunConfig;
  submitting?: boolean;
  error?: string | null;
  /** AI-accent info banner shown at the top; omit to hide it (e.g. when a host already explains). */
  infoText?: string;
  /**
   * Show the "Save for later" action: a neutral secondary button that persists the configuration
   * WITHOUT starting anything (the scenario AI builder). Built with plan_mode so the saved input is
   * mode-agnostic; the host decides Build vs Launch at action time.
   */
  showSave?: boolean;
  showPlan?: boolean;
  showLaunch?: boolean;
  saveLabel?: string;
  planLabel?: string;
  launchLabel?: string;
  onSave?: (input: AutonomousRunCreateInput) => void;
  onPlan?: (input: AutonomousRunCreateInput) => void;
  onLaunch?: (input: AutonomousRunCreateInput) => void;
  onCancel: () => void;
  cancelLabel?: string;
}

/**
 * The AI-accent banner + {@link AutonomousRunConfigFields} + a Plan / Launch footer, with NO drawer
 * chrome, so it can be dropped either inside {@link AutonomousRunConfigDrawer} or straight into
 * another surface (e.g. the scenario-creation drawer's "Generate with AI" step) without nesting
 * drawers. The caller owns the {@link useAutonomousRunConfig} instance and the submit handlers.
 */
export const AutonomousRunConfigPanel = ({
  config,
  submitting = false,
  error,
  infoText,
  showSave = false,
  showPlan = true,
  showLaunch = true,
  saveLabel,
  planLabel,
  launchLabel,
  onSave,
  onPlan,
  onLaunch,
  onCancel,
  cancelLabel,
}: AutonomousRunConfigPanelProps) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const canSubmit = config.canSubmit && !submitting;

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

      <AutonomousRunConfigFields config={config} disabled={submitting} />

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
          {showSave && onSave && (
            <Button
              onClick={() => onSave(config.buildInput(true))}
              variant="outlined"
              color="inherit"
              disabled={!canSubmit}
              data-testid="button-autonomous-save"
            >
              {saveLabel ?? t('Save for later')}
            </Button>
          )}
          {showPlan && onPlan && (
            <Button
              onClick={() => onPlan(config.buildInput(true))}
              variant="outlined"
              color="inherit"
              disabled={!canSubmit}
              startIcon={<AutoAwesome />}
              data-testid="button-autonomous-plan"
            >
              {planLabel ?? t('Plan (dry-run)')}
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
