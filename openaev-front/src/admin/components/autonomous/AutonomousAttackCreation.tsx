import { AutoAwesome } from '@mui/icons-material';
import { Button, IconButton, Tooltip } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, useState } from 'react';
import { useNavigate } from 'react-router';

import { createAutonomousRun, startAutonomousRun } from '../../../actions/autonomous/autonomous-actions';
import { type AutonomousRunCreateInput } from '../../../actions/autonomous/autonomous-types';
import { useFormatter } from '../../../components/i18n';
import { SCENARIO_BASE_URL, SIMULATION_BASE_URL } from '../../../constants/BaseUrls';
import useAuth from '../../../utils/hooks/useAuth';
import useEnterpriseEdition from '../../../utils/hooks/useEnterpriseEdition';
import { isFeatureEnabled } from '../../../utils/utils';
import EEChip from '../common/entreprise_edition/EEChip';
import AutonomousRunConfigDrawer from './AutonomousRunConfigDrawer';

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
 * the {@code INJECT_CHAINING} preview feature (autonomy is a launch mode of chained scenarios, so it
 * shares the chaining flag) and the Enterprise Edition license (every AI feature is EE-only).
 *
 * <p>The configuration itself - objective (template or free text), specialist agents, allow / deny
 * scope, run label, time budget - and its Plan / Launch actions live in the shared
 * {@link AutonomousRunConfigDrawer}, so this component only owns the entry button, the EE gate, and
 * where to land once the run is created.
 */
const AutonomousAttackCreation: FunctionComponent<AutonomousAttackCreationProps> = ({
  presetScopeAssetGroupId,
  variant = 'button',
}) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const navigate = useNavigate();
  const { settings } = useAuth();
  const featureEnabled = isFeatureEnabled('INJECT_CHAINING');
  const {
    isValidated: isEnterpriseEdition,
    openDialog: openEnterpriseEditionDialog,
    setEEFeatureDetectedInfo,
  } = useEnterpriseEdition();

  const [open, setOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

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
    setError(null);
  };

  // Both entry actions create then start the run; the built input already carries plan_mode (true
  // for the dry-run "Plan" action), so a single handler covers both.
  const handleSubmit = (input: AutonomousRunCreateInput) => {
    setSubmitting(true);
    setError(null);
    createAutonomousRun(input)
      .then((res) => {
        const runId = res.data.autonomous_run_id;
        const scenarioId = res.data.autonomous_run_scenario_id;
        const simulationId = res.data.autonomous_run_simulation_id;
        return startAutonomousRun(runId).then(() => {
          handleClose();
          // Land on the run's scenario - that is the autonomous cockpit (AI overview, live attack
          // map, always-open reasoning panel, run controls).
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

  // The autonomous run is driven by the XTM One orchestrator (the AI brain), so the entry point is
  // meaningless without a configured XTM One - hide it exactly like the CTEM Command Center shortcut
  // does, and when agentic AI is disabled.
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
            // Compact hero action, styled like the Reports icon button next to it but in AI purple.
            // EE is enforced on click (handleOpen), so non-EE users still see it and get the standard
            // EE upsell dialog - matching the CTA button's behaviour.
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
                // AI purple: this is an XTM One (agentic AI) action, like the CTEM and Ask Ariane
                // buttons - not a generic primary CTA.
                'backgroundColor': theme.palette.ai.main,
                'color': theme.palette.ai.contrastText,
                '&:hover': { backgroundColor: theme.palette.ai.dark },
              }}
            >
              {t('Autonomous attack')}
              {!isEnterpriseEdition && <EEChip />}
            </Button>
          )}
      <AutonomousRunConfigDrawer
        open={open}
        onClose={handleClose}
        title={t('Launch an autonomous attack')}
        presetScopeAssetGroupId={presetScopeAssetGroupId}
        submitting={submitting}
        error={error}
        onPlan={handleSubmit}
        onLaunch={handleSubmit}
      />
    </>
  );
};

export default AutonomousAttackCreation;
