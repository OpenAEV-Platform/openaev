import { Add } from '@mui/icons-material';
import { Button, Dialog, DialogActions, DialogContent, DialogTitle, FormControl, InputLabel, MenuItem, Select, type SelectChangeEvent, TextField, Typography } from '@mui/material';
import { type FunctionComponent, useState } from 'react';

import { createGlobalGeneratedReport, updateGlobalGeneratedReportStatus, uploadGlobalGeneratedReportDocument } from '../../../../../actions/generated_reports/generatedreport-action';
import { useFormatter } from '../../../../../components/i18n';
import { sendErrorToBackend } from '../../../../../utils/Action';
import { type GeneratedReport } from '../../../../../utils/api-types';
import { MESSAGING$ } from '../../../../../utils/Environment';
import { type ComparisonWindow, WINDOW_OPTIONS, windowLabelFor } from './comparisonWindow';
import fetchGlobalGeneratedReportPdfData from './fetchGlobalGeneratedReportPdfData';
import getGlobalExecutiveReportPdfDoc from './getGlobalExecutiveReportPdfDoc';
import getGlobalTechnicalReportPdfDoc from './getGlobalTechnicalReportPdfDoc';
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

interface Props { onGenerated: () => void }

/**
 * "Generate Global Report" trigger for the unified "Reports" page (left menu,
 * under Findings): same versioned/persisted lifecycle as the per-simulation
 * `GenerateReportButton` (PENDING -> RUNNING -> COMPLETED/FAILED backed by
 * `GeneratedReport`), but scoped platform-wide (no `exercise`/`scenario` set).
 * Unlike the Home page's `GenerateGlobalReportQuickButton` (ephemeral,
 * not saved), every click here creates a new row visible in this list.
 */
const GenerateGlobalReportButton: FunctionComponent<Props> = ({ onGenerated }) => {
  const { t, fldt } = useFormatter();
  const [open, setOpen] = useState(false);
  const [template, setTemplate] = useState<ReportTemplateKey>('EXECUTIVE');
  const [window, setWindow] = useState<ComparisonWindow>('LAST_MONTH');
  const [customStart, setCustomStart] = useState('');
  const [customEnd, setCustomEnd] = useState('');
  const [generating, setGenerating] = useState(false);

  const handleOpen = () => setOpen(true);
  const handleClose = () => {
    if (!generating) setOpen(false);
  };

  const buildDocDefinition = async (data: Awaited<ReturnType<typeof fetchGlobalGeneratedReportPdfData>>) => {
    if (template === 'EXECUTIVE') {
      return getGlobalExecutiveReportPdfDoc({
        data,
        t,
        fldt,
      });
    }
    return getGlobalTechnicalReportPdfDoc({
      data,
      t,
      fldt,
    });
  };

  const handleGenerate = async () => {
    setGenerating(true);
    let generatedReport: GeneratedReport | undefined;
    try {
      const windowInput = {
        window,
        startDate: window === 'CUSTOM' ? customStart : undefined,
        endDate: window === 'CUSTOM' ? customEnd : undefined,
      };
      const createRes = await createGlobalGeneratedReport({
        generated_report_template: template,
        generated_report_label: t(windowLabelFor(window)),
      });
      generatedReport = createRes.data;
      await updateGlobalGeneratedReportStatus(generatedReport!.generated_report_id, { generated_report_status: 'RUNNING' });

      const data = await fetchGlobalGeneratedReportPdfData(windowInput);
      const docDefinition = await buildDocDefinition(data);

      const pdfMake = await loadPdfMake();
      const pdfBlob: Blob = await pdfMake.createPdf(docDefinition).getBlob();
      const file = new File([pdfBlob], `global_${template.toLowerCase()}_report.pdf`, { type: 'application/pdf' });

      await uploadGlobalGeneratedReportDocument(generatedReport!.generated_report_id, file);
      MESSAGING$.notifySuccess(t('Report successfully generated'));
      setOpen(false);
      onGenerated();
    } catch (e) {
      if (generatedReport) {
        await updateGlobalGeneratedReportStatus(generatedReport.generated_report_id, {
          generated_report_status: 'FAILED',
          generated_report_error_message: e instanceof Error ? e.message : 'Unknown error',
        }).catch(() => {});
      }
      sendErrorToBackend(e as Error, { componentStack: 'GenerateGlobalReportButton' });
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
        {t('Generate Global Report')}
      </Button>
      <Dialog open={open} onClose={handleClose} fullWidth maxWidth="xs">
        <DialogTitle>{t('Generate a global report')}</DialogTitle>
        <DialogContent>
          <FormControl fullWidth style={{ marginTop: 8 }}>
            <InputLabel id="global-report-template-label">{t('Template')}</InputLabel>
            <Select
              labelId="global-report-template-label"
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
          <Typography
            variant="caption"
            color="textSecondary"
            style={{
              marginTop: 12,
              display: 'block',
            }}
          >
            {t('This report covers every simulation platform-wide and will be saved to this list.')}
          </Typography>
          <FormControl fullWidth style={{ marginTop: 16 }}>
            <InputLabel id="global-report-window-label">{t('Comparison window')}</InputLabel>
            <Select
              labelId="global-report-window-label"
              value={window}
              label={t('Comparison window')}
              disabled={generating}
              onChange={(event: SelectChangeEvent) => setWindow(event.target.value as ComparisonWindow)}
            >
              {WINDOW_OPTIONS.map(option => (
                <MenuItem key={option.key} value={option.key}>
                  {t(option.label)}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          {window === 'CUSTOM' && (
            <div style={{
              display: 'flex',
              gap: 8,
              marginTop: 16,
            }}
            >
              <TextField
                label={t('Start date')}
                type="date"
                fullWidth
                disabled={generating}
                value={customStart}
                onChange={e => setCustomStart(e.target.value)}
                slotProps={{ inputLabel: { shrink: true } }}
              />
              <TextField
                label={t('End date')}
                type="date"
                fullWidth
                disabled={generating}
                value={customEnd}
                onChange={e => setCustomEnd(e.target.value)}
                slotProps={{ inputLabel: { shrink: true } }}
              />
            </div>
          )}
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

export default GenerateGlobalReportButton;
