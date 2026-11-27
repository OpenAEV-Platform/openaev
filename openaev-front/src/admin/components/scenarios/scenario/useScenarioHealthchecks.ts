import { useEffect, useState } from 'react';

import { type AgentHelper } from '../../../../actions/agents/agent-helper';
import { type WorkflowConfigurationHelper } from '../../../../actions/chaining/workflow-helper';
import { type CollectorHelper } from '../../../../actions/collectors/collector-helper';
import { type LoggedHelper } from '../../../../actions/helper';
import { type InjectHelper } from '../../../../actions/injects/inject-helper';
import { searchScenarioHealthcheks } from '../../../../actions/scenarios/scenario-actions';
import { type ScenariosHelper } from '../../../../actions/scenarios/scenario-helper';
import { useHelper } from '../../../../store';
import { type Agent, type HealthCheck, type Inject, type Scenario } from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';

// The date of the last change of any of the injects, empty when there is none.
export const latestInjectUpdate = (injects: Inject[]) => injects.reduce(
  (latest, inject) => (inject.inject_updated_at > latest ? inject.inject_updated_at : latest),
  '',
);

// The healthchecks of a scenario, read once for the header and the overview. The store hands out new
// objects on unrelated updates, so they are read again only when a value they depend on changes.
const useScenarioHealthchecks = (scenarioId: Scenario['scenario_id']) => {
  const [healthchecks, setHealthchecks] = useState<HealthCheck[]>([]);
  const { scenario, settings, injects, collectors, agents } = useHelper((helper: ScenariosHelper & LoggedHelper & InjectHelper & CollectorHelper & AgentHelper) => ({
    scenario: helper.getScenario(scenarioId),
    settings: helper.getPlatformSettings(),
    injects: helper.getScenarioInjects(scenarioId),
    collectors: helper.getExistingCollectors(),
    agents: helper.getAgents(),
  }));
  const scenarioWorkflowId = (scenario as unknown as Record<string, unknown>).scenario_workflow_id as string | undefined;
  const { workflowConfiguration } = useHelper((helper: WorkflowConfigurationHelper) => ({
    workflowConfiguration: scenarioWorkflowId
      ? helper.getWorkflowConfiguration(scenarioWorkflowId)
      : undefined,
  }));

  const injectAssetIds = new Set(injects.flatMap((inject: Inject) => inject.inject_assets ?? []));
  const activeAgentsCount = agents.filter((agent: Agent) => agent.agent_active && injectAssetIds.has(agent.agent_asset)).length;
  const injectsUpdatedAt = latestInjectUpdate(injects);
  const workflowConfigurationKey = JSON.stringify(workflowConfiguration ?? null);

  useEffect(() => {
    let stale = false;
    const refreshHealthchecks = () => {
      searchScenarioHealthcheks(scenarioId).then((result: { data: HealthCheck[] }) => {
        if (!stale) setHealthchecks(result.data);
      });
    };
    refreshHealthchecks();
    // Step changes in the Logic tab touch none of the dependencies below: it signals them instead.
    const subscription = MESSAGING$.refreshHealthchecks.subscribe(refreshHealthchecks);
    return () => {
      stale = true;
      subscription.unsubscribe();
    };
  }, [
    scenarioId,
    scenario.scenario_updated_at,
    settings?.smtp_service_available,
    settings?.imap_service_available,
    injects.length,
    injectsUpdatedAt,
    collectors.length,
    activeAgentsCount,
    workflowConfigurationKey,
  ]);

  return healthchecks;
};

export default useScenarioHealthchecks;
