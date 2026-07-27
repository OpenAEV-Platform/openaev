import { useMemo, useState } from 'react';

import { fetchTargetResultAssetWithAgents } from '../../../../../actions/atomic_testings/atomic-testing-actions';
import { type InjectHelper } from '../../../../../actions/injects/inject-helper';
import Loader from '../../../../../components/Loader';
import { useHelper } from '../../../../../store';
import type {
  InjectExpectationAgentOutput,
  InjectResultOverviewOutput,
  InjectTarget,
} from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import type { InjectExpectationsStore } from '../../../common/injects/expectations/Expectation';
import InjectExpectationResultList, { type AgentResultBreakdownEntry } from './InjectExpectationResultList';

interface Props {
  inject: InjectResultOverviewOutput;
  injectExpectation: InjectExpectationsStore;
  expectationType: string;
  target: InjectTarget;
}

const InjectExpectationAggregatedAgentsView = ({ inject, injectExpectation, expectationType, target }: Props) => {
  const dispatch = useAppDispatch();
  const [loading, setLoading] = useState(false);

  useDataLoader(() => {
    setLoading(true);
    dispatch(fetchTargetResultAssetWithAgents(inject.inject_id, target.target_id, expectationType)).finally(() => setLoading(false));
  });

  const { injectExpectationsWithAgents } = useHelper((helper: InjectHelper) =>
    ({ injectExpectationsWithAgents: helper.getInjectExpectationsByAssetAndInject(target.target_id, inject.inject_id, expectationType) }));

  // Roll the per-agent results up per security-platform source so the aggregated
  // endpoint row can surface the per-agent breakdown in an "i" tooltip instead of
  // rendering a heavy expandable table for every agent of the endpoint.
  const agentBreakdownBySource = useMemo(() => {
    const map: Record<string, AgentResultBreakdownEntry[]> = {};
    (injectExpectationsWithAgents ?? []).forEach((agentExpectation: InjectExpectationAgentOutput) => {
      if (!agentExpectation.inject_expectation_agent) return;
      const agentName = agentExpectation.inject_expectation_agent_name ?? '-';
      (agentExpectation.inject_expectation_results ?? []).forEach((result) => {
        const key = result.sourceId ?? result.sourceName ?? '';
        if (!key) return;
        (map[key] ??= []).push({
          agentName,
          result: result.result,
          score: result.score,
          date: result.date,
        });
      });
    });
    return map;
  }, [injectExpectationsWithAgents]);

  if (loading) {
    return <Loader variant="inElement" />;
  }

  const results = injectExpectation.inject_expectation_results ?? [];
  if (results.length === 0) {
    return null;
  }

  return (
    <InjectExpectationResultList
      injectExpectation={injectExpectation}
      injectExpectationResults={results}
      injectExpectationAgent={undefined}
      injectorContractPayload={inject.inject_injector_contract?.injector_contract_payload}
      injectType={inject.inject_type}
      agentBreakdownBySource={agentBreakdownBySource}
      aggregateAgentAlerts
    />
  );
};

export default InjectExpectationAggregatedAgentsView;
