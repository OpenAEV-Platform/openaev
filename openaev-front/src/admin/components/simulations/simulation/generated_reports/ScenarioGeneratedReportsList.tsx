import { Download } from '@mui/icons-material';
import { Chip, IconButton, List, ListItem, ListItemText, Tooltip } from '@mui/material';
import { type FunctionComponent } from 'react';

import { downloadScenarioGeneratedReportUrl } from '../../../../../actions/generated_reports/generatedreport-action';
import { useFormatter } from '../../../../../components/i18n';
import { type GeneratedReport, type Scenario } from '../../../../../utils/api-types';
import { triggerSourceLabel } from './generatedReportFiltersUtils';
import { REPORT_TEMPLATES } from './reportTemplates';

interface Props {
  scenarioId: Scenario['scenario_id'];
  generatedReports: GeneratedReport[];
}

const statusColor = (status: GeneratedReport['generated_report_status']) => {
  switch (status) {
    case 'COMPLETED': return 'success';
    case 'FAILED': return 'error';
    case 'RUNNING': return 'warning';
    default: return 'default';
  }
};

/**
 * History of previously generated scenario reports: status + re-download,
 * mirroring `GlobalGeneratedReportsList.tsx` but backed by the
 * `/api/scenarios/{scenarioId}/generated-reports` endpoints, and showing the
 * comparison window used (stored in `generated_report_label`).
 */
const ScenarioGeneratedReportsList: FunctionComponent<Props> = ({ scenarioId, generatedReports }) => {
  const { t, fldt } = useFormatter();

  if (generatedReports.length === 0) {
    return (
      <div style={{
        textAlign: 'center',
        padding: 20,
      }}
      >
        <i>{t('No scenario report has been generated yet')}</i>
      </div>
    );
  }

  return (
    <List style={{ padding: 0 }}>
      {generatedReports.map((generatedReport) => {
        const templateLabel = REPORT_TEMPLATES.find(rt => rt.key === generatedReport.generated_report_template)?.label
          ?? generatedReport.generated_report_template;
        return (
          <ListItem
            key={generatedReport.generated_report_id}
            divider
            secondaryAction={generatedReport.generated_report_status === 'COMPLETED' && (
              <Tooltip title={t('Download')}>
                <IconButton
                  edge="end"
                  component="a"
                  href={downloadScenarioGeneratedReportUrl(scenarioId, generatedReport.generated_report_id)}
                  download
                >
                  <Download color="primary" />
                </IconButton>
              </Tooltip>
            )}
          >
            <ListItemText
              primary={(
                <>
                  {t(templateLabel)}
                  {generatedReport.generated_report_label && (
                    <Chip
                      size="small"
                      variant="outlined"
                      label={generatedReport.generated_report_label}
                      style={{ marginLeft: 8 }}
                    />
                  )}
                  <Chip
                    size="small"
                    variant="outlined"
                    label={t(triggerSourceLabel(generatedReport.generated_report_trigger_source))}
                    style={{ marginLeft: 8 }}
                  />
                  {' '}
                  <Chip
                    size="small"
                    label={t(generatedReport.generated_report_status)}
                    color={statusColor(generatedReport.generated_report_status)}
                    style={{ marginLeft: 8 }}
                  />
                </>
              )}
              secondary={fldt(generatedReport.generated_report_created_at)}
            />
          </ListItem>
        );
      })}
    </List>
  );
};

export default ScenarioGeneratedReportsList;
