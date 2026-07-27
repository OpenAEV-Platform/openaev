import { Add } from '@mui/icons-material';
import { Button, Dialog, DialogActions, DialogContent, DialogTitle, FormControl, InputLabel, MenuItem, Select, type SelectChangeEvent, Typography } from '@mui/material';
import { type FunctionComponent, useState } from 'react';

import { createGeneratedReport, updateGeneratedReportStatus, uploadGeneratedReportDocument } from '../../../../../actions/generated_reports/generatedreport-action';
import { useFormatter } from '../../../../../components/i18n';
import { sendErrorToBackend } from '../../../../../utils/Action';
import { type Exercise, type GeneratedReport } from '../../../../../utils/api-types';
import { MESSAGING$ } from '../../../../../utils/Environment';
import fetchGeneratedReportPdfData from './fetchGeneratedReportPdfData';
import getExecutiveReportPdfDoc from './getExecutiveReportPdfDoc';
import getTechnicalReportPdfDoc from './getTechnicalReportPdfDoc';
import { REPORT_TEMPLATES, type ReportTemplateKey } from './reportTemplates';

const loadPdfMake = async () => {
  const [pdfMakeModule, pdfFontsModule] = await Promise.all([
    import('pdfmake/build/pdfmake'),
    import('pdfmake/build/vfs_fonts'),
  ]);
  const pdfMake = pdfMakeModule.default;
  const pdfFonts = pdfFontsModule.default;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (pdfMake as any).addVirtualFileSystem(pdfFonts);
  return pdfMake;
};

interface Props {
  exerciseId: Exercise['exercise_id'];
  onGenerated: () => void;
}

/**
 * "Generate Report" trigger: template selector limited to the 2 fixed
 * conceptual report types (Technical / Executive). Async status tracking
 * against the backend GeneratedReport API, and client-side PDF assembly
 * reusing the existing pdfmake export pipeline (same approach as
 * ExportPdfButton.tsx).
 */
const GenerateReportButton: FunctionComponent<Props> = ({ exerciseId, onGenerated }) => {
  const { t, fldt } = useFormatter();
  const [open, setOpen] = useState(false);
  const [template, setTemplate] = useState<ReportTemplateKey>('EXECUTIVE');
  const [generating, setGenerating] = useState(false);

  const handleOpen = () => setOpen(true);
  const handleClose = () => {
    if (!generating) setOpen(false);
  };

  const buildDocDefinition = async (data: Awaited<ReturnType<typeof fetchGeneratedReportPdfData>>) => {
    if (template === 'EXECUTIVE') {
      return getExecutiveReportPdfDoc({
        data,
        t,
        fldt,
      });
    }
    return getTechnicalReportPdfDoc({
      data,
      t,
      fldt,
    });
  };

  const handleGenerate = async () => {
    setGenerating(true);
    let generatedReport: GeneratedReport | undefined;
    try {
      const createRes = await createGeneratedReport(exerciseId, { generated_report_template: template });
      generatedReport = createRes.data;
      await updateGeneratedReportStatus(exerciseId, generatedReport!.generated_report_id, { generated_report_status: 'RUNNING' });

      const data = await fetchGeneratedReportPdfData(exerciseId);
      const docDefinition = await buildDocDefinition(data);

      const pdfMake = await loadPdfMake();
      const pdfBlob: Blob = await pdfMake.createPdf(docDefinition).getBlob();
      const file = new File([pdfBlob], `${template.toLowerCase()}_report_${exerciseId}.pdf`, { type: 'application/pdf' });

      await uploadGeneratedReportDocument(exerciseId, generatedReport!.generated_report_id, file);
      MESSAGING$.notifySuccess(t('Report successfully generated'));
      setOpen(false);
      onGenerated();
    } catch (e) {
      if (generatedReport) {
        await updateGeneratedReportStatus(exerciseId, generatedReport.generated_report_id, {
          generated_report_status: 'FAILED',
          generated_report_error_message: e instanceof Error ? e.message : 'Unknown error',
        }).catch(() => {});
      }
      sendErrorToBackend(e as Error, { componentStack: 'GenerateReportButton' });
      MESSAGING$.notifyError(t('An error occurred during report generation.'));
      onGenerated();
    } finally {
      setGenerating(false);
    }
  };

  return (
    <>
      <Button
        variant="contained"
        color="primary"
        startIcon={<Add />}
        onClick={handleOpen}
      >
        {t('Generate Report')}
      </Button>
      <Dialog open={open} onClose={handleClose} fullWidth maxWidth="xs">
        <DialogTitle>{t('Generate a new report')}</DialogTitle>
        <DialogContent>
          <FormControl fullWidth style={{ marginTop: 8 }}>
            <InputLabel id="report-template-label">{t('Template')}</InputLabel>
            <Select
              labelId="report-template-label"
              value={template}
              label={t('Template')}
              disabled={generating}
              onChange={(event: SelectChangeEvent) => setTemplate(event.target.value as ReportTemplateKey)}
            >
              {REPORT_TEMPLATES.map(reportTemplate => (
                <MenuItem key={reportTemplate.key} value={reportTemplate.key}>
                  {t(reportTemplate.label)}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <Typography variant="body2" color="textSecondary" style={{ marginTop: 8 }}>
            {t(REPORT_TEMPLATES.find(rt => rt.key === template)?.description ?? '')}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose} disabled={generating}>{t('Cancel')}</Button>
          <Button onClick={handleGenerate} color="primary" variant="contained" disabled={generating}>
            {generating ? t('Generating...') : t('Generate')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default GenerateReportButton;
