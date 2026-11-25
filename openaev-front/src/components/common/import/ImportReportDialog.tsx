import { Button, Chip } from '@filigran/design-system';
// fds:keep-mui Dialog and Alert have no library equivalent yet
import { Alert, Dialog, DialogActions, DialogContent, DialogTitle, useTheme } from '@mui/material';
import { type FunctionComponent } from 'react';
import { CSVLink } from 'react-csv';

import { type ImportReport, type ImportReportRow } from '../../../utils/api-types';
import { useFormatter } from '../../i18n';
import Transition from '../Transition';
import { skippedRowsOf, skippedRowsToCells } from './import-report-helpers';
import { type ImportRowReason, type ReasonSeverity } from './import-report-types';

// `as const` keeps the keyword values ('column', 'auto'...) as literals, so the objects are valid
// `style` values whatever the editor's type service infers. The spacings come from the theme, so
// they are built in the component.
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
  // Translated label of each field of a row, by field name. A field without label shows its name.
  columnLabels?: Record<string, string>;
  // Translated label of each reason code. A code without label shows the code.
  reasonLabels?: Partial<Record<ImportRowReason, string>>;
  // Tone of the reason chip, by reason code. Defaults to critical (a malformed value); use medium
  // for a conflict with data that already exists.
  reasonSeverities?: Partial<Record<ImportRowReason, ReasonSeverity>>;
  // The translated sentence that explains why a row was skipped, written from its reason code and
  // `import_row_params`. Without it, only the reason chip shows.
  rowMessage?: (row: ImportReportRow) => string | null;
  downloadFileName: string;
}

const ImportReportDialog: FunctionComponent<Props> = ({
  open,
  onClose,
  title,
  report,
  fileError = null,
  columnLabels = {},
  reasonLabels = {},
  reasonSeverities = {},
  rowMessage,
  downloadFileName,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const skippedRows = report ? skippedRowsOf(report) : [];

  const contentStyle = {
    display: 'flex',
    flexDirection: 'column',
    gap: theme.spacing(2),
  } as const;

  const countsStyle = {
    display: 'flex',
    gap: theme.spacing(1),
  } as const;

  const cellStyle = {
    padding: theme.spacing(1, 1.5),
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
          {fileError && (
            <Alert severity="error">
              {`${t('The file was not imported.')} ${t(fileError)}`}
            </Alert>
          )}
          {report && (
            <>
              <div style={countsStyle}>
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
                            <div style={{ marginTop: theme.spacing(0.5) }}>{rowMessage?.(row)}</div>
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
          <CSVLink
            data={skippedRowsToCells(skippedRows, report.import_columns)}
            headers={report.import_columns}
            filename={downloadFileName}
          >
            <Button type="button" priority="secondary">
              {t('Download skipped rows')}
            </Button>
          </CSVLink>
        )}
        <Button type="button" onClick={onClose}>
          {t('Close')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default ImportReportDialog;
