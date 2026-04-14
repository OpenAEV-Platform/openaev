import { Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useEffect, useState } from 'react';

import { fetchTargetResultAssetWithAgents } from '../../../../../actions/atomic_testings/atomic-testing-actions';
import ExpandableSection from '../../../../../components/common/ExpandableSection';
import { useFormatter } from '../../../../../components/i18n';
import ItemStatus from '../../../../../components/ItemStatus';
import Loader from '../../../../../components/Loader';
import type {
  InjectExpectationAgentOutput,
  InjectResultOverviewOutput,
  InjectTarget,
} from '../../../../../utils/api-types';
import { computeInjectExpectationLabel } from '../../../../../utils/statusUtils';
import type { InjectExpectationsStore } from '../../../common/injects/expectations/Expectation';
import InjectExpectationResultList from './InjectExpectationResultList';

interface Props {
  inject: InjectResultOverviewOutput;
  expectationType: string;
  target: InjectTarget;
}

const InjectExpectationAggregatedAgentsView = ({ inject, expectationType, target }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const [loading, setLoading] = useState(true);
  const [injectExpectationsWithAgents, setInjectExpectationsWithAgents] = useState<InjectExpectationAgentOutput[]>([]);

  useEffect(() => {
    let active = true;
    setLoading(true);
    fetchTargetResultAssetWithAgents(inject.inject_id, target.target_id, expectationType)
      .then((result: { data: InjectExpectationAgentOutput[] }) => {
        if (active) {
          setInjectExpectationsWithAgents(result.data ?? []);
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [inject.inject_id, target.target_id, expectationType]);

  if (loading) {
    return <Loader />;
  }

  return (
    <>
      {!loading && injectExpectationsWithAgents && injectExpectationsWithAgents.length > 0 && (
        <>
          {injectExpectationsWithAgents.map((injectExpectationAgent: InjectExpectationAgentOutput) => {
            const statusResult = computeInjectExpectationLabel(injectExpectationAgent.inject_expectation_status, injectExpectationAgent.inject_expectation_type);
            const header = (
              <>
                <Typography gutterBottom sx={{ mr: theme.spacing(1.5) }}>
                  {injectExpectationAgent.inject_expectation_agent_name}
                </Typography>
                <ItemStatus label={t(`${statusResult}`)} status={injectExpectationAgent.inject_expectation_status} />
              </>
            );
            return injectExpectationAgent?.inject_expectation_status !== 'PENDING' && injectExpectationAgent?.inject_expectation_agent
              && (
                <Paper
                  variant="outlined"
                  style={{
                    padding: theme.spacing(2, 0),
                    margin: theme.spacing(2, 0),
                  }}
                >
                  <ExpandableSection
                    forceExpanded={false}
                    header={header}
                    key={injectExpectationAgent.inject_expectation_id}
                  >
                    <div style={{ margin: theme.spacing(0, 2) }}>
                      <InjectExpectationResultList
                        injectExpectation={injectExpectationAgent as InjectExpectationsStore}
                        injectExpectationResults={injectExpectationAgent.inject_expectation_results ?? []}
                        injectExpectationAgent={injectExpectationAgent.inject_expectation_agent}
                        injectorContractPayload={inject.inject_injector_contract?.injector_contract_payload}
                        injectType={inject.inject_type}
                      />
                    </div>
                  </ExpandableSection>
                </Paper>
              );
          })}
        </>
      )}
    </>
  );
};

export default InjectExpectationAggregatedAgentsView;
