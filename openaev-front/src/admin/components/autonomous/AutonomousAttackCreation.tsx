import {
  ArrowBack,
  ArrowUpward,
  AutoAwesome,
  Diamond,
  Dns,
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
import { Alert, Box, Button, Card, CardActionArea, IconButton, Skeleton, Stack, Step, StepLabel, Stepper, TextField, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router';

import { createAutonomousRun, fetchAvailableAgents, fetchDefaultAgents, fetchObjectiveTemplates, startAutonomousRun } from '../../../actions/autonomous/autonomous-actions';
import { type AdditionalAgent, type AutonomousDiscoveryMode, type AutonomousObjectiveTemplate, ORCHESTRATOR_AGENT_ID, ORCHESTRATOR_DEFAULT_DISCOVERY_MODE, SPECIALIST_DEFAULT_DISCOVERY_MODE } from '../../../actions/autonomous/autonomous-types';
import Drawer from '../../../components/common/Drawer';
import { useFormatter } from '../../../components/i18n';
import { SCENARIO_BASE_URL, SIMULATION_BASE_URL } from '../../../constants/BaseUrls';
import { type WorkflowScopeRuleInput } from '../../../utils/api-types';
import useAuth from '../../../utils/hooks/useAuth';
import useEnterpriseEdition from '../../../utils/hooks/useEnterpriseEdition';
import { toHttpUrl } from '../../../utils/url-helper';
import { isFeatureEnabled } from '../../../utils/utils';
import ScopeForm, { type ScopeCustomRule } from '../chaining/ScopeForm';
import EEChip from '../common/entreprise_edition/EEChip';
import AutonomousAgentsSelector from './AutonomousAgentsSelector';

// Maps the objective-template icon tokens seeded server-side (kebab-case, see
// AutonomousObjectiveTemplateService) to MUI icons. Unknown/empty tokens fall
// back to a generic "objective" target icon.
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

// The license-independent built-in specialist the orchestrator always consults; seeded as a default
// handover in XTM One, so it is shown as always-on and cannot be removed from the per-run picker.
const BUILTIN_AGENT_SLUG = 'openaev-payload-creator';

// One list (allow or deny) worth of selections, mirroring the state the manual chained-scope editor
// keeps per drawer (ScopeRules). Reused as-is here so the launch stepper offers the exact same
// picker experience - kind tabs, live-searched paginated lists, manual IP/CIDR/hostname input and
// CSV import - split across an Allow-list step and a Deny-list step.
interface ScopeSelection {
  endpointIds: string[];
  assetGroupIds: string[];
  teamIds: string[];
  playerIds: string[];
  customRules: ScopeCustomRule[];
}

const EMPTY_SCOPE: ScopeSelection = {
  endpointIds: [],
  assetGroupIds: [],
  teamIds: [],
  playerIds: [],
  customRules: [],
};

const scopeSelectionCount = (selection: ScopeSelection): number =>
  selection.endpointIds.length
  + selection.assetGroupIds.length
  + selection.teamIds.length
  + selection.playerIds.length
  + selection.customRules.length;

const toScopeRule = (
  mode: 'ALLOWLIST' | 'DENYLIST',
  source: WorkflowScopeRuleInput['workflow_scope_rule_source'],
  value: string,
): WorkflowScopeRuleInput => ({
  workflow_scope_rule_selected_mode: mode,
  workflow_scope_rule_source: source,
  workflow_scope_rule_value: value,
});

// Flattens a per-list selection into the workflow scope-rule inputs the create API seeds onto the
// run's scenario + simulation workflows (both lists together make the full scope definition).
const selectionToRules = (selection: ScopeSelection, mode: 'ALLOWLIST' | 'DENYLIST'): WorkflowScopeRuleInput[] => [
  ...selection.endpointIds.map(id => toScopeRule(mode, 'ASSET', id)),
  ...selection.assetGroupIds.map(id => toScopeRule(mode, 'ASSET_GROUP', id)),
  ...selection.teamIds.map(id => toScopeRule(mode, 'TEAM', id)),
  ...selection.playerIds.map(id => toScopeRule(mode, 'PLAYER', id)),
  ...selection.customRules.map(rule => toScopeRule(mode, rule.source, rule.value)),
];

/**
 * Embeds the manual chained-scope picker body ({@link ScopeForm}, minus its own footer) for a single
 * list, wiring the one {@link ScopeSelection} state object to ScopeForm's per-kind change callbacks.
 */
const ScopeStep: FunctionComponent<{
  mode: 'ALLOWLIST' | 'DENYLIST';
  selection: ScopeSelection;
  onChange: (next: ScopeSelection) => void;
}> = ({ mode, selection, onChange }) => (
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

export interface AutonomousAttackCreationProps {
  /** Pre-select an asset group as the run scope (used by entity "Autonomous attack" entries). */
  presetScopeAssetGroupId?: string;
  /**
   * Display name for the preset scope. Accepted for caller compatibility; the allow-list now resolves
   * the group's name from its id, so it is no longer needed for display.
   */
  presetScopeAssetGroupName?: string;
  /**
   * Trigger rendering: the full contained CTA button (default, used on list pages) or a
   * compact hero icon button with a tooltip (used on entity detail heroes, next to Reports).
   */
  variant?: 'button' | 'icon';
}

/**
 * Dedicated, deeply-integrated entry point for the Autonomous (AI-driven) attack path. Rendered as a
 * visible action - a contained CTA on list pages ({@code variant="button"}, the default) or a compact
 * AI-purple icon button on entity detail heroes ({@code variant="icon"}, next to Reports). Gated behind
 * the {@code AUTONOMOUS_ATTACK_PATH} preview feature and the Enterprise Edition license (every AI feature
 * is EE-only).
 *
 * <p>The drawer is a three-step launch stepper that mirrors the manual chained-scenario flow: (1) pick
 * an objective (template or free text) and optionally label the run, (2) define the allow-list, (3)
 * define the deny-list - steps 2 and 3 reuse the exact same scope components as the manual editor.
 * Scope is optional: skip it and launch, and the AI opens by asking which targets are in scope and
 * records the resolved scope itself. On the asset-group hero it pre-selects the group in the allow-list.
 */
const AutonomousAttackCreation: FunctionComponent<AutonomousAttackCreationProps> = ({
  presetScopeAssetGroupId,
  variant = 'button',
}) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const navigate = useNavigate();
  const { settings } = useAuth();
  const featureEnabled = isFeatureEnabled('AUTONOMOUS_ATTACK_PATH');
  const xtmOneUrl = toHttpUrl(settings.platform_xtm_one_url)?.replace(/\/+$/, '');
  const {
    isValidated: isEnterpriseEdition,
    openDialog: openEnterpriseEditionDialog,
    setEEFeatureDetectedInfo,
  } = useEnterpriseEdition();

  const [open, setOpen] = useState(false);
  const [activeStep, setActiveStep] = useState(0);
  const [templates, setTemplates] = useState<AutonomousObjectiveTemplate[]>([]);
  const [loadingTemplates, setLoadingTemplates] = useState(false);
  const [selectedTemplateKey, setSelectedTemplateKey] = useState<string | null>(null);
  const [objective, setObjective] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [allowScope, setAllowScope] = useState<ScopeSelection>(EMPTY_SCOPE);
  const [denyScope, setDenyScope] = useState<ScopeSelection>(EMPTY_SCOPE);
  const [availableAgents, setAvailableAgents] = useState<AdditionalAgent[]>([]);
  const [selectedAgentIds, setSelectedAgentIds] = useState<string[]>([]);
  const [selectedAgentModes, setSelectedAgentModes] = useState<Record<string, string>>({});
  const [loadingAgents, setLoadingAgents] = useState(false);
  const [agentsLoaded, setAgentsLoaded] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) {
      return;
    }
    setLoadingTemplates(true);
    fetchObjectiveTemplates()
      .then(res => setTemplates(res.data ?? []))
      .catch(() => setTemplates([]))
      .finally(() => setLoadingTemplates(false));
  }, [open]);

  // Load the specialist agents the orchestrator can consult and prefill the selection with the
  // tenant's configured defaults (set in Settings > Customization > Autonomous attack).
  useEffect(() => {
    if (!open) {
      return;
    }
    setLoadingAgents(true);
    Promise.all([fetchAvailableAgents(), fetchDefaultAgents()])
      .then(([agents, defaults]) => {
        setAvailableAgents(agents.data ?? []);
        setSelectedAgentIds(defaults.data?.agent_ids ?? []);
        setSelectedAgentModes(defaults.data?.agent_modes ?? {});
        setAgentsLoaded(true);
      })
      .catch(() => {
        setAvailableAgents([]);
        setSelectedAgentIds([]);
        setSelectedAgentModes({});
        setAgentsLoaded(false);
      })
      .finally(() => setLoadingAgents(false));
  }, [open]);

  // Apply the preset scope (e.g. an asset group's "Autonomous attack") to the allow-list whenever
  // the drawer opens, so it lands as an allow-listed asset group exactly like a manual pick.
  useEffect(() => {
    if (open && presetScopeAssetGroupId) {
      setAllowScope(current => (current.assetGroupIds.includes(presetScopeAssetGroupId)
        ? current
        : {
            ...current,
            assetGroupIds: [...current.assetGroupIds, presetScopeAssetGroupId],
          }));
    }
  }, [open, presetScopeAssetGroupId]);

  const handleOpen = () => {
    if (!isEnterpriseEdition) {
      setEEFeatureDetectedInfo(t('Autonomous attack path'));
      openEnterpriseEditionDialog();
      return;
    }
    setOpen(true);
  };

  const handleClose = () => {
    setOpen(false);
    setActiveStep(0);
    setSelectedTemplateKey(null);
    setObjective('');
    setName('');
    setDescription('');
    setAllowScope(EMPTY_SCOPE);
    setDenyScope(EMPTY_SCOPE);
    setAvailableAgents([]);
    setSelectedAgentIds([]);
    setSelectedAgentModes({});
    setAgentsLoaded(false);
    setError(null);
  };

  const handleSelectTemplate = (template: AutonomousObjectiveTemplate) => {
    setSelectedTemplateKey(template.autonomous_objective_template_key);
    setObjective(template.autonomous_objective_template_prompt);
  };

  const canLaunch = objective.trim().length > 0 && !submitting;
  const allowCount = scopeSelectionCount(allowScope);
  const denyCount = scopeSelectionCount(denyScope);

  // Every agent - including the built-in payload creator - is a normal toggle: built-ins are enabled
  // by default (prefilled from the tenant defaults) but can be turned off or replaced for this run.
  const toggleAgent = (agentId: string, enabled: boolean) => {
    setSelectedAgentIds(current => (enabled
      ? (current.includes(agentId) ? current : [...current, agentId])
      : current.filter(id => id !== agentId)));
    // Seed the specialist default (EXPANSIVE) when enabling so the launch payload always carries an
    // explicit mode; the orchestrator row is pinned and not toggled here.
    if (enabled) {
      setSelectedAgentModes(current => (current[agentId] ? current : {
        ...current,
        [agentId]: SPECIALIST_DEFAULT_DISCOVERY_MODE,
      }));
    }
  };

  const changeAgentMode = (agentId: string, mode: AutonomousDiscoveryMode) => {
    setSelectedAgentModes(current => ({
      ...current,
      [agentId]: mode,
    }));
  };

  const scopeRules = useMemo<WorkflowScopeRuleInput[]>(
    () => [...selectionToRules(allowScope, 'ALLOWLIST'), ...selectionToRules(denyScope, 'DENYLIST')],
    [allowScope, denyScope],
  );

  // Both entry points create then start the run; a dry-run passes plan_mode=true so the
  // orchestrator designs the attack path without executing anything (see the Plan button).
  const handleLaunch = (planMode = false) => {
    if (objective.trim().length === 0) {
      return;
    }
    setSubmitting(true);
    setError(null);
    createAutonomousRun({
      objective: objective.trim(),
      objective_template_key: selectedTemplateKey ?? undefined,
      name: name.trim() || undefined,
      description: description.trim() || undefined,
      scope_rules: scopeRules.length > 0 ? scopeRules : undefined,
      // Authoritative selection: send it as soon as the agents loaded (even empty, i.e. the operator
      // disabled every agent). Only omit it when the fetch failed, so the backend can fall back to
      // the tenant defaults rather than silently running with no specialist agents.
      agent_ids: agentsLoaded ? selectedAgentIds : undefined,
      // Per-agent discovery modes (omit when the fetch failed so the backend falls back to tenant
      // defaults). Always carries the orchestrator's own mode (keyed by the ORCHESTRATOR_AGENT_ID
      // sentinel) plus each enabled specialist's mode.
      agent_modes: agentsLoaded
        ? {
            [ORCHESTRATOR_AGENT_ID]: selectedAgentModes[ORCHESTRATOR_AGENT_ID] ?? ORCHESTRATOR_DEFAULT_DISCOVERY_MODE,
            ...Object.fromEntries(selectedAgentIds.map(id => [id, selectedAgentModes[id] ?? SPECIALIST_DEFAULT_DISCOVERY_MODE])),
          }
        : undefined,
      plan_mode: planMode || undefined,
    })
      .then((res) => {
        const runId = res.data.autonomous_run_id;
        const scenarioId = res.data.autonomous_run_scenario_id;
        const simulationId = res.data.autonomous_run_simulation_id;
        return startAutonomousRun(runId).then(() => {
          handleClose();
          // Land on the run's scenario - that is the autonomous cockpit (AI overview, live attack
          // map, always-open reasoning panel, run controls). The scenario is the home of an
          // autonomous run; its single simulation offers the same experience if opened directly.
          if (scenarioId) {
            navigate(`${SCENARIO_BASE_URL}/${scenarioId}`);
          } else if (simulationId) {
            navigate(`${SIMULATION_BASE_URL}/${simulationId}`);
          } else {
            navigate(`/admin/autonomous/${runId}`);
          }
        });
      })
      .catch(() => setError(t('Failed to launch the autonomous run')))
      .finally(() => setSubmitting(false));
  };

  // The autonomous run is driven by the XTM One orchestrator (the AI brain), so
  // the entry point is meaningless without a configured XTM One - hide it exactly
  // like the CTEM Command Center shortcut does, and when agentic AI is disabled.
  const xtmOneReady
    = settings.platform_xtm_one_configured === true
      && settings.filigran_chatbot_ai_cgu_status !== 'disabled';
  if (!featureEnabled || !xtmOneReady) {
    return null;
  }

  const stepLabels = [
    t('Objective'),
    allowCount > 0 ? `${t('Allow list')} (${allowCount})` : t('Allow list'),
    denyCount > 0 ? `${t('Deny list')} (${denyCount})` : t('Deny list'),
  ];
  const lastStep = stepLabels.length - 1;

  return (
    <>
      {variant === 'icon'
        ? (
            // Compact hero action, styled like the Reports icon button next to it but in AI
            // purple. EE is enforced on click (handleOpen), so non-EE users still see it and
            // get the standard EE upsell dialog - matching the CTA button's behaviour.
            <Tooltip title={t('Autonomous attack')}>
              <IconButton
                onClick={handleOpen}
                size="small"
                aria-label={t('Autonomous attack')}
                data-testid="button-autonomous-attack"
                sx={{
                  'color': theme.palette.ai.main,
                  '&:hover': {
                    color: theme.palette.ai.dark,
                    backgroundColor: alpha(theme.palette.ai.main, 0.08),
                  },
                }}
              >
                <AutoAwesome fontSize="small" />
              </IconButton>
            </Tooltip>
          )
        : (
            <Button
              onClick={handleOpen}
              variant="contained"
              size="small"
              data-testid="button-autonomous-attack"
              startIcon={<AutoAwesome />}
              sx={{
                'whiteSpace': 'nowrap',
                'flexShrink': 0,
                // AI purple: this is an XTM One (agentic AI) action, like the CTEM and
                // Ask Ariane buttons - not a generic primary CTA.
                'backgroundColor': theme.palette.ai.main,
                'color': theme.palette.ai.contrastText,
                '&:hover': { backgroundColor: theme.palette.ai.dark },
              }}
            >
              {t('Autonomous attack')}
              {!isEnterpriseEdition && <EEChip />}
            </Button>
          )}
      <Drawer open={open} handleClose={handleClose} title={t('Launch an autonomous attack')}>
        {() => (
          <Stack sx={{ gap: theme.spacing(3) }}>
            <Alert
              severity="info"
              variant="outlined"
              icon={<AutoAwesome fontSize="inherit" />}
              sx={{
                'color': theme.palette.ai.main,
                'borderColor': alpha(theme.palette.ai.main, 0.5),
                'backgroundColor': alpha(theme.palette.ai.main, 0.08),
                '& .MuiAlert-icon': { color: theme.palette.ai.main },
              }}
            >
              {t('An AI orchestrator provisions and drives a real attack path autonomously, adapting in real time. Set an objective, then optionally scope the perimeter with the allow / deny lists - or skip scoping and the AI will ask you which targets are in scope before it attacks anything. Plan first for a dry-run that designs the path without executing, or launch now to run it live.')}
            </Alert>

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

            {activeStep === 0 && (
              <>
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
                    {loadingTemplates && templates.length === 0
                      ? ['s1', 's2', 's3', 's4', 's5', 's6', 's7', 's8', 's9', 's10'].map(skeletonKey => (
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
                      : templates.map((template) => {
                          const isSelected = selectedTemplateKey === template.autonomous_objective_template_key;
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
                                onClick={() => handleSelectTemplate(template)}
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
                    value={objective}
                    onChange={(event) => {
                      setObjective(event.target.value);
                      setSelectedTemplateKey(null);
                    }}
                    label={t('Objective (free text)')}
                    placeholder={t('e.g. Reach the domain controller and prove domain admin from an initial foothold')}
                    multiline
                    minRows={3}
                    fullWidth
                  />
                </Box>

                <Box>
                  <AutonomousAgentsSelector
                    title={t('Agents')}
                    agents={availableAgents}
                    enabledIds={selectedAgentIds}
                    onToggle={toggleAgent}
                    modes={selectedAgentModes}
                    onModeChange={changeAgentMode}
                    orchestrator={{
                      id: ORCHESTRATOR_AGENT_ID,
                      name: t('Autonomous orchestrator'),
                      description: t('Plans and drives the entire attack path, and consults the agents below.'),
                    }}
                    builtinSlug={BUILTIN_AGENT_SLUG}
                    loading={loadingAgents}
                    disabled={submitting}
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
                      value={name}
                      onChange={event => setName(event.target.value)}
                      label={t('Name')}
                      placeholder={t('Auto-generated if left empty')}
                      fullWidth
                    />
                    <TextField
                      value={description}
                      onChange={event => setDescription(event.target.value)}
                      label={t('Description')}
                      placeholder={t('Auto-generated from the objective if left empty')}
                      multiline
                      minRows={2}
                      fullWidth
                    />
                  </Stack>
                </Box>
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
                <ScopeStep mode="ALLOWLIST" selection={allowScope} onChange={setAllowScope} />
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
                <ScopeStep mode="DENYLIST" selection={denyScope} onChange={setDenyScope} />
              </Box>
            )}

            {error && <Alert severity="error">{error}</Alert>}

            <Box sx={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              gap: theme.spacing(1),
            }}
            >
              <Button onClick={handleClose} disabled={submitting}>
                {t('Cancel')}
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
                <Button
                  onClick={() => handleLaunch(true)}
                  variant="outlined"
                  color="warning"
                  disabled={!canLaunch}
                  startIcon={<AutoAwesome />}
                  data-testid="button-autonomous-plan"
                >
                  {t('Plan (dry-run)')}
                </Button>
                <Button
                  onClick={() => handleLaunch(false)}
                  variant="contained"
                  disabled={!canLaunch}
                  startIcon={<AutoAwesome />}
                  data-testid="button-autonomous-launch"
                  sx={{
                    'backgroundColor': theme.palette.ai.main,
                    'color': theme.palette.ai.contrastText,
                    '&:hover': { backgroundColor: theme.palette.ai.dark },
                  }}
                >
                  {t('Launch now')}
                </Button>
              </Box>
            </Box>
          </Stack>
        )}
      </Drawer>
    </>
  );
};

export default AutonomousAttackCreation;
