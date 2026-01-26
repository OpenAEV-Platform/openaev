import { Typography } from '@mui/material';
import { useContext, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchTargetResult } from '../../../../../actions/atomic_testings/atomic-testing-actions';
import Paper from '../../../../../components/common/Paper';
import { useFormatter } from '../../../../../components/i18n';
import type { InjectResultOverviewOutput, InjectTarget } from '../../../../../utils/api-types';
import { isAgent, isAssetGroups, isPlayer, isTeam } from '../../../../../utils/target/TargetUtils';
import { type ExpectationResultType, ExpectationType, type InjectExpectationsStore } from '../../../common/injects/expectations/Expectation';
import ExecutionStatusDetail from '../../../common/injects/status/ExecutionStatusDetail';
import TerminalViewTab from '../../../common/injects/status/traces/TerminalViewTab';
import TabbedView, { type TabConfig } from '../../../settings/groups/grants/ui/TabbedView';
import { InjectResultOverviewOutputContext, type InjectResultOverviewOutputContextType } from '../../InjectResultOverviewOutputContext';
import InjectExpectationProvider from '../context/InjectExpectationProvider';
import InjectExpectationCard from './InjectExpectationCard';
import TargetResultsReactFlow from './TargetResultsReactFlow';

interface Props {
  inject: InjectResultOverviewOutput;
  target: InjectTarget;
  isAgentless: boolean;
}

const useStyles = makeStyles()(theme => ({
  container: {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr 1fr',
    justifyItems: 'center',
  },
  paddingTop: { paddingTop: theme.spacing(2) },
  gap: {
    display: 'flex',
    flexDirection: 'column',
    gap: theme.spacing(1),
  },
}));

const TargetResultsDetail = ({ inject, target, isAgentless }: Props) => {
  const { classes } = useStyles();
  const { t } = useFormatter();

  const [sortedGroupedTargetResults, setSortedGroupedTargetResults] = useState<Record<string, InjectExpectationsStore[]>>({});

  const [searchParams, setSearchParams] = useSearchParams();
  const openIdParams = searchParams.get('expectation_id');

  const [activeTab, setActiveTab] = useState<string | null>(null);

  const { injectResultOverviewOutput } = useContext<InjectResultOverviewOutputContextType>(InjectResultOverviewOutputContext);

  const transformToSortedGroupedResults = (results: InjectExpectationsStore[]) => {
    const groupedByType: Record<string, InjectExpectationsStore[]> = {};
    results.forEach((result) => {
      const type = result.inject_expectation_type;
      if (!groupedByType[type]) {
        groupedByType[type] = [];
      }
      groupedByType[type].push(result);
    });

    const sortedGroupedResults: Record<string, InjectExpectationsStore[]> = {};
    Object.keys(groupedByType)
      .toSorted((a, b) => Object.keys(ExpectationType).indexOf(a as ExpectationResultType) - Object.keys(ExpectationType).indexOf(b as ExpectationResultType))
      .forEach((key) => {
        sortedGroupedResults[key] = groupedByType[key].toSorted((a, b) => {
          if (a.inject_expectation_name && b.inject_expectation_name) {
            return a.inject_expectation_name.localeCompare(b.inject_expectation_name);
          }
          if (a.inject_expectation_name && !b.inject_expectation_name) {
            return -1; // a comes before b
          }
          if (!a.inject_expectation_name && b.inject_expectation_name) {
            return 1; // b comes before a
          }
          return a.inject_expectation_id.localeCompare(b.inject_expectation_id);
        });
      });
    return sortedGroupedResults;
  };

  useEffect(() => {
    fetchTargetResult(inject.inject_id, target.target_id!, target.target_type!)
      .then((result: { data: InjectExpectationsStore[] }) => {
        setSortedGroupedTargetResults(transformToSortedGroupedResults(result.data ?? []));
      });
  }, [injectResultOverviewOutput, target]);

  useEffect(() => {
    if (!openIdParams || !sortedGroupedTargetResults) return;

    const activeTabIndex: string = Object.values(sortedGroupedTargetResults)
      .map(results => results.find(r => r.inject_expectation_id === openIdParams))
      .filter(res => !!res)[0]?.inject_expectation_type.toString();

    if (!activeTabIndex) return;

    setActiveTab(activeTabIndex);
    searchParams.delete('open');
    setSearchParams(searchParams, { replace: true });
  }, [openIdParams, sortedGroupedTargetResults]);

  const tabs: TabConfig[] = [];
  Object.entries(sortedGroupedTargetResults).forEach(([type, expectationResults]) => (
    tabs.push({
      key: type,
      label: t(`TYPE_${type}`),
      component: (
        expectationResults.map(expectationResult => (
          <InjectExpectationProvider key={expectationResult.inject_expectation_id} inject={inject}>
            <InjectExpectationCard
              injectExpectation={expectationResult}
              inject={inject}
              isAgentless={isAgentless}
              target={target}
            />
          </InjectExpectationProvider>
        ))
      ),
    })
  ));
  if (!isAssetGroups(target)) {
    tabs.push({
      key: 'execution',
      label: 'Execution',
      component: (
        <ExecutionStatusDetail
          target={{
            id: target.target_id,
            name: target.target_name,
            targetType: target.target_type,
            platformType: target.target_subtype,
          }}
          injectId={inject.inject_id}
        />
      ),
    });
    if (!isTeam(target) && !isPlayer(target)) {
      tabs.push({
        key: 'terminal-view',
        label: t('Terminal view'),
        component: (
          <TerminalViewTab injectId={inject.inject_id} target={target} forceExpanded={isAgent(target)} />
        ),
      });
    }
  }

  return (
    <Paper>
      <div className={classes.container}>
        <Typography sx={{ justifySelf: 'center' }} variant="h3" gutterBottom>{t('Name')}</Typography>
        <Typography variant="h3" gutterBottom>{t('Type')}</Typography>
        <Typography sx={{ justifySelf: 'center' }} variant="h3" gutterBottom>{t('Platform')}</Typography>
      </div>
      <div className={classes.container}>
        <Typography sx={{ justifySelf: 'center' }}>{target.target_name}</Typography>
        <Typography>{target.target_type}</Typography>
        <Typography sx={{ justifySelf: 'center' }}>{target.target_subtype ?? t('N/A')}</Typography>
      </div>

      <TargetResultsReactFlow
        className={`${classes.paddingTop} ${classes.gap}`}
        injectStatusName={injectResultOverviewOutput?.inject_status?.status_name}
        targetResultsByType={sortedGroupedTargetResults}
        lastExecutionStartDate={injectResultOverviewOutput?.inject_status?.tracking_sent_date || ''}
        lastExecutionEndDate={injectResultOverviewOutput?.inject_status?.tracking_end_date || ''}
      />

      <TabbedView tabs={tabs} externalCurrentTab={activeTab} notifyTabChange={setActiveTab} />
    </Paper>
  );
};

export default TargetResultsDetail;
