import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type ReactNode, useContext, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router';

import { fetchTargetResult } from '../../../../../actions/atomic_testings/atomic-testing-actions';
import Paper from '../../../../../components/common/Paper';
import { useFormatter } from '../../../../../components/i18n';
import type { InjectResultOverviewOutput, InjectTarget } from '../../../../../utils/api-types';
import { isAgent, isAssetGroups, isAssets } from '../../../../../utils/target/TargetUtils';
import { type ExpectationResultType, ExpectationType, type InjectExpectationsStore } from '../../../common/injects/expectations/Expectation';
import ExecutionStatusDetail from '../../../common/injects/status/ExecutionStatusDetail';
import TerminalViewTab from '../../../common/injects/status/traces/TerminalViewTab';
import { InjectResultOverviewOutputContext, type InjectResultOverviewOutputContextType } from '../../InjectResultOverviewOutputContext';
import InjectExpectationProvider from '../context/InjectExpectationProvider';
import InjectExpectationCard from './InjectExpectationCard';
import TargetResultsTimeline from './TargetResultsTimeline';

interface Props {
  inject: InjectResultOverviewOutput;
  target: InjectTarget;
  isAgentless: boolean;
}

interface SectionConfig {
  key: string;
  label: string;
  content: ReactNode;
}

const TargetResultsDetail = ({ inject, target, isAgentless }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();

  const [sortedGroupedTargetResults, setSortedGroupedTargetResults] = useState<Record<string, InjectExpectationsStore[]>>({});

  const [searchParams, setSearchParams] = useSearchParams();
  const openIdParams = searchParams.get('expectation_id');

  const sectionRefs = useRef<Record<string, HTMLElement | null>>({});

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

  // ?expectation_id= deep link: scroll to the section containing that expectation.
  useEffect(() => {
    if (!openIdParams || !sortedGroupedTargetResults) return;

    const expectationType = Object.values(sortedGroupedTargetResults)
      .flat()
      .find(result => result.inject_expectation_id === openIdParams)
      ?.inject_expectation_type;

    if (!expectationType) return;

    sectionRefs.current[expectationType]?.scrollIntoView({
      behavior: 'smooth',
      block: 'start',
    });
    searchParams.delete('open');
    setSearchParams(searchParams, { replace: true });
  }, [openIdParams, sortedGroupedTargetResults]);

  const sections: SectionConfig[] = [];
  if (!isAssetGroups(target)) {
    sections.push({
      key: 'execution',
      label: t('Execution'),
      content: (
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
    // Terminal view shows command-execution traces, which only exist for agent-based execution:
    // endpoints (ASSETS) and their agents (AGENT). Other target kinds - AI targets, cloud / SaaS /
    // identity assets, teams, players - never produce a terminal transcript, so the section is hidden.
    if (isAssets(target) || isAgent(target)) {
      sections.push({
        key: 'terminal-view',
        label: t('Terminal view'),
        content: (
          <TerminalViewTab injectId={inject.inject_id} target={target} />
        ),
      });
    }
  }

  Object.entries(sortedGroupedTargetResults).forEach(([type, expectationResults]) => (
    sections.push({
      key: type,
      label: t(`TYPE_${type}`),
      content: (
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(1),
        }}
        >
          {expectationResults.map(expectationResult => (
            <InjectExpectationProvider key={expectationResult.inject_expectation_id} inject={inject}>
              <InjectExpectationCard
                injectExpectation={expectationResult}
                inject={inject}
                isAgentless={isAgentless}
                target={target}
              />
            </InjectExpectationProvider>
          ))}
        </div>
      ),
    })
  ));

  return (
    <Paper>
      <div
        key={`${inject.inject_id}-${target.target_id}`}
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(2.5),
          paddingTop: theme.spacing(1),
        }}
      >
        <TargetResultsTimeline
          injectStatusName={injectResultOverviewOutput?.inject_status?.status_name}
          targetResultsByType={sortedGroupedTargetResults}
          lastExecutionStartDate={injectResultOverviewOutput?.inject_status?.tracking_sent_date || ''}
          lastExecutionEndDate={injectResultOverviewOutput?.inject_status?.tracking_end_date || ''}
        />

        {sections.map(section => (
          <section
            key={section.key}
            ref={(element) => {
              sectionRefs.current[section.key] = element;
            }}
            style={{ scrollMarginTop: theme.spacing(6) }}
          >
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: theme.spacing(1.5),
              marginBottom: theme.spacing(1.5),
            }}
            >
              <Typography
                sx={{
                  fontSize: 11,
                  fontWeight: 600,
                  textTransform: 'uppercase',
                  letterSpacing: '0.12em',
                  color: 'text.secondary',
                  fontFamily: theme.typography.h1.fontFamily,
                  whiteSpace: 'nowrap',
                }}
              >
                {section.label}
              </Typography>
              <Box
                aria-hidden
                sx={{
                  flex: 1,
                  height: '1px',
                  backgroundColor: alpha(theme.palette.text.primary, 0.05),
                }}
              />
            </div>
            {section.content}
          </section>
        ))}
      </div>
    </Paper>
  );
};

export default TargetResultsDetail;
