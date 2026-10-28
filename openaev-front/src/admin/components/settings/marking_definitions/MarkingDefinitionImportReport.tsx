import { Button, Chip } from '@filigran/design-system';
// fds:keep-mui Dialog and Alert have no library equivalent yet
import { Alert, Dialog, DialogActions, DialogContent, DialogTitle, useTheme } from '@mui/material';
import { type CSSProperties, type FunctionComponent } from 'react';

import { USE_MOCK_IMPORT_EXPORT } from '../../../../actions/marking_definitions/marking-definition-actions';
import { toMarkingDefinitionsCsv } from '../../../../actions/marking_definitions/marking-definition-csv';
import {
  type MarkingDefinitionImportResult,
  type MarkingDefinitionImportRow,
  type MarkingDefinitionImportRowReason,
} from '../../../../actions/marking_definitions/marking-definition-import-types';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { download } from '../../../../utils/utils';

interface Props {
  open: boolean;
  onClose: () => void;
  result: MarkingDefinitionImportResult | null;
  fileError: string | null;
}

// A conflict with data that already exists (or repeats in the file) reads orange, a malformed
// value reads red.
const CONFLICT_REASONS: MarkingDefinitionImportRowReason[] = ['ALREADY_EXISTS', 'DUPLICATE_IN_FILE'];

const MarkingDefinitionImportReport: FunctionComponent<Props> = ({ open, onClose, result, fileError }) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const reasonLabels: Record<MarkingDefinitionImportRowReason, string> = {
    MISSING_FIELD: t('Missing value'),
    INVALID_COLOR: t('Invalid colour'),
    INVALID_ORDER: t('Invalid order'),
    DUPLICATE_IN_FILE: t('Duplicate in file'),
    ALREADY_EXISTS: t('Already exists'),
  };

  const skippedRows: MarkingDefinitionImportRow[] = result?.import_rows.filter(row => row.import_row_status === 'SKIPPED') ?? [];

  // Same format as the import, so the user fixes the rows and imports the file again.
  const downloadSkippedRows = () => {
    const csv = toMarkingDefinitionsCsv(skippedRows.map(row => [
      row.import_row_type ?? '',
      row.import_row_definition ?? '',
      row.import_row_color ?? '',
      row.import_row_order ?? '',
    ]));
    download(csv, 'MarkingDefinitionsSkipped.csv', 'text/csv');
  };

  const cellStyle: CSSProperties = {
    padding: '8px 12px',
    textAlign: 'left',
    verticalAlign: 'top',
    borderBottom: `1px solid ${theme.palette.divider}`,
  };

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="md"
      fullWidth
      slotProps={{ paper: { elevation: 1 } }}
      slots={{ transition: Transition }}
    >
      <DialogTitle>{t('Import marking definitions')}</DialogTitle>
      <DialogContent>
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
        }}
        >
          {USE_MOCK_IMPORT_EXPORT && (
            <Alert severity="info">
              {t('Mock backend: the file was checked with the import rules, but nothing was saved.')}
            </Alert>
          )}
          {fileError && (
            <Alert severity="error">
              {`${t('The file was not imported.')} ${fileError}`}
            </Alert>
          )}
          {result && (
            <>
              <div style={{
                display: 'flex',
                gap: 8,
              }}
              >
                <Chip label={`${result.import_created} ${t('imported')}`} severity={result.import_created > 0 ? 'low' : 'neutral'} />
                <Chip label={`${result.import_skipped} ${t('skipped')}`} severity={result.import_skipped > 0 ? 'critical' : 'neutral'} />
              </div>
              {skippedRows.length > 0 && (
                <Alert severity="warning">
                  {t('The rows below were not imported and nothing existing was overwritten. Fix them in your file and import it again, or edit the existing marking definition.')}
                </Alert>
              )}
              {skippedRows.length > 0 && (
                <div style={{ overflowX: 'auto' }}>
                  <table style={{
                    width: '100%',
                    borderCollapse: 'collapse',
                  }}
                  >
                    <thead>
                      <tr>
                        <th style={cellStyle}>{t('Line')}</th>
                        <th style={cellStyle}>{t('Type')}</th>
                        <th style={cellStyle}>{t('Definition')}</th>
                        <th style={cellStyle}>{t('Color')}</th>
                        <th style={cellStyle}>{t('Order')}</th>
                        <th style={cellStyle}>{t('Reason')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {skippedRows.map(row => (
                        <tr key={row.import_row_line}>
                          <td style={cellStyle}>{row.import_row_line}</td>
                          <td style={cellStyle}>{row.import_row_type || '-'}</td>
                          <td style={cellStyle}>{row.import_row_definition || '-'}</td>
                          <td style={cellStyle}>{row.import_row_color || '-'}</td>
                          <td style={cellStyle}>{row.import_row_order || '-'}</td>
                          <td style={cellStyle}>
                            {row.import_row_reason && (
                              <Chip
                                label={reasonLabels[row.import_row_reason]}
                                severity={CONFLICT_REASONS.includes(row.import_row_reason) ? 'medium' : 'critical'}
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
        {skippedRows.length > 0 && (
          <Button type="button" priority="secondary" onClick={downloadSkippedRows}>
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

export default MarkingDefinitionImportReport;
