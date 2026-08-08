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
  /** Seed the free-text objective when nothing else does (launching an already-defined scenario:
   *  "execute what is defined, then iterate"). A saved objective still wins. */
  defaultObjective?: string;
  /** Demote the objective-template gallery into a collapsed accordion and lead with the free-text
   *  mission (already-defined scenario launched in autonomous mode). */
  demoteTemplates?: boolean;
  /** Default time budget (hours) when nothing pre-fills it: 24h for a live launch, a smaller value
   *  for the AI builder (planning is a quick, untimed design pass). Omit to keep the 24h default. */
  defaultTimeoutHours?: number;
  /** The drawer hosts the AI builder (a plan-authoring pass), not a live launch. Plan mode is
   *  untimed server-side, so the time budget always shows the default and is omitted from the
   *  payload (never persisted as a stale value). Defaults to false (live launch). */
  planMode?: boolean;
  /**
   * Short note rendered just above the time-budget field. The autonomous launch drawer uses it to
   * warn that the default 24h run budget overrides the scenario's own configured "Simulation time
   * out" - shown only when they actually differ (omit it when they already match). Ignored when the
   * time budget is hidden (plan mode).
   */
  timeBudgetNote?: string;
  submitting?: boolean;
  error?: string | null;
  /** Show the "Save for later" action (persist config, start nothing). Defaults to false. */
  showSave?: boolean;
  /** Show the "Launch now" action (live, executing run). Defaults to true. */
  showLaunch?: boolean;
  saveLabel?: string;
  launchLabel?: string;
  onSave?: (input: AutonomousRunCreateInput) => void;
  onLaunch?: (input: AutonomousRunCreateInput) => void;
}

/**
 * The shared autonomous-run configuration drawer: {@link AutonomousRunConfigPanel} (AI banner + full
 * config body + Save / Launch footer) wrapped in a {@link Drawer}. The scenario "Build with AI"
 * (Save + Build) and autonomous launch both render through this so they can never drift on what an
 * autonomous run can be configured with.
 */
const AutonomousRunConfigDrawer = ({
  open,
  onClose,
  title,
  infoText,
  presetScopeAssetGroupId,
  initialInput,
  defaultObjective,
  demoteTemplates,
  defaultTimeoutHours,
  planMode,
  timeBudgetNote,
  submitting = false,
  error,
  showSave = false,
  showLaunch = true,
  saveLabel,
  launchLabel,
  onSave,
  onLaunch,
}: AutonomousRunConfigDrawerProps) => {
  const { t } = useFormatter();
  const config = useAutonomousRunConfig({
    open,
    presetScopeAssetGroupId,
    initialInput,
    defaultObjective,
    defaultTimeoutHours,
    isPlanMode: planMode,
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
          demoteTemplates={demoteTemplates}
          // Plan mode (AI builder) is untimed server-side, so the time budget is hidden entirely
          // rather than shown with a meaningless default.
          hideTimeBudget={planMode}
          timeBudgetNote={planMode ? undefined : timeBudgetNote}
          infoText={infoText
            ?? t('In autonomous mode an AI orchestrator drives the run live and adapts in real time - reacting to findings, adding steps and consulting specialist agents to pursue the objective. Set an objective, pick the specialist agents it may consult, and optionally scope the perimeter with the allow / deny lists - or skip scoping and the AI will ask you which targets are in scope.')}
          showSave={showSave}
          showLaunch={showLaunch}
          saveLabel={saveLabel}
          launchLabel={launchLabel}
          onSave={onSave}
          onLaunch={onLaunch}
          onCancel={onClose}
        />
      )}
    </Drawer>
  );
};

export default AutonomousRunConfigDrawer;
