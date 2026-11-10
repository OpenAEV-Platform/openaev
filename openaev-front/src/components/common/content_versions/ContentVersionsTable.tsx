import { Chip, IconButton, Text } from '@filigran/design-system';
import { ExpandLess, ExpandMore } from '@mui/icons-material';
// fds:keep-mui the library has no Table yet (Figma DS Table 8582:34772 only); dressed to its spec by FDS_TABLE_SX
import { Skeleton, Table, TableBody, TableCell, TableHead, TableRow } from '@mui/material';
import { Fragment, type FunctionComponent, type ReactNode, useState } from 'react';
import { Link } from 'react-router';

import { type ContentHistoryOutput } from '../../../utils/api-types';
import { useFormatter } from '../../i18n';
import { FDS_TABLE_FRAME_STYLE, FDS_TABLE_SX } from '../fdsTableSpec';
import FdsPagination from '../pagination/FdsPagination';
import BeforeAfter from '../update/BeforeAfter';
import DiffLegend from '../update/DiffLegend';

// Shape of content_history_details, a free JSON object on the API side.
export interface HistoryChange {
  field: string;
  before?: unknown;
  after?: unknown;
}

interface HistoryDetails {
  changes?: HistoryChange[];
  choices?: Record<string, string>;
  reason?: string;
  repository?: string;
  changelog?: string;
  compared_to?: string;
  source?: string;
}

const HISTORY_PER_PAGE = 10;

const historyDetails = (entry: ContentHistoryOutput) => entry.content_history_details as HistoryDetails | undefined;

// Placeholder rows while a table loads, as the paginated lists show.
export const SkeletonRows: FunctionComponent<{
  rows: number;
  columns: number;
}> = ({ rows, columns }) => (
  <>
    {[...Array(rows)].map((_, row) => (
      <TableRow key={row}>
        {[...Array(columns)].map((__, column) => (
          <TableCell key={column}><Skeleton height={24} /></TableCell>
        ))}
      </TableRow>
    ))}
  </>
);

export interface ContentVersionRow {
  // Null for a local copy, which has no version.
  version?: string | null;
  // Where the version opens, when it is another installed copy (scenarios).
  link?: string;
  current?: boolean;
  latest?: boolean;
  installed?: boolean;
  revoked?: boolean;
  changelog?: string | null;
  publishedAt?: string | null;
  usage?: string;
  action?: ReactNode;
}

interface VersionsProps {
  rows: ContentVersionRow[];
  usageLabel: string;
  loading?: boolean;
}

