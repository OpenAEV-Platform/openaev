import { Paper, Typography } from '@mui/material';
import { type FunctionComponent, useCallback, useEffect, useState } from 'react';

import { fetchScenarioGeneratedReports } from '../../../../../actions/generated_reports/generatedreport-action';
import { useFormatter } from '../../../../../components/i18n';
import { type GeneratedReport, type Scenario } from '../../../../../utils/api-types';
import GeneratedReportsFilterBar from './generatedReportFilters';
import { DEFAULT_GENERATED_REPORT_FILTERS, filterGeneratedReports, type GeneratedReportFilters } from './generatedReportFiltersUtils';
import GenerateScenarioReportButton from './GenerateScenarioReportButton';
import ScenarioGeneratedReportsList from './ScenarioGeneratedReportsList';

interface Props { scenario: Scenario }

/**
 * "Access Reports" entry point for a Scenario: generation trigger
 * (template + comparison window) and history of previously generated
 * scenario reports, mirroring `GlobalGeneratedReports.tsx`.
 */
const ScenarioGeneratedReports: FunctionComponent<Props> = ({ scenario }) => {
  const { t } = useFormatter();
  const [generatedReports, setGeneratedReports] = useState<GeneratedReport[]>([]);
  const [filters, setFilters] = useState<GeneratedReportFilters>(DEFAULT_GENERATED_REPORT_FILTERS);

  const reload = useCallback(() => {
    fetchScenarioGeneratedReports(scenario.scenario_id).then((res) => {
      setGeneratedReports(res.data ?? []);
    });
  }, [scenario.scenario_id]);

  useEffect(() => {
    reload();
  }, [reload]);

  return (
    <Paper
      variant="outlined"
      style={{
        padding: 20,
        marginBottom: 20,
      }}
    >
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: 10,
      }}
      >
        <Typography variant="h4" style={{ margin: 0 }}>{t('Scenario reports')}</Typography>
        <GenerateScenarioReportButton scenario={scenario} onGenerated={reload} />
      </div>
      <GeneratedReportsFilterBar filters={filters} onChange={setFilters} />
      <ScenarioGeneratedReportsList scenarioId={scenario.scenario_id} generatedReports={filterGeneratedReports(generatedReports, filters)} />
    </Paper>
  );
};

export default ScenarioGeneratedReports;
