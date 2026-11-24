import { IconButton, Skeleton, Text } from '@filigran/design-system';
import { ExpandLess, ExpandMore } from '@mui/icons-material';
// fds:keep-mui the library has no Table yet
import { Table, TableBody, TableCell, TableHead, TableRow } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Fragment, type FunctionComponent, type ReactNode, useState } from 'react';

import { type CatalogHistoryOutput } from '../../../utils/api-types';
import { useFormatter } from '../../i18n';
import { FDS_TABLE_SX, fdsTableFrameStyle, fdsTableSectionStyle } from '../fdsTableSpec';
import { LIB_SURFACE_LAYER } from '../libSurfaceBorder';

const ACTION_LABELS: Record<CatalogHistoryOutput['catalog_history_action'], string> = {
  INSTALL: 'Installed',
  UNINSTALL: 'Uninstalled',
  REVOKE: 'Revoked',
  LIFT_REVOCATION: 'Revocation lifted',
  UPDATE: 'Updated',
  ROLLBACK: 'Rolled back',
  SAVE: 'Saved',
};

/** What an entry changed, when the content says it: the version it came from, the fields, the before / after. */
export interface CatalogHistoryChanges {
  fromVersion: string;
  // The fields changed, in one line.
  summary: ReactNode;
  // The field by field before / after, opened under the row.
  details: ReactNode;
}

interface Props {
  history: CatalogHistoryOutput[] | null;
  // With it, a Changes column and rows that open on their before / after; null for an entry with none.
  changesOf?: (entry: CatalogHistoryOutput) => CatalogHistoryChanges | null;
}

// What happened to a catalogue content, newest first. A row reads its version first (what the reader scans
// for), then what happened; the date, then its time.
const CatalogHistoryTable: FunctionComponent<Props> = ({ history, changesOf }) => {
  const { t, fsd, ft } = useFormatter();
  const theme = useTheme();
  const [opened, setOpened] = useState<string | null>(null);

  if (history === null) {
    return <Skeleton className="h-24" />;
  }
  const author = (entry: CatalogHistoryOutput) => (entry.catalog_history_mode === 'AUTO'
    ? t('Automatic')
    : entry.catalog_history_user ?? '-');
  const twoLines = (first: ReactNode, second: ReactNode, bold = false) => (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(0.25),
    }}
    >
      <Text variant={bold ? 'content-compact-bold' : 'content-compact'}>{first}</Text>
      <Text variant="content-compact" className="text-default-secondary">{second}</Text>
    </div>
  );
  const columns = changesOf ? 5 : 3;
  return (
    <div className={LIB_SURFACE_LAYER} style={fdsTableFrameStyle(theme)}>
      {history.length === 0
        ? (
            <div style={fdsTableSectionStyle(theme)}>
              <Text variant="content-compact" className="text-default-secondary">{t('No history yet')}</Text>
            </div>
          )
        : (
            <Table size="small" sx={FDS_TABLE_SX} aria-label={t('History')}>
              <TableHead>
                <TableRow>
                  <TableCell>{t('History')}</TableCell>
                  {changesOf && <TableCell>{t('Changes')}</TableCell>}
                  <TableCell>{t('User')}</TableCell>
                  <TableCell>{t('Date')}</TableCell>
                  {changesOf && <TableCell />}
                </TableRow>
              </TableHead>
              <TableBody>
                {history.map((entry) => {
                  const changes = changesOf?.(entry) ?? null;
                  const open = opened === entry.catalog_history_id;
                  const version = changes
                    ? `v${changes.fromVersion} → v${entry.catalog_history_version}`
                    : `v${entry.catalog_history_version}`;
                  return (
                    <Fragment key={entry.catalog_history_id}>
                      {/* Every cell starts on the same line; the open arrow stays centred. */}
                      <TableRow sx={{
                        '& > td': { verticalAlign: 'top' },
                        '& > td:last-of-type': { verticalAlign: 'middle' },
                      }}
                      >
                        <TableCell>{twoLines(version, t(ACTION_LABELS[entry.catalog_history_action]), true)}</TableCell>
                        {changesOf && <TableCell>{changes ? changes.summary : '-'}</TableCell>}
                        <TableCell>{author(entry)}</TableCell>
                        <TableCell>{twoLines(fsd(entry.catalog_history_created_at), ft(entry.catalog_history_created_at))}</TableCell>
                        {changesOf && (
                          <TableCell align="right" sx={{ width: theme.spacing(6) }}>
                            {changes && (
                              <IconButton
                                priority="tertiary"
                                size="sm"
                                // White like the Versions rows' arrow (the tertiary button would paint it blue).
                                icon={open
                                  ? <ExpandLess fontSize="small" style={{ color: 'var(--text-default-primary)' }} />
                                  : <ExpandMore fontSize="small" style={{ color: 'var(--text-default-primary)' }} />}
                                aria-label={open ? t('Hide the changes') : t('Show the changes')}
                                aria-expanded={open}
                                onClick={() => setOpened(open ? null : entry.catalog_history_id)}
                              />
                            )}
                          </TableCell>
                        )}
                      </TableRow>
                      {/* The field by field change, the same before / after block as everywhere else. */}
                      {open && changes && (
                        <TableRow>
                          <TableCell colSpan={columns} sx={{ padding: `${theme.spacing(2)} !important` }}>{changes.details}</TableCell>
                        </TableRow>
                      )}
                    </Fragment>
                  );
                })}
              </TableBody>
            </Table>
          )}
    </div>
  );
};

export default CatalogHistoryTable;
