import { Description } from '@mui/icons-material';
import { Button, Dialog, DialogActions, DialogContent, DialogTitle, FormControl, InputLabel, MenuItem, Select, type SelectChangeEvent, TextField, Typography } from '@mui/material';
import { type FunctionComponent, useState } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import { sendErrorToBackend } from '../../../../../utils/Action';
import { MESSAGING$ } from '../../../../../utils/Environment';
import { type ComparisonWindow, WINDOW_OPTIONS } from './comparisonWindow';
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

/**
 * Compact "quick" Global Report trigger for the Home page: a small button
 * opens a minimal template/variant picker, then the PDF is generated and
 * opened directly in a new browser tab. Unlike the full `GenerateGlobalReportButton`
 * (still used from nowhere now - see `Simulations.tsx` history removal),
 * this quick action is intentionally ephemeral: no `GeneratedReport` backend
 * record is created and nothing is persisted/versioned - it is a one-off,
 * throwaway snapshot for a fast at-a-glance look, not part of the
 * "Access Reports" version history.
 */
const GenerateGlobalReportQuickButton: FunctionComponent = () => {
  const { t, fldt } = useFormatter();
  const [open, setOpen] = useState(false);
  const [template, setTemplate] = useState<ReportTemplateKey>('EXECUTIVE');
  const [comparisonWindow, setComparisonWindow] = useState<ComparisonWindow>('LAST_MONTH');
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
    try {
      const windowInput = {
        window: comparisonWindow,
        startDate: comparisonWindow === 'CUSTOM' ? customStart : undefined,
        endDate: comparisonWindow === 'CUSTOM' ? customEnd : undefined,
      };
      const data = await fetchGlobalGeneratedReportPdfData(windowInput);
      const docDefinition = await buildDocDefinition(data);

      const pdfMake = await loadPdfMake();
      const pdfBlob: Blob = await pdfMake.createPdf(docDefinition).getBlob();
      const blobUrl = URL.createObjectURL(pdfBlob);
      window.open(blobUrl, '_blank');

      setOpen(false);
    } catch (e) {
      sendErrorToBackend(e as Error, { componentStack: 'GenerateGlobalReportQuickButton' });
      MESSAGING$.notifyError(t('An error occurred during report generation.'));
    } finally {
      setGenerating(false);
    }
  };

  return (
    <>
      <Button
        variant="outlined"
        size="small"
        startIcon={<Description fontSize="small" />}
        onClick={handleOpen}
      >
        {t('Generate Global Report')}
      </Button>
      <Dialog open={open} onClose={handleClose} fullWidth maxWidth="xs">
        <DialogTitle>{t('Generate a global report')}</DialogTitle>
        <DialogContent>
          <FormControl fullWidth style={{ marginTop: 8 }}>
            <InputLabel id="global-report-quick-template-label">{t('Template')}</InputLabel>
            <Select
              labelId="global-report-quick-template-label"
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
          <Typography
            variant="caption"
            color="textSecondary"
            style={{
              marginTop: 12,
              display: 'block',
            }}
          >
            {t('This report covers every simulation platform-wide. It opens in a new tab and is not saved to Access Reports.')}
          </Typography>
          <FormControl fullWidth style={{ marginTop: 16 }}>
            <InputLabel id="global-report-quick-window-label">{t('Comparison window')}</InputLabel>
            <Select
              labelId="global-report-quick-window-label"
              value={comparisonWindow}
              label={t('Comparison window')}
              disabled={generating}
              onChange={(event: SelectChangeEvent) => setComparisonWindow(event.target.value as ComparisonWindow)}
            >
              {WINDOW_OPTIONS.map(option => (
                <MenuItem key={option.key} value={option.key}>
                  {t(option.label)}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          {comparisonWindow === 'CUSTOM' && (
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

export default GenerateGlobalReportQuickButton;