// F7: the same versions table for payloads and scenarios.
export const ContentVersionsTable: FunctionComponent<VersionsProps> = ({ rows, usageLabel, loading = false }) => {
  const { t, nsdt } = useFormatter();
  return (
    <Table size="small" aria-label={t('Versions')} sx={FDS_TABLE_SX}>
      <TableHead>
        <TableRow>
          <TableCell>{t('Version')}</TableCell>
          <TableCell>{t('Status')}</TableCell>
          <TableCell>{t('Changelog')}</TableCell>
          <TableCell>{t('Published')}</TableCell>
          <TableCell>{usageLabel}</TableCell>
          <TableCell />
        </TableRow>
      </TableHead>
      <TableBody>
        {loading && rows.length === 0 && <SkeletonRows rows={3} columns={6} />}
        {rows.map((row, index) => {
          const label = row.version ? `v${row.version}` : t('Local copy');
          return (
            <TableRow key={row.version ?? index}>
              <TableCell>{row.link ? <Link to={row.link}>{label}</Link> : label}</TableCell>
              <TableCell>
                <div style={{
                  display: 'flex',
                  gap: 4,
                  flexWrap: 'wrap',
                }}
                >
                  {row.current && <Chip label={t('Current version')} />}
                  {row.latest && <Chip label={t('Latest version')} severity="low" />}
                  {row.installed && !row.current && <Chip label={t('Installed')} severity="low" />}
                  {!row.installed && <Chip label={t('Not installed')} />}
                  {row.revoked && <Chip label={t('Revoked')} severity="critical" />}
                </div>
              </TableCell>
              <TableCell>{row.changelog ?? '-'}</TableCell>
              <TableCell>{row.publishedAt ? nsdt(row.publishedAt) : '-'}</TableCell>
              <TableCell>{row.usage ?? '-'}</TableCell>
              <TableCell align="right">{row.action}</TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
};

const HISTORY_ACTION_LABELS: Record<string, string> = {
  INSTALL: 'Installed',
  UPDATE: 'Updated',
  ROLLBACK: 'Rolled back',
  DUPLICATE: 'Duplicated',
  VERSION_CHANGE: 'Version changed',
  UNINSTALL: 'Uninstalled',
  REVOKE: 'Revoked',
  LIFT_REVOCATION: 'Revocation lifted',
  CUSTOMIZE: 'Customized',
  OVERRIDE: 'Customization changed',
  REBASE: 'Rebased',
  CONFLICT: 'Conflict with a new version',
};

const FIELD_LABELS: Record<string, string> = {
  payload_name: 'Name',
  payload_description: 'Description',
  payload_platforms: 'Platforms',
  payload_execution_arch: 'Architecture',
  command_executor: 'Executor',
  command_content: 'Command',
  dns_resolution_hostname: 'Hostname',
  executable_file: 'Executable file',
  file_drop_file: 'File to drop',
  payload_arguments: 'Arguments',
  payload_prerequisites: 'Prerequisites',
  payload_cleanup_executor: 'Cleanup executor',
  payload_cleanup_command: 'Cleanup command',
};

// What the payload runs, compared line by line like code (same block as the Versions tab).
const CODE_FIELDS = new Set(['command_content', 'payload_cleanup_command', 'payload_arguments', 'payload_prerequisites']);

const CHOICE_LABELS: Record<string, string> = {
  KEEP_MINE: 'kept mine',
  TAKE_CATALOG: 'took Filigran\'s',
  EDIT: 'edited',
};

const BLOCK_LABELS: Record<string, string> = {
  EXECUTION: 'Execution',
  PLATFORMS: 'Platforms',
};

// Empty stays empty, so the Before / After block shows "(empty)".
const valueText = (value: unknown) => {
  if (value === null || value === undefined || value === '') {
    return '';
  }
  return typeof value === 'string' ? value : JSON.stringify(value, null, 2);
};

// What changed, field by field, in the same Before / After block as the Versions tab: labelled rows,
// removed words struck through on grey, changed words on a brand tint.
export const ChangesTable: FunctionComponent<{
  changes: HistoryChange[];
  beforeLabel?: string;
  afterLabel?: string;
}> = ({ changes, beforeLabel, afterLabel }) => {
  const { t } = useFormatter();
  const before = beforeLabel ?? String(t('Before'));
  const after = afterLabel ?? String(t('After'));
  return (
    <div
      aria-label={t('Changes')}
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 12,
      }}
    >
      {/* One key for the whole change, then one block per field (no key repeated on each). */}
      <DiffLegend beforeLabel={before} afterLabel={after} />
      {changes.map(change => (
        <BeforeAfter
          key={change.field}
          label={t(FIELD_LABELS[change.field] ?? change.field)}
          before={valueText(change.before)}
          after={valueText(change.after)}
          beforeLabel={before}
          afterLabel={after}
          code={CODE_FIELDS.has(change.field)}
          legend={false}
        />
      ))}
    </div>
  );
};

interface HistoryProps {
  history: ContentHistoryOutput[];
  loading?: boolean;
}

export const ContentHistoryTable: FunctionComponent<HistoryProps> = ({ history, loading = false }) => {
  const { t, fsd, ft } = useFormatter();
  const [opened, setOpened] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  // The row reads version first (what the reader scans for), then what happened under it.
  const versionLine = (entry: ContentHistoryOutput) => {
    if (entry.content_history_from_version) {
      return `v${entry.content_history_from_version} → v${entry.content_history_version ?? '-'}`;
    }
    return entry.content_history_version ? `v${entry.content_history_version}` : null;
  };
  const actionLine = (entry: ContentHistoryOutput) => {
    if (entry.content_history_action === 'VERSION_CHANGE' && entry.content_history_count != null) {
      return t('{count, plural, one {# inject moved} other {# injects moved}}', { count: String(entry.content_history_count) });
    }
    return t(HISTORY_ACTION_LABELS[entry.content_history_action ?? ''] ?? entry.content_history_action ?? '-');
  };
  const twoLines = (first: ReactNode, second: ReactNode) => (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 2,
    }}
    >
      <Text variant="content-compact-bold">{first}</Text>
      <Text variant="content-compact" className="text-default-secondary">{second}</Text>
    </div>
  );

  // One line on what the entry changed; the field by field detail opens below it.
  const summary = (entry: ContentHistoryOutput) => {
    const details = historyDetails(entry);
    const parts: string[] = [];
    if (details?.changelog) {
      parts.push(details.changelog);
    }
    if (details?.changes && details.changes.length > 0) {
      const fields = details.changes.map(change => t(FIELD_LABELS[change.field] ?? change.field)).join(', ');
      parts.push(details.compared_to
        ? t('Changed since v{version}: {fields}', {
            version: details.compared_to,
            fields,
          })
        : fields);
    } else if (details?.changes) {
      parts.push(t('Metadata only'));
    }
    Object.entries(details?.choices ?? {}).forEach(([block, choice]) => {
      parts.push(`${t(BLOCK_LABELS[block] ?? block)}: ${t(CHOICE_LABELS[choice] ?? choice)}`);
    });
    if (entry.content_history_action === 'INSTALL') {
      parts.push(entry.content_history_automatic ? t('Automatic') : t('By hand'));
      if (details?.repository) {
        parts.push(details.repository);
      }
    }
    // A user's revocation shows its user; a list's comes from Filigran.
    if ((entry.content_history_action === 'REVOKE' || entry.content_history_action === 'LIFT_REVOCATION')
      && details?.source === 'repository') {
      parts.push(t('By Filigran'));
    }
    if (details?.reason) {
      parts.push(`${t('Reason')}: ${details.reason}`);
    }
    return parts.length > 0 ? parts.join(' · ') : '-';
  };
  return (
    <>
      {/* Fixed layout: opening a long before/after value must not resize the columns. */}
      <div style={FDS_TABLE_FRAME_STYLE}>
        <Table
          size="small"
          aria-label={t('History')}

          sx={{
            ...FDS_TABLE_SX,
            'tableLayout': 'fixed',
            '& tr:last-child > td': { borderBottom: 0 },
          }}
        >
          <TableHead>
            <TableRow>
              <TableCell sx={{ width: '24%' }}>{t('History')}</TableCell>
              <TableCell>{t('Changes')}</TableCell>
              <TableCell sx={{ width: '14%' }}>{t('User')}</TableCell>
              <TableCell sx={{ width: '16%' }}>{t('Date')}</TableCell>
              <TableCell sx={{ width: 48 }} />
            </TableRow>
          </TableHead>
          <TableBody>
            {loading && history.length === 0 && <SkeletonRows rows={3} columns={5} />}
            {!loading && history.length === 0 && (
              <TableRow>
                <TableCell colSpan={5}>{t('No history yet')}</TableCell>
              </TableRow>
            )}
            {history.slice(page * HISTORY_PER_PAGE, (page + 1) * HISTORY_PER_PAGE).map((entry) => {
              const changes = historyDetails(entry)?.changes ?? [];
              const open = opened === entry.content_history_id;
              return (
                <Fragment key={entry.content_history_id}>
                  {/* Every cell starts on the same baseline (version, changes, user, date); the expand button stays centred. */}
                  <TableRow sx={{
                    '& > td': { verticalAlign: 'baseline' },
                    '& > td:last-of-type': { verticalAlign: 'middle' },
                  }}
                  >
                    <TableCell>
                      {versionLine(entry)
                        ? twoLines(versionLine(entry), actionLine(entry))
                        : <Text variant="content-compact-bold">{actionLine(entry)}</Text>}
                    </TableCell>
                    <TableCell sx={{ overflowWrap: 'anywhere' }}>{summary(entry)}</TableCell>
                    <TableCell>{entry.content_history_user ?? (entry.content_history_automatic ? t('Platform') : '-')}</TableCell>
                    <TableCell>
                      {entry.content_history_created_at
                        ? (
                            <div style={{
                              display: 'flex',
                              flexDirection: 'column',
                              gap: 2,
                            }}
                            >
                              <Text variant="content-compact">{fsd(entry.content_history_created_at)}</Text>
                              <Text variant="content-compact" className="text-default-secondary">{ft(entry.content_history_created_at)}</Text>
                            </div>
                          )
                        : '-'}
                    </TableCell>
                    <TableCell align="right">
                      {changes.length > 0 && (
                        <IconButton
                          priority="tertiary"
                          size="sm"
                          icon={open ? <ExpandLess fontSize="small" /> : <ExpandMore fontSize="small" />}
                          aria-label={open ? t('Hide the changes') : t('Show the changes')}
                          onClick={() => setOpened(open ? null : entry.content_history_id)}
                        />
                      )}
                    </TableCell>
                  </TableRow>
                  {open && (
                  // The field by field change, on the page itself: same as the Versions tab (DS borders only).
                    <TableRow>
                      <TableCell colSpan={5} sx={{ padding: '16px !important' }}>
                        <ChangesTable
                          changes={changes}
                          beforeLabel={entry.content_history_from_version ? String(t('Before · v{version}', { version: entry.content_history_from_version })) : undefined}
                          afterLabel={entry.content_history_version ? String(t('After · v{version}', { version: entry.content_history_version })) : undefined}
                        />
                      </TableCell>
                    </TableRow>
                  )}
                </Fragment>
              );
            })}
          </TableBody>
        </Table>
      </div>
      {history.length > HISTORY_PER_PAGE && (
        <div style={{
          display: 'flex',
          justifyContent: 'flex-end',
          paddingTop: 12,
        }}
        >
          <FdsPagination count={history.length} page={page} onPageChange={setPage} rowsPerPage={HISTORY_PER_PAGE} />
        </div>
      )}
    </>
  );
};
