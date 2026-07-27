import { Download } from '@mui/icons-material';
import {
  Alert,
  Chip,
  FormControl,
  IconButton,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  type SelectChangeEvent,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material';
import { type FunctionComponent, useState } from 'react';

import { useFormatter } from '../../../components/i18n';
import { type GeneratedReport } from '../../../utils/api-types';
import { triggerSourceLabel } from '../simulations/simulation/generated_reports/generatedReportFiltersUtils';
import { REPORT_TEMPLATES } from '../simulations/simulation/generated_reports/reportTemplates';

type TemplateFilter = 'ALL' | GeneratedReport['generated_report_template'];
type StatusFilter = 'ALL' | GeneratedReport['generated_report_status'];
type TriggerFilter = 'ALL' | GeneratedReport['generated_report_trigger_source'];

const statusColor = (status: GeneratedReport['generated_report_status']) => {
  switch (status) {
    case 'COMPLETED': return 'success';
    case 'FAILED': return 'error';
    case 'RUNNING': return 'warning';
    default: return 'default';
  }
};

interface Props {
  reports: GeneratedReport[];
  sourceLabel: (report: GeneratedReport) => string;
  downloadUrlFor: (report: GeneratedReport) => string;
  emptyLabel: string;
}

/**
 * Single-scope report table (Global, Simulation or Scenario) with its own
 * independent filter bar (template / technical layout / status / trigger).
 * Used inside each tab of AllReports so filtering in one tab never affects
 * another.
 */
const ReportsScopeTab: FunctionComponent<Props> = ({
  reports,
  sourceLabel,
  downloadUrlFor,
  emptyLabel,
}) => {
  const { t, fldt } = useFormatter();

  const [templateFilter, setTemplateFilter] = useState<TemplateFilter>('ALL');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const [triggerFilter, setTriggerFilter] = useState<TriggerFilter>('ALL');

  const filtered = reports.filter((report) => {
    if (templateFilter !== 'ALL' && report.generated_report_template !== templateFilter) return false;
    if (statusFilter !== 'ALL' && report.generated_report_status !== statusFilter) return false;
    if (triggerFilter !== 'ALL' && report.generated_report_trigger_source !== triggerFilter) return false;
    return true;
  });

  return (
    <>
      <div style={{
        display: 'flex',
        gap: 12,
        marginBottom: 16,
        flexWrap: 'wrap',
      }}
      >
        <FormControl size="small" style={{ minWidth: 160 }}>
          <InputLabel id="reports-filter-template">{t('Template')}</InputLabel>
          <Select
            labelId="reports-filter-template"
            label={t('Template')}
            value={templateFilter}
            onChange={(event: SelectChangeEvent) => setTemplateFilter(event.target.value as TemplateFilter)}
          >
            <MenuItem value="ALL">{t('All')}</MenuItem>
            {REPORT_TEMPLATES.map(rt => (
              <MenuItem key={rt.key} value={rt.key}>{t(rt.label)}</MenuItem>
            ))}
          </Select>
        </FormControl>
        <FormControl size="small" style={{ minWidth: 150 }}>
          <InputLabel id="reports-filter-status">{t('Status')}</InputLabel>
          <Select
            labelId="reports-filter-status"
            label={t('Status')}
            value={statusFilter}
            onChange={(event: SelectChangeEvent) => setStatusFilter(event.target.value as StatusFilter)}
          >
            <MenuItem value="ALL">{t('All')}</MenuItem>
            <MenuItem value="PENDING">{t('PENDING')}</MenuItem>
            <MenuItem value="RUNNING">{t('RUNNING')}</MenuItem>
            <MenuItem value="COMPLETED">{t('COMPLETED')}</MenuItem>
            <MenuItem value="FAILED">{t('FAILED')}</MenuItem>
          </Select>
        </FormControl>
        <FormControl size="small" style={{ minWidth: 170 }}>
          <InputLabel id="reports-filter-trigger">{t('Trigger')}</InputLabel>
          <Select
            labelId="reports-filter-trigger"
            label={t('Trigger')}
            value={triggerFilter}
            onChange={(event: SelectChangeEvent) => setTriggerFilter(event.target.value as TriggerFilter)}
          >
            <MenuItem value="ALL">{t('All')}</MenuItem>
            <MenuItem value="MANUAL">{t(triggerSourceLabel('MANUAL'))}</MenuItem>
            <MenuItem value="AUTO_ON_COMPLETION">{t(triggerSourceLabel('AUTO_ON_COMPLETION'))}</MenuItem>
            <MenuItem value="SCHEDULED">{t(triggerSourceLabel('SCHEDULED'))}</MenuItem>
          </Select>
        </FormControl>
      </div>
      {filtered.length === 0
        ? (
            <Alert severity="info">{reports.length === 0 ? t(emptyLabel) : t('No report matches the current filters.')}</Alert>
          )
        : (
            <TableContainer component={Paper} variant="outlined">
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>{t('Source')}</TableCell>
                    <TableCell>{t('Template')}</TableCell>
                    <TableCell>{t('Trigger')}</TableCell>
                    <TableCell>{t('Status')}</TableCell>
                    <TableCell>{t('Generated')}</TableCell>
                    <TableCell align="right">{t('Actions')}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {filtered.map((report) => {
                    const templateLabel = REPORT_TEMPLATES.find(rt => rt.key === report.generated_report_template)?.label
                      ?? report.generated_report_template;
                    return (
                      <TableRow key={report.generated_report_id} hover>
                        <TableCell>
                          <Typography variant="body2">{sourceLabel(report)}</Typography>
                          {report.generated_report_label && (
                            <Typography variant="caption" color="textSecondary">{report.generated_report_label}</Typography>
                          )}
                        </TableCell>
                        <TableCell>
                          {t(templateLabel)}
                        </TableCell>
                        <TableCell>{t(triggerSourceLabel(report.generated_report_trigger_source))}</TableCell>
                        <TableCell>
                          <Chip
                            size="small"
                            label={t(report.generated_report_status)}
                            color={statusColor(report.generated_report_status)}
                          />
                        </TableCell>
                        <TableCell>{fldt(report.generated_report_created_at)}</TableCell>
                        <TableCell align="right">
                          {report.generated_report_status === 'COMPLETED' && (
                            <Tooltip title={t('Download')}>
                              <IconButton
                                component="a"
                                href={downloadUrlFor(report)}
                                download
                                size="small"
                              >
                                <Download color="primary" fontSize="small" />
                              </IconButton>
                            </Tooltip>
                          )}
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          )}
    </>
  );
};

export default ReportsScopeTab;
