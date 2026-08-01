import {
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
import { Alert, Box, Button, Card, CardActionArea, IconButton, Skeleton, Stack, TextField, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useState } from 'react';
import { useNavigate } from 'react-router';

import { createAutonomousRun, fetchObjectiveTemplates, startAutonomousRun } from '../../../actions/autonomous/autonomous-actions';
import { type AutonomousObjectiveTemplate, type AutonomousScopeTarget } from '../../../actions/autonomous/autonomous-types';
import Drawer from '../../../components/common/Drawer';
import { useFormatter } from '../../../components/i18n';
import { SCENARIO_BASE_URL, SIMULATION_BASE_URL } from '../../../constants/BaseUrls';
import useAuth from '../../../utils/hooks/useAuth';
import useEnterpriseEdition from '../../../utils/hooks/useEnterpriseEdition';
import { isFeatureEnabled } from '../../../utils/utils';
import EEChip from '../common/entreprise_edition/EEChip';
import ScopeTargetsField from './ScopeTargetsField';

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

// Objective templates whose target is a human audience (phishing, human credential harvesting).
// For these the run scope that matters is a TEAM (an inject can only be delivered to a team, never
// to a bare person), so the drawer spotlights the team picker over the asset-group one.
const IDENTITY_OBJECTIVE_KEYS = new Set(['phishing-to-access', 'harvest-credentials']);

export interface AutonomousAttackCreationProps {
  /** Pre-select an asset group as the run scope (used by entity "Autonomous attack" entries). */
  presetScopeAssetGroupId?: string;
  /** Display name for the preset scope, so the picker shows it before the list loads. */
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
 * is EE-only). The drawer is intentionally minimal - pick an objective (template or free text),
 * optionally label the run, then launch. There is nothing to build by hand: the attack-path
 * substrate is auto-provisioned server-side and the AI orchestrator builds and executes the path.
 *
 * On the asset-group hero it pre-selects the group as the run scope via {@code presetScopeAssetGroupId}.
 */
const AutonomousAttackCreation: FunctionComponent<AutonomousAttackCreationProps> = ({
  presetScopeAssetGroupId,
  presetScopeAssetGroupName,
  variant = 'button',
}) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const navigate = useNavigate();
  const { settings } = useAuth();
  const featureEnabled = isFeatureEnabled('AUTONOMOUS_ATTACK_PATH');
  const {
    isValidated: isEnterpriseEdition,
    openDialog: openEnterpriseEditionDialog,
    setEEFeatureDetectedInfo,
  } = useEnterpriseEdition();

  const [open, setOpen] = useState(false);
  const [templates, setTemplates] = useState<AutonomousObjectiveTemplate[]>([]);
  const [loadingTemplates, setLoadingTemplates] = useState(false);
  const [selectedTemplateKey, setSelectedTemplateKey] = useState<string | null>(null);
  const [objective, setObjective] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [scope, setScope] = useState<AutonomousScopeTarget[]>([]);
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

  // Apply the preset scope (e.g. an asset group's "Autonomous attack") whenever the drawer opens.
  useEffect(() => {
    if (open && presetScopeAssetGroupId) {
      setScope(current => (current.some(target => target.type === 'ASSETS_GROUPS' && target.id === presetScopeAssetGroupId)
        ? current
        : [...current, {
            type: 'ASSETS_GROUPS',
            id: presetScopeAssetGroupId,
            name: presetScopeAssetGroupName ?? presetScopeAssetGroupId,
          }]));
    }
  }, [open, presetScopeAssetGroupId, presetScopeAssetGroupName]);

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
    setSelectedTemplateKey(null);
    setObjective('');
    setName('');
    setDescription('');
    setScope([]);
    setError(null);
  };

  const handleSelectTemplate = (template: AutonomousObjectiveTemplate) => {
    setSelectedTemplateKey(template.autonomous_objective_template_key);
    setObjective(template.autonomous_objective_template_prompt);
  };

  const canLaunch = objective.trim().length > 0 && !submitting;

  // Target-dependent objectives (e.g. web-app exploitation, crown-jewel) need a specific target.
  // Pre-selecting a scope here is always optional - if left empty, the orchestrator resolves the
  // scope on its first cycle and parks to ask you one precise question when the target is ambiguous.
  const selectedTemplate = templates.find(
    template => template.autonomous_objective_template_key === selectedTemplateKey,
  );
  const isTargetDependent = selectedTemplate?.autonomous_objective_template_scope_mode === 'target';

  // Identity-targeted objectives deliver to PEOPLE, and an inject can only reach a team - so the
  // meaningful scope for these is an audience (teams / persons). The unified scope picker exposes
  // all four kinds; this only decides which kind we spotlight first.
  const isIdentityObjective = IDENTITY_OBJECTIVE_KEYS.has(selectedTemplateKey ?? '');

  const handleLaunch = () => {
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
      scope: scope.length > 0 ? scope : undefined,
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
              {t('An AI orchestrator provisions and drives a real attack path autonomously, adapting in real time. Just set an objective - you never build anything by hand. You can steer it live and it will ask for input only when stuck.')}
            </Alert>

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
                                <Typography variant="subtitle2" sx={{ fontWeight: 'bold', fontSize: '0.8125rem' }}>
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
              <Typography variant="h2" gutterBottom>
                {t('Scope (optional)')}
              </Typography>
              <ScopeTargetsField
                value={scope}
                onChange={setScope}
                emphasisType={isIdentityObjective ? 'TEAMS' : undefined}
              />
              <Typography
                variant="caption"
                color={(isTargetDependent || isIdentityObjective) ? 'warning.main' : 'text.secondary'}
                sx={{
                  display: 'block',
                  marginTop: theme.spacing(1),
                }}
              >
                {isIdentityObjective
                  ? t('This objective targets people, so it needs an audience. Add the teams or persons that will receive the campaign (a person is targeted through a team - the AI wraps them for you), or leave it empty and the AI will ask you who is in scope before sending anything.')
                  : isTargetDependent
                    ? t('This objective targets a specific perimeter. Add the assets, asset groups, teams or persons to focus on, or leave it empty - the AI will enumerate candidates and ask you which are in scope before attacking.')
                    : t('Optional. Mix any assets, asset groups, teams and persons to define the perimeter; leave it empty to let the AI resolve the scope. The AI uses whichever targets the technique needs.')}
              </Typography>
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

            {error && <Alert severity="error">{error}</Alert>}

            <Box sx={{
              display: 'flex',
              justifyContent: 'flex-end',
              gap: theme.spacing(1),
            }}
            >
              <Button onClick={handleClose} disabled={submitting}>
                {t('Cancel')}
              </Button>
              <Button
                onClick={handleLaunch}
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
                {t('Launch')}
              </Button>
            </Box>
          </Stack>
        )}
      </Drawer>
    </>
  );
};

export default AutonomousAttackCreation;
