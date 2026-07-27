import { Download } from '@mui/icons-material';
import { Chip, IconButton, List, ListItem, ListItemText, Tooltip } from '@mui/material';
import { type FunctionComponent } from 'react';

import { downloadGeneratedReportUrl } from '../../../../../actions/generated_reports/generatedreport-action';
import { useFormatter } from '../../../../../components/i18n';
import { type Exercise, type GeneratedReport } from '../../../../../utils/api-types';
import { triggerSourceLabel } from './generatedReportFiltersUtils';
import { REPORT_TEMPLATES } from './reportTemplates';

interface Props {
  exerciseId: Exercise['exercise_id'];
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
 * History of generated reports for this simulation: status + re-download,
 * as required by the "versioning & storage" requirement (traceability,
 * re-downloadable past reports).
 */
const GeneratedReportsList: FunctionComponent<Props> = ({ exerciseId, generatedReports }) => {
  const { t, fldt } = useFormatter();

  if (generatedReports.length === 0) {
    return (
      <div style={{
        textAlign: 'center',
        padding: 20,
      }}
      >
        <i>{t('No report has been generated for this simulation yet')}</i>
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
                  href={downloadGeneratedReportUrl(exerciseId, generatedReport.generated_report_id)}
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

export default GeneratedReportsList;
