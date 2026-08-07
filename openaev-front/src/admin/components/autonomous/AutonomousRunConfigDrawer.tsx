import { useEffect } from 'react';

import { type AutonomousRunCreateInput } from '../../../actions/autonomous/autonomous-types';
import Drawer from '../../../components/common/Drawer';
import { useFormatter } from '../../../components/i18n';
import { AutonomousRunConfigPanel } from './AutonomousRunConfigFields';
import { useAutonomousRunConfig } from './useAutonomousRunConfig';

interface AutonomousRunConfigDrawerProps {
  open: boolean;
  onClose: () => void;
  title: string;
  /** AI-accent info banner shown at the top; a sensible default is used when omitted. */
  infoText?: string;
  /** Pre-select an asset group as the allow-list scope (entity "Autonomous attack" entry points). */
  presetScopeAssetGroupId?: string;
  /** Pre-fill the whole config from a saved input (scenario AI builder's "Save for later"). */
  initialInput?: AutonomousRunCreateInput | null;
  submitting?: boolean;
  error?: string | null;
  /** Show the "Save for later" action (persist config, start nothing). Defaults to false. */
  showSave?: boolean;
  /** Show the "Plan (dry-run)" action (author-scenario / design-only). Defaults to true. */
  showPlan?: boolean;
  /** Show the "Launch now" action (live, executing run). Defaults to true. */
  showLaunch?: boolean;
  saveLabel?: string;
  planLabel?: string;
  launchLabel?: string;
  onSave?: (input: AutonomousRunCreateInput) => void;
  onPlan?: (input: AutonomousRunCreateInput) => void;
  onLaunch?: (input: AutonomousRunCreateInput) => void;
}

/**
 * The shared autonomous-run configuration drawer: {@link AutonomousRunConfigPanel} (AI banner + full
 * config body + Plan / Launch footer) wrapped in a {@link Drawer}. Every entry point that configures
 * a run from an existing surface - the entity "Autonomous attack" action and the scenario "Plan with
 * AI" / autonomous launch - renders through this so they can never drift on what an autonomous run
 * can be configured with.
 */
const AutonomousRunConfigDrawer = ({
  open,
  onClose,
  title,
  infoText,
  presetScopeAssetGroupId,
  initialInput,
  submitting = false,
  error,
  showSave = false,
  showPlan = true,
  showLaunch = true,
  saveLabel,
  planLabel,
  launchLabel,
  onSave,
  onPlan,
  onLaunch,
}: AutonomousRunConfigDrawerProps) => {
  const { t } = useFormatter();
  const config = useAutonomousRunConfig({
    open,
    presetScopeAssetGroupId,
    initialInput,
  });

  // Clear the selection every time the drawer closes so a fresh open starts clean (and a preset
  // scope re-applies on the next open). Only react to open toggles; the hook object is fresh each
  // render, so listing it would loop.
  useEffect(() => {
    if (!open) {
      config.reset();
    }
  }, [open]);

  return (
    <Drawer open={open} handleClose={onClose} title={title}>
      {() => (
        <AutonomousRunConfigPanel
          config={config}
          submitting={submitting}
          error={error}
          infoText={infoText
            ?? t('An AI orchestrator designs and (optionally) drives a real attack path, adapting in real time. Set an objective, pick the specialist agents it may consult, and optionally scope the perimeter with the allow / deny lists - or skip scoping and the AI will ask you which targets are in scope. Plan for a dry-run that only designs the path, or launch now to run it live.')}
          showSave={showSave}
          showPlan={showPlan}
          showLaunch={showLaunch}
          saveLabel={saveLabel}
          planLabel={planLabel}
          launchLabel={launchLabel}
          onSave={onSave}
          onPlan={onPlan}
          onLaunch={onLaunch}
          onCancel={onClose}
        />
      )}
    </Drawer>
  );
};

export default AutonomousRunConfigDrawer;
