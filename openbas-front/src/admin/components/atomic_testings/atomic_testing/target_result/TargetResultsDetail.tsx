import { Tab, Tabs, Typography } from '@mui/material';
import { type SyntheticEvent, useContext, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchTargetResultMerged } from '../../../../../actions/atomic_testings/atomic-testing-actions';
import Paper from '../../../../../components/common/Paper';
import { useFormatter } from '../../../../../components/i18n';
import type { InjectResultOverviewOutput, InjectTarget } from '../../../../../utils/api-types';
import {
  type ExpectationResultType,
  ExpectationType,
  type InjectExpectationsStore,
} from '../../../common/injects/expectations/Expectation';
import ExecutionStatusDetail from '../../../common/injects/status/ExecutionStatusDetail';
import {
  InjectResultOverviewOutputContext,
  type InjectResultOverviewOutputContextType,
} from '../../InjectResultOverviewOutputContext';
import InjectExpectationCard from './InjectExpectationCard';
import TargetResultsReactFlow from './TargetResultsReactFlow';

interface Props {
  inject: InjectResultOverviewOutput;
  target: InjectTarget;
}

const useStyles = makeStyles()(theme => ({
  container: {
    display: 'grid',
    gridTemplateColumns: '1fr auto 1fr',
  },
  allWidth: { gridColumn: 'span 3' },
  paddingTop: { paddingTop: theme.spacing(2) },
}));

const TargetResultsDetail = ({ inject, target }: Props) => {
  const { classes } = useStyles();
  const { t } = useFormatter();
  const canShowExecutionTab = target.target_type !== 'ASSETS_GROUPS';

  const [sortedGroupedTargetResults, setSortedGroupedTargetResults] = useState<Record<string, InjectExpectationsStore[]>>({});

  const [searchParams, setSearchParams] = useSearchParams();
  const openIdParams = searchParams.get('expectation_id');

  const [activeTab, setActiveTab] = useState(0);
  const handleTabChange = (_event: SyntheticEvent, newValue: number) => {
    setActiveTab(newValue);
  };

  const { injectResultOverviewOutput, updateInjectResultOverviewOutput } = useContext<InjectResultOverviewOutputContextType>(InjectResultOverviewOutputContext);

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
    fetchTargetResultMerged(inject.inject_id, target.target_id!, target.target_type!)
      .then((result: { data: InjectExpectationsStore[] }) => {
        setSortedGroupedTargetResults(transformToSortedGroupedResults(result.data ?? []));
      });
  }, [injectResultOverviewOutput, target]);

  useEffect(() => {
    if (!openIdParams || !sortedGroupedTargetResults) return;

    const activeTabIndex = Object.values(sortedGroupedTargetResults).findIndex(results =>
      results.some(r => r.inject_expectation_id === openIdParams),
    );

    if (activeTabIndex === -1) return;

    setActiveTab(activeTabIndex);
    searchParams.delete('open');
    setSearchParams(searchParams, { replace: true });
  }, [openIdParams, sortedGroupedTargetResults]);

  return (
    <Paper className={classes.container}>
      <Typography sx={{ justifySelf: 'center' }} variant="h3" gutterBottom>{t('Name')}</Typography>
      <Typography variant="h3" gutterBottom>{t('Type')}</Typography>
      <Typography sx={{ justifySelf: 'center' }} variant="h3" gutterBottom>{t('Platform')}</Typography>
      <Typography sx={{ justifySelf: 'center' }}>{target.target_name}</Typography>
      <Typography>{target.target_type}</Typography>
      <Typography sx={{ justifySelf: 'center' }}>{target.target_subtype ?? t('N/A')}</Typography>

      <TargetResultsReactFlow
        className={`${classes.allWidth} ${classes.paddingTop}`}
        injectStatusName={injectResultOverviewOutput?.inject_status?.status_name}
        targetResultsByType={sortedGroupedTargetResults}
        lastExecutionStartDate={injectResultOverviewOutput?.inject_status?.tracking_sent_date || ''}
        lastExecutionEndDate={injectResultOverviewOutput?.inject_status?.tracking_end_date || ''}
      />

      <Tabs
        value={activeTab}
        onChange={handleTabChange}
        indicatorColor="primary"
        textColor="primary"
        className={`${classes.allWidth}`}
      >
        {Object.keys(sortedGroupedTargetResults).map((type, index) => (
          <Tab key={index} label={t(`TYPE_${type}`)} />
        ))}
        {canShowExecutionTab && <Tab label={t('Execution')} />}
      </Tabs>

      <div className={`${classes.allWidth} ${classes.paddingTop}`}>
        {Object.entries(sortedGroupedTargetResults).length > 0
          && Object.entries(sortedGroupedTargetResults).length > activeTab
          && Object.entries(sortedGroupedTargetResults)[activeTab][1].map(expectationResult => (
            <InjectExpectationCard
              key={expectationResult.inject_expectation_id}
              injectExpectation={expectationResult}
              inject={inject}
              onUpdateInjectExpectationResult={updateInjectResultOverviewOutput}
            />
          ))}

        {(activeTab === Object.keys(sortedGroupedTargetResults).length && canShowExecutionTab) && (
          <ExecutionStatusDetail
            target={{
              id: target.target_id,
              name: target.target_name,
              targetType: target.target_type,
              platformType: target.target_subtype,
            }}
            injectId={inject.inject_id}
          />
        )}
      </div>

    </Paper>
  );
};

export default TargetResultsDetail;
