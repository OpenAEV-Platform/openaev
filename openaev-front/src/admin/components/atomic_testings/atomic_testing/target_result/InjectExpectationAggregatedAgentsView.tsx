import { Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useState } from 'react';

import { fetchTargetResultAssetWithAgents } from '../../../../../actions/atomic_testings/atomic-testing-actions';
import { type InjectHelper } from '../../../../../actions/injects/inject-helper';
import ExpandableSection from '../../../../../components/common/ExpandableSection';
import { useFormatter } from '../../../../../components/i18n';
import ItemStatus from '../../../../../components/ItemStatus';
import Loader from '../../../../../components/Loader';
import { useHelper } from '../../../../../store';
import type {
  InjectExpectationAgentOutput,
  InjectResultOverviewOutput,
  InjectTarget,
} from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { computeInjectExpectationLabel } from '../../../../../utils/statusUtils';
import type { InjectExpectationsStore } from '../../../common/injects/expectations/Expectation';
import InjectExpectationResultList from './InjectExpectationResultList';

interface Props {
  inject: InjectResultOverviewOutput;
  expectationType: string;
  target: InjectTarget;
}

const InjectExpectationAggregatedAgentsView = ({ inject, expectationType, target }: Props) => {
  const dispatch = useAppDispatch();
  const { t } = useFormatter();
  const theme = useTheme();
  const [loading, setLoading] = useState(false);

  useDataLoader(() => {
    setLoading(true);
    dispatch(fetchTargetResultAssetWithAgents(inject.inject_id, target.target_id, expectationType)).finally(() => setLoading(false));
  });

  const { injectExpectationsWithAgents } = useHelper((helper: InjectHelper) =>
    ({ injectExpectationsWithAgents: helper.getInjectExpectationsByAsset(target.target_id, expectationType) }));

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
