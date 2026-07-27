import { FormControl, InputLabel, MenuItem, Select, type SelectChangeEvent } from '@mui/material';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import { type GeneratedReportFilters, type ReportTemplateFilter, type TriggerSourceFilter, triggerSourceLabel } from './generatedReportFiltersUtils';
import { REPORT_TEMPLATES } from './reportTemplates';

interface FilterBarProps {
  filters: GeneratedReportFilters;
  onChange: (filters: GeneratedReportFilters) => void;
}

/**
 * Shared "Access Reports" filter bar: filter by report template
 * (Executive/Technical) and by trigger source (Manual/Auto on completion/
 * Scheduled). Used identically by the per-simulation, global and scenario
 * report history lists.
 */
const GeneratedReportsFilterBar: FunctionComponent<FilterBarProps> = ({ filters, onChange }) => {
  const { t } = useFormatter();
  return (
    <div style={{
      display: 'flex',
      gap: 12,
      marginBottom: 12,
      flexWrap: 'wrap',
    }}
    >
      <FormControl size="small" style={{ minWidth: 160 }}>
        <InputLabel id="generated-reports-filter-template">{t('Template')}</InputLabel>
        <Select
          labelId="generated-reports-filter-template"
          label={t('Template')}
          value={filters.template}
          onChange={(event: SelectChangeEvent) => onChange({
            ...filters,
            template: event.target.value as ReportTemplateFilter,
          })}
        >
          <MenuItem value="ALL">{t('All')}</MenuItem>
          {REPORT_TEMPLATES.map(rt => (
            <MenuItem key={rt.key} value={rt.key}>{t(rt.label)}</MenuItem>
          ))}
        </Select>
      </FormControl>
      <FormControl size="small" style={{ minWidth: 160 }}>
        <InputLabel id="generated-reports-filter-trigger">{t('Trigger')}</InputLabel>
        <Select
          labelId="generated-reports-filter-trigger"
          label={t('Trigger')}
          value={filters.triggerSource}
          onChange={(event: SelectChangeEvent) => onChange({
            ...filters,
            triggerSource: event.target.value as TriggerSourceFilter,
          })}
        >
          <MenuItem value="ALL">{t('All')}</MenuItem>
          <MenuItem value="MANUAL">{t(triggerSourceLabel('MANUAL'))}</MenuItem>
          <MenuItem value="AUTO_ON_COMPLETION">{t(triggerSourceLabel('AUTO_ON_COMPLETION'))}</MenuItem>
          <MenuItem value="SCHEDULED">{t(triggerSourceLabel('SCHEDULED'))}</MenuItem>
        </Select>
      </FormControl>
    </div>
  );
};

export default GeneratedReportsFilterBar;
