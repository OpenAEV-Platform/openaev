import { Paper, Typography } from '@mui/material';
import { type FunctionComponent, useCallback, useEffect, useState } from 'react';

import { fetchGeneratedReports } from '../../../../../actions/generated_reports/generatedreport-action';
import { useFormatter } from '../../../../../components/i18n';
import { type Exercise, type GeneratedReport } from '../../../../../utils/api-types';
import GeneratedReportsFilterBar from './generatedReportFilters';
import { DEFAULT_GENERATED_REPORT_FILTERS, filterGeneratedReports, type GeneratedReportFilters } from './generatedReportFiltersUtils';
import GeneratedReportsList from './GeneratedReportsList';
import GenerateReportButton from './GenerateReportButton';

interface Props { exerciseId: Exercise['exercise_id'] }

/**
 * Structured "Reports" feature entry point for a simulation: template
 * selection + trigger ("Generate Report") and the history of previously
 * generated reports. Intentionally minimal UI per scope: no configuration
 * screen, no preview, no Notification Center hook.
 */
const GeneratedReports: FunctionComponent<Props> = ({ exerciseId }) => {
  const { t } = useFormatter();
  const [generatedReports, setGeneratedReports] = useState<GeneratedReport[]>([]);
  const [filters, setFilters] = useState<GeneratedReportFilters>(DEFAULT_GENERATED_REPORT_FILTERS);

  const reload = useCallback(() => {
    fetchGeneratedReports(exerciseId).then((res) => {
      setGeneratedReports(res.data ?? []);
    });
  }, [exerciseId]);

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
        <Typography variant="h4" style={{ margin: 0 }}>{t('Generated reports')}</Typography>
        <GenerateReportButton exerciseId={exerciseId} onGenerated={reload} />
      </div>
      <GeneratedReportsFilterBar filters={filters} onChange={setFilters} />
      <GeneratedReportsList exerciseId={exerciseId} generatedReports={filterGeneratedReports(generatedReports, filters)} />
    </Paper>
  );
};

export default GeneratedReports;
