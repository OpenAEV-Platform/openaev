import { Button, Chip } from '@filigran/design-system';
// fds:keep-mui Dialog and Alert have no library equivalent yet
import { Alert, Dialog, DialogActions, DialogContent, DialogTitle, useTheme } from '@mui/material';
import { type FunctionComponent } from 'react';

import { download } from '../../../utils/utils';
import { useFormatter } from '../../i18n';
import Transition from '../Transition';
import { skippedRowsOf, skippedRowsToJson } from './import-report-helpers';
import { type ImportReport } from './import-report-types';

type ReasonSeverity = 'medium' | 'critical';

// `as const` keeps the keyword values ('column', 'auto'...) as literals, so the objects are valid
// `style` values whatever the editor's type service infers.
const contentStyle = {
  display: 'flex',
  flexDirection: 'column',
  gap: 16,
} as const;

const scrollStyle = { overflowX: 'auto' } as const;

const tableStyle = {
  width: '100%',
  borderCollapse: 'collapse',
} as const;

interface Props {
  open: boolean;
  onClose: () => void;
  title: string;
  // The report of a file that was read, or the message of a file-level failure. One of the two.
  report: ImportReport | null;
  fileError?: string | null;
  // Optional info banner, for example to flag a mocked backend.
  notice?: string;
  // Translated label of each field of a row, by field name. A field without label shows its name.
  columnLabels?: Record<string, string>;
  // Translated label of each reason code. A code without label shows the code.
  reasonLabels?: Record<string, string>;
  // Tone of the reason chip, by reason code. Defaults to critical (a malformed value); use medium
  // for a conflict with data that already exists.
  reasonSeverities?: Record<string, ReasonSeverity>;
  downloadFileName: string;
}

const ImportReportDialog: FunctionComponent<Props> = ({
  open,
  onClose,
  title,
  report,
  fileError = null,
  notice,
  columnLabels = {},
  reasonLabels = {},
  reasonSeverities = {},
  downloadFileName,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const skippedRows = report ? skippedRowsOf(report) : [];

  const cellStyle = {
    padding: '8px 12px',
    textAlign: 'left',
    verticalAlign: 'top',
    borderBottom: `1px solid ${theme.palette.divider}`,
  } as const;

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="md"
      fullWidth
      slotProps={{ paper: { elevation: 1 } }}
      slots={{ transition: Transition }}
    >
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <div style={contentStyle}>
          {notice && <Alert severity="info">{notice}</Alert>}
          {fileError && (
            <Alert severity="error">
              {`${t('The file was not imported.')} ${fileError}`}
            </Alert>
          )}
          {report && (
            <>
              <div style={{
                display: 'flex',
                gap: 8,
              }}
              >
                <Chip label={`${report.import_created} ${t('imported')}`} severity={report.import_created > 0 ? 'low' : 'neutral'} />
                <Chip label={`${report.import_skipped} ${t('skipped')}`} severity={report.import_skipped > 0 ? 'critical' : 'neutral'} />
                {report.import_ignored > 0 && (
                  <Chip label={`${report.import_ignored} ${t('ignored (already identical)')}`} severity="neutral" />
                )}
              </div>
              {skippedRows.length > 0 && (
                <Alert severity="warning">
                  {t('The rows below were not imported and nothing existing was overwritten. Fix them in your file and import it again.')}
                </Alert>
              )}
              {skippedRows.length > 0 && (
                <div style={scrollStyle}>
                  <table style={tableStyle}>
                    <thead>
                      <tr>
                        <th style={cellStyle}>{t('Position')}</th>
                        {report.import_columns.map(column => (
                          <th key={column} style={cellStyle}>{columnLabels[column] ?? column}</th>
                        ))}
                        <th style={cellStyle}>{t('Reason')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {skippedRows.map(row => (
                        <tr key={row.import_row_position}>
                          <td style={cellStyle}>{row.import_row_position}</td>
                          {report.import_columns.map(column => (
                            // Plain text on purpose: these are the user's own fields, never HTML.
                            <td key={column} style={cellStyle}>{row.import_row_values[column] || '-'}</td>
                          ))}
                          <td style={cellStyle}>
                            {row.import_row_reason && (
                              <Chip
                                label={reasonLabels[row.import_row_reason] ?? row.import_row_reason}
                                severity={reasonSeverities[row.import_row_reason] ?? 'critical'}
                              />
                            )}
                            <div style={{ marginTop: 4 }}>{row.import_row_message}</div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </>
          )}
        </div>
      </DialogContent>
      <DialogActions>
        {report && skippedRows.length > 0 && (
          <Button
            type="button"
            priority="secondary"
            onClick={() => download(skippedRowsToJson(report), downloadFileName, 'application/json')}
          >
            {t('Download skipped rows')}
          </Button>
        )}
        <Button type="button" onClick={onClose}>
          {t('Close')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default ImportReportDialog;
