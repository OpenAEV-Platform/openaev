import { Add } from '@mui/icons-material';
import { Button, Dialog, DialogActions, DialogContent, DialogTitle, FormControl, InputLabel, MenuItem, Select, type SelectChangeEvent, TextField, Typography } from '@mui/material';
import { type FunctionComponent, useState } from 'react';

import { createScenarioGeneratedReport, updateScenarioGeneratedReportStatus, uploadScenarioGeneratedReportDocument } from '../../../../../actions/generated_reports/generatedreport-action';
import { useFormatter } from '../../../../../components/i18n';
import { sendErrorToBackend } from '../../../../../utils/Action';
import { type GeneratedReport, type Scenario } from '../../../../../utils/api-types';
import { MESSAGING$ } from '../../../../../utils/Environment';
import { type ComparisonWindow, WINDOW_OPTIONS, windowLabelFor } from './comparisonWindow';
import fetchScenarioGeneratedReportPdfData, { type ScenarioGeneratedReportPdfData } from './fetchScenarioGeneratedReportPdfData';
import getScenarioExecutiveReportPdfDoc from './getScenarioExecutiveReportPdfDoc';
import getScenarioTechnicalReportPdfDoc from './getScenarioTechnicalReportPdfDoc';
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
  scenario: Scenario;
  onGenerated: () => void;
}

/**
 * "Generate Report" trigger for Scenario reports: template selector (2 fixed
 * templates) - with, when Technical is selected, a choice between the 3
 * alternative content backbones (MITRE/Control/Timeline-centric) mirroring
 * the per-simulation and global buttons - plus a comparison-window selector
 * (Last run/1 week/1 month/Custom), aggregating every simulation run of the
 * scenario within that window. Same async status tracking / client-side
 * pdfmake assembly pipeline as the per-simulation and global buttons.
 */
const GenerateScenarioReportButton: FunctionComponent<Props> = ({ scenario, onGenerated }) => {
  const { t, fldt } = useFormatter();
  const [open, setOpen] = useState(false);
  const [template, setTemplate] = useState<ReportTemplateKey>('EXECUTIVE');
  const [window, setWindow] = useState<ComparisonWindow>('LAST_RUN');
  const [customStart, setCustomStart] = useState('');
  const [customEnd, setCustomEnd] = useState('');
  const [generating, setGenerating] = useState(false);

  const handleOpen = () => setOpen(true);
  const handleClose = () => {
    if (!generating) setOpen(false);
  };

  const buildDocDefinition = async (data: ScenarioGeneratedReportPdfData) => {
    if (template === 'EXECUTIVE') {
      return getScenarioExecutiveReportPdfDoc({
        data,
        t,
        fldt,
      });
    }
    return getScenarioTechnicalReportPdfDoc({
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
      const createRes = await createScenarioGeneratedReport(scenario.scenario_id, {
        generated_report_template: template,
        generated_report_label: t(windowLabelFor(window)),
      });
      generatedReport = createRes.data;
      await updateScenarioGeneratedReportStatus(scenario.scenario_id, generatedReport!.generated_report_id, { generated_report_status: 'RUNNING' });

      const data = await fetchScenarioGeneratedReportPdfData(scenario, windowInput);
      const docDefinition = await buildDocDefinition(data);

      const pdfMake = await loadPdfMake();
      const pdfBlob: Blob = await pdfMake.createPdf(docDefinition).getBlob();
      const file = new File([pdfBlob], `scenario_${template.toLowerCase()}_report_${scenario.scenario_id}.pdf`, { type: 'application/pdf' });

      await uploadScenarioGeneratedReportDocument(scenario.scenario_id, generatedReport!.generated_report_id, file);
      MESSAGING$.notifySuccess(t('Report successfully generated'));
      setOpen(false);
      onGenerated();
    } catch (e) {
      if (generatedReport) {
        await updateScenarioGeneratedReportStatus(scenario.scenario_id, generatedReport.generated_report_id, {
          generated_report_status: 'FAILED',
          generated_report_error_message: e instanceof Error ? e.message : 'Unknown error',
        }).catch(() => {});
      }
      sendErrorToBackend(e as Error, { componentStack: 'GenerateScenarioReportButton' });
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
        <DialogTitle>{t('Generate a new scenario report')}</DialogTitle>
        <DialogContent>
          <FormControl fullWidth style={{ marginTop: 8 }}>
            <InputLabel id="scenario-report-template-label">{t('Template')}</InputLabel>
            <Select
              labelId="scenario-report-template-label"
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
          <FormControl fullWidth style={{ marginTop: 16 }}>
            <InputLabel id="scenario-report-window-label">{t('Comparison window')}</InputLabel>
            <Select
              labelId="scenario-report-window-label"
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

export default GenerateScenarioReportButton;
