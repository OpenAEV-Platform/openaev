import { useEffect, useMemo, useState } from 'react';

import { fetchAvailableAgents, fetchDefaultAgents, fetchObjectiveTemplates } from '../../../actions/autonomous/autonomous-actions';
import {
  type AdditionalAgent,
  type AutonomousDiscoveryMode,
  type AutonomousObjectiveTemplate,
  type AutonomousRunCreateInput,
  ORCHESTRATOR_AGENT_ID,
  ORCHESTRATOR_DEFAULT_DISCOVERY_MODE,
  SPECIALIST_DEFAULT_DISCOVERY_MODE,
} from '../../../actions/autonomous/autonomous-types';
import { type WorkflowScopeRuleInput } from '../../../utils/api-types';
import { type ScopeCustomRule } from '../chaining/ScopeForm';

// The license-independent built-in specialist the orchestrator always consults; seeded as a default
// handover in XTM One, so it is shown as always-on and cannot be removed from the per-run picker.
export const BUILTIN_AGENT_SLUG = 'openaev-payload-creator';

// One list (allow or deny) worth of selections, mirroring the state the manual chained-scope editor
// keeps per drawer (ScopeRules). Reused as-is so the AI config offers the exact same picker
// experience - kind tabs, live-searched paginated lists, manual IP/CIDR/hostname input and CSV
// import - split across an Allow-list section and a Deny-list section.
export interface ScopeSelection {
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

// Flattens a per-list selection into the workflow scope-rule inputs the create/plan/launch APIs seed
// onto the run's scenario + simulation workflows (both lists together make the full scope definition).
const selectionToRules = (selection: ScopeSelection, mode: 'ALLOWLIST' | 'DENYLIST'): WorkflowScopeRuleInput[] => [
  ...selection.endpointIds.map(id => toScopeRule(mode, 'ASSET', id)),
  ...selection.assetGroupIds.map(id => toScopeRule(mode, 'ASSET_GROUP', id)),
  ...selection.teamIds.map(id => toScopeRule(mode, 'TEAM', id)),
  ...selection.playerIds.map(id => toScopeRule(mode, 'PLAYER', id)),
  ...selection.customRules.map(rule => toScopeRule(mode, rule.source, rule.value)),
];

// Inverse of selectionToRules: rebuild the per-list allow / deny {@link ScopeSelection}s from a flat
// scope-rule array (a saved config's scope_rules), so the AI builder can pre-fill the exact picker
// state the operator saved. Unknown sources fall back to a manual custom rule.
const scopeRulesToSelections = (
  rules: WorkflowScopeRuleInput[],
): {
  allow: ScopeSelection;
  deny: ScopeSelection;
} => {
  const allow: ScopeSelection = {
    endpointIds: [],
    assetGroupIds: [],
    teamIds: [],
    playerIds: [],
    customRules: [],
  };
  const deny: ScopeSelection = {
    endpointIds: [],
    assetGroupIds: [],
    teamIds: [],
    playerIds: [],
    customRules: [],
  };
  rules.forEach((rule) => {
    const target = rule.workflow_scope_rule_selected_mode === 'DENYLIST' ? deny : allow;
    const value = rule.workflow_scope_rule_value;
    switch (rule.workflow_scope_rule_source) {
      case 'ASSET':
        target.endpointIds.push(value);
        break;
      case 'ASSET_GROUP':
        target.assetGroupIds.push(value);
        break;
      case 'TEAM':
        target.teamIds.push(value);
        break;
      case 'PLAYER':
        target.playerIds.push(value);
        break;
      default:
        target.customRules.push({
          source: rule.workflow_scope_rule_source === 'CSV' ? 'CSV' : 'MANUAL',
          value,
        });
    }
  });
  return {
    allow,
    deny,
  };
};

export interface UseAutonomousRunConfigOptions {
  /** Only load the (templates + agents) galleries while the surface hosting the config is open. */
  open: boolean;
  /** Pre-select an asset group as the allow-list scope (entity "Autonomous attack" entry points). */
  presetScopeAssetGroupId?: string;
  /**
   * Pre-fill the whole configuration from a previously saved input (the scenario AI builder's "Save
   * for later"). When set, its objective / template / label / time budget / scope and its agent
   * selection override the tenant-default prefill so the operator sees exactly what they saved. Keep
   * it stable across renders (store it in state) - it is a hook effect dependency.
   */
  initialInput?: AutonomousRunCreateInput | null;
  /**
   * Seed the free-text objective when nothing else does. Used when launching an already-defined
   * scenario (manually authored or AI-built) in autonomous mode: the default mission is "execute
   * what is already defined, then iterate", so the operator sees a sensible objective rather than a
   * blank field / a template gallery. A saved objective (from {@link initialInput}) still wins; this
   * only fills a blank. Keep it stable across renders - it is a hook effect dependency.
   */
  defaultObjective?: string;
  /**
   * Default value (in hours) for the run's time budget when nothing else pre-fills it. Autonomous
   * LAUNCH (execution) is long-lived, so it keeps the 24h default; the AI builder (planning) is a
   * quick design pass - the server does not even enforce a timeout in plan mode - so its host passes
   * a much smaller default (1h) rather than surfacing a misleading 24h. A saved config's own timeout
   * (from {@link initialInput}) still wins.
   */
  defaultTimeoutHours?: number;
}

/** Fallback time budget (hours) when a host does not override it: autonomous execution is long-lived. */
export const DEFAULT_TIMEOUT_HOURS = 24;

export interface AutonomousRunConfig {
  templates: AutonomousObjectiveTemplate[];
  loadingTemplates: boolean;
  selectedTemplateKey: string | null;
  objective: string;
  setObjective: (value: string) => void;
  name: string;
  setName: (value: string) => void;
  description: string;
  setDescription: (value: string) => void;
  timeoutHours: number;
  setTimeoutHours: (value: number) => void;
  allowScope: ScopeSelection;
  setAllowScope: (value: ScopeSelection) => void;
  denyScope: ScopeSelection;
  setDenyScope: (value: ScopeSelection) => void;
  availableAgents: AdditionalAgent[];
  selectedAgentIds: string[];
  selectedAgentModes: Record<string, string>;
  loadingAgents: boolean;
  agentsLoaded: boolean;
  selectTemplate: (template: AutonomousObjectiveTemplate) => void;
  toggleAgent: (agentId: string, enabled: boolean) => void;
  changeAgentMode: (agentId: string, mode: AutonomousDiscoveryMode) => void;
  allowCount: number;
  denyCount: number;
  canSubmit: boolean;
  reset: () => void;
  /** Build the create/plan/launch payload from the current selection (optionally a dry-run plan). */
  buildInput: (planMode?: boolean) => AutonomousRunCreateInput;
}

/**
 * Owns all of the autonomous-run configuration state (objective + template gallery, specialist
 * agents prefilled from tenant defaults, allow / deny scope, run label, time budget) and knows how
 * to fold it into an {@link AutonomousRunCreateInput}. Shared by every entry point that configures a
 * run - the entity "Autonomous attack" drawer, the scenario "Plan with AI" / autonomous launch, and
 * the "Generate with AI" scenario-creation flow - so they never drift.
 */
export const useAutonomousRunConfig = ({
  open,
  presetScopeAssetGroupId,
  initialInput,
  defaultObjective,
  defaultTimeoutHours = DEFAULT_TIMEOUT_HOURS,
}: UseAutonomousRunConfigOptions): AutonomousRunConfig => {
  const [templates, setTemplates] = useState<AutonomousObjectiveTemplate[]>([]);
  const [loadingTemplates, setLoadingTemplates] = useState(false);
  const [selectedTemplateKey, setSelectedTemplateKey] = useState<string | null>(null);
  const [objective, setObjective] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  // OpenAEV-enforced run timeout, in hours. Autonomous LAUNCH defaults to 24h (vs the 1h chained
  // workflow timeout): recon and human-in-the-loop steps make live runs long-lived. The AI builder
  // (planning) passes a smaller default since plan mode is untimed server-side and short.
  const [timeoutHours, setTimeoutHours] = useState<number>(defaultTimeoutHours);
  const [allowScope, setAllowScope] = useState<ScopeSelection>(EMPTY_SCOPE);
  const [denyScope, setDenyScope] = useState<ScopeSelection>(EMPTY_SCOPE);
  const [availableAgents, setAvailableAgents] = useState<AdditionalAgent[]>([]);
  const [selectedAgentIds, setSelectedAgentIds] = useState<string[]>([]);
  const [selectedAgentModes, setSelectedAgentModes] = useState<Record<string, string>>({});
  const [loadingAgents, setLoadingAgents] = useState(false);
  const [agentsLoaded, setAgentsLoaded] = useState(false);

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
        // A saved config's agent selection wins over the tenant defaults, so the operator sees the
        // agents they saved. `agent_ids: undefined` in the saved input means "was never set" - fall
        // back to defaults; `[]` is an explicit "no agents" and is honoured.
        if (initialInput?.agent_ids !== undefined) {
          setSelectedAgentIds(initialInput.agent_ids ?? []);
          setSelectedAgentModes(initialInput.agent_modes ?? {});
        } else {
          setSelectedAgentIds(defaults.data?.agent_ids ?? []);
          setSelectedAgentModes(defaults.data?.agent_modes ?? {});
        }
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
  // the surface opens, so it lands as an allow-listed asset group exactly like a manual pick.
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

  // Pre-fill the non-agent fields from a saved config when the surface opens (the agent selection is
  // seeded in the agents effect above, once the gallery has loaded). Objective / template / label /
  // time budget / scope are all local state, so they can be set immediately.
  useEffect(() => {
    if (!open) {
      return;
    }
    if (initialInput) {
      // A saved AI config wins: its objective (or the caller's default mission when the saved
      // objective is blank) pre-fills the field, along with the saved template / label / time
      // budget / scope.
      setObjective(initialInput.objective || defaultObjective || '');
      setSelectedTemplateKey(initialInput.objective_template_key ?? null);
      setName(initialInput.name ?? '');
      setDescription(initialInput.description ?? '');
      // A saved timeout wins; otherwise fall back to this host's default (24h launch / 1h planning)
      // rather than leaving whatever a previous open left behind.
      setTimeoutHours(
        initialInput.timeout_seconds && initialInput.timeout_seconds > 0
          ? Math.round(initialInput.timeout_seconds / 3600)
          : defaultTimeoutHours,
      );
      const { allow, deny } = scopeRulesToSelections(initialInput.scope_rules ?? []);
      setAllowScope(allow);
      setDenyScope(deny);
    } else {
      // No saved config: apply this host's default time budget, and (e.g. a manually authored
      // scenario launched autonomously) seed the "execute what is defined, then iterate" mission so
      // the free-text objective is the sensible primary default instead of an empty field.
      setTimeoutHours(defaultTimeoutHours);
      if (defaultObjective) {
        setObjective(defaultObjective);
      }
    }
  }, [open, initialInput, defaultObjective, defaultTimeoutHours]);

  const selectTemplate = (template: AutonomousObjectiveTemplate) => {
    setSelectedTemplateKey(template.autonomous_objective_template_key);
    setObjective(template.autonomous_objective_template_prompt);
  };

  // User-driven edits to the free-text mission mean it is no longer the pre-built template verbatim,
  // so deselect the template (its highlight AND the objective_template_key sent on submit) - the
  // mission the operator typed is now the source of truth. Picking a template goes through
  // selectTemplate, which uses the raw setObjective setter above, so it never trips this.
  const changeObjective = (value: string) => {
    setObjective(value);
    setSelectedTemplateKey(null);
  };

  // Every agent - including the built-in payload creator - is a normal toggle: built-ins are enabled
  // by default (prefilled from the tenant defaults) but can be turned off or replaced for this run.
  const toggleAgent = (agentId: string, enabled: boolean) => {
    setSelectedAgentIds((current) => {
      if (!enabled) {
        return current.filter(id => id !== agentId);
      }
      return current.includes(agentId) ? current : [...current, agentId];
    });
    if (enabled) {
      setSelectedAgentModes(current => (current[agentId]
        ? current
        : {
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

  const reset = () => {
    setSelectedTemplateKey(null);
    setObjective('');
    setName('');
    setDescription('');
    setTimeoutHours(defaultTimeoutHours);
    setAllowScope(EMPTY_SCOPE);
    setDenyScope(EMPTY_SCOPE);
    setAvailableAgents([]);
    setSelectedAgentIds([]);
    setSelectedAgentModes({});
    setAgentsLoaded(false);
  };

  const buildInput = (planMode = false): AutonomousRunCreateInput => ({
    objective: objective.trim(),
    objective_template_key: selectedTemplateKey ?? undefined,
    name: name.trim() || undefined,
    description: description.trim() || undefined,
    scope_rules: scopeRules.length > 0 ? scopeRules : undefined,
    // Authoritative selection: send it as soon as the agents loaded (even empty, i.e. the operator
    // disabled every agent). Only omit it when the fetch failed, so the backend can fall back to
    // the tenant defaults rather than silently running with no specialist agents.
    agent_ids: agentsLoaded ? selectedAgentIds : undefined,
    agent_modes: agentsLoaded
      ? {
          [ORCHESTRATOR_AGENT_ID]: selectedAgentModes[ORCHESTRATOR_AGENT_ID] ?? ORCHESTRATOR_DEFAULT_DISCOVERY_MODE,
          ...Object.fromEntries(selectedAgentIds.map(id => [id, selectedAgentModes[id] ?? SPECIALIST_DEFAULT_DISCOVERY_MODE])),
        }
      : undefined,
    plan_mode: planMode || undefined,
    // OpenAEV-enforced run deadline (seconds). Clamp to the advertised 1h-720h range (the HTML
    // min/max only guard the spinner, not typed input); ignored server-side in plan/dry-run mode.
    timeout_seconds: Math.min(720 * 3600, Math.max(3600, Math.round(timeoutHours * 3600))),
  });

  return {
    templates,
    loadingTemplates,
    selectedTemplateKey,
    objective,
    setObjective: changeObjective,
    name,
    setName,
    description,
    setDescription,
    timeoutHours,
    setTimeoutHours,
    allowScope,
    setAllowScope,
    denyScope,
    setDenyScope,
    availableAgents,
    selectedAgentIds,
    selectedAgentModes,
    loadingAgents,
    agentsLoaded,
    selectTemplate,
    toggleAgent,
    changeAgentMode,
    allowCount: scopeSelectionCount(allowScope),
    denyCount: scopeSelectionCount(denyScope),
    canSubmit: objective.trim().length > 0,
    reset,
    buildInput,
  };
};
