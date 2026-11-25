import { IconButton, Skeleton, Text } from '@filigran/design-system';
// fds:keep-mui the library has no Table yet
import { Table, TableBody, TableCell, TableHead, TableRow } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Fragment, type ReactNode, useState } from 'react';

import { type ContentChangeOutput } from '../../../utils/api-types';
import { useFormatter } from '../../i18n';
import { FDS_TABLE_SX, fdsTableFrameStyle, fdsTableSectionStyle } from '../fdsTableSpec';
import { FoldArrow } from '../FoldRow';
import { LIB_SURFACE_LAYER } from '../libSurfaceBorder';
import TablePaginationComponentV2 from '../queryable/pagination/TablePaginationComponentV2';
import { DEFAULT_ROWS_PER_PAGE } from '../queryable/pagination/usePaginationState';
import { type ContentHistory } from './useContentHistory';

const ACTION_LABELS: Record<ContentChangeOutput['content_change_action'], string> = {
  INSTALL: 'Installed',
  UNINSTALL: 'Uninstalled',
  REVOKE: 'Revoked',
  LIFT_REVOCATION: 'Revocation lifted',
  UPDATE: 'Updated',
  ROLLBACK: 'Rolled back',
  SAVE: 'Saved',
};

/** What an entry changed, when the content says it: the version it came from, the fields, the before / after. */
export interface ContentHistoryChanges {
  fromVersion: string;
  // The fields changed, in one line.
  summary: ReactNode;
  // The field by field before / after, opened under the row.
  details: ReactNode;
}

interface Props<T extends ContentChangeOutput> {
  history: ContentHistory<T>;
  // With it, a Changes column and rows that open on their before / after; null for an entry with none.
  changesOf?: (entry: T) => ContentHistoryChanges | null;
}

// What happened to a catalogue content, newest first. A row reads its version first (what the reader scans
// for), then what happened; the date, then its time.
const ContentHistoryTable = <T extends ContentChangeOutput>({ history: { entries, pagination }, changesOf }: Props<T>) => {
  const { t, fsd, ft } = useFormatter();
  const theme = useTheme();
  const [opened, setOpened] = useState<string | null>(null);

  if (entries === null) {
    return <Skeleton className="h-24" />;
  }
  const author = (entry: ContentChangeOutput) => (entry.content_change_mode === 'AUTO'
    ? t('Automatic')
    : entry.content_change_user ?? '-');
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
      {entries.length === 0
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
                {entries.map((entry) => {
                  const changes = changesOf?.(entry) ?? null;
                  const open = opened === entry.content_change_id;
                  const version = changes
                    ? `v${changes.fromVersion} → v${entry.content_change_version}`
                    : `v${entry.content_change_version}`;
                  return (
                    <Fragment key={entry.content_change_id}>
                      {/* Every cell starts on the same line; the open arrow stays centred. */}
                      <TableRow sx={{
                        '& > td': { verticalAlign: 'top' },
                        '& > td:last-of-type': { verticalAlign: 'middle' },
                      }}
                      >
                        <TableCell>{twoLines(version, t(ACTION_LABELS[entry.content_change_action]), true)}</TableCell>
                        {changesOf && <TableCell>{changes ? changes.summary : '-'}</TableCell>}
                        <TableCell>{author(entry)}</TableCell>
                        <TableCell>{twoLines(fsd(entry.content_change_created_at), ft(entry.content_change_created_at))}</TableCell>
                        {changesOf && (
                          <TableCell align="right" sx={{ width: theme.spacing(6) }}>
                            {changes && (
                              <IconButton
                                priority="tertiary"
                                size="sm"
                                // FDS-WORKAROUND: white like the Versions rows' arrow, the tertiary IconButton paints it blue
                                // — remove when the library IconButton has a neutral colour — see the History arrow note in this PR
                                icon={<FoldArrow open={open} neutral />}
                                aria-label={open ? t('Hide the changes') : t('Show the changes')}
                                aria-expanded={open}
                                onClick={() => setOpened(open ? null : entry.content_change_id)}
                              />
                            )}
                          </TableCell>
                        )}
                      </TableRow>
                      {/* The field by field change, the same before / after block as everywhere else. */}
                      {open && changes && (
                        // `&&` outweighs the table's own cell padding.
                        <TableRow sx={{ '&& > td': { p: 2 } }}>
                          <TableCell colSpan={columns}>{changes.details}</TableCell>
                        </TableRow>
                      )}
                    </Fragment>
                  );
                })}
              </TableBody>
            </Table>
          )}
      {pagination.getTotalElements() > DEFAULT_ROWS_PER_PAGE && (
        <TablePaginationComponentV2 page={pagination.page} size={pagination.elementsPerPage} paginationHelpers={pagination} />
      )}
    </div>
  );
};

export default ContentHistoryTable;
