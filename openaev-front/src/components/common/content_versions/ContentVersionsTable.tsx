import { Chip, IconButton } from '@filigran/design-system';
import { ExpandLess, ExpandMore } from '@mui/icons-material';
import { Skeleton, Table, TableBody, TableCell, TableHead, TableRow } from '@mui/material';
import { Fragment, type FunctionComponent, type ReactNode, useState } from 'react';
import { Link } from 'react-router';

import { type ContentHistoryOutput } from '../../../utils/api-types';
import { useFormatter } from '../../i18n';
import TextDiff from '../update/TextDiff';

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
  list_serial?: number;
}

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
    <Table size="small" aria-label={t('Versions')}>
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
                  {row.latest && <Chip label={t('Latest version')} severity="info" />}
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

const CHOICE_LABELS: Record<string, string> = {
  KEEP_MINE: 'kept mine',
  TAKE_CATALOG: 'took Filigran\'s',
  EDIT: 'edited',
};

const BLOCK_LABELS: Record<string, string> = {
  EXECUTION: 'Execution',
  PLATFORMS: 'Platforms',
};

const valueText = (value: unknown) => {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  return typeof value === 'string' ? value : JSON.stringify(value, null, 2);
};

// Field, before, after: words that changed between two texts are highlighted.
export const ChangesTable: FunctionComponent<{ changes: HistoryChange[] }> = ({ changes }) => {
  const { t } = useFormatter();
  const cell = {
    whiteSpace: 'pre-wrap' as const,
    overflowWrap: 'anywhere' as const,
    fontFamily: 'monospace',
    fontSize: 12,
    verticalAlign: 'top',
  };
  return (
    <Table size="small" aria-label={t('Changes')} sx={{ tableLayout: 'fixed' }}>
      <TableHead>
        <TableRow>
          <TableCell sx={{ width: '20%' }}>{t('Field')}</TableCell>
          <TableCell sx={{ width: '40%' }}>{t('Before')}</TableCell>
          <TableCell sx={{ width: '40%' }}>{t('After')}</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {changes.map(change => (
          <TableRow key={change.field}>
            <TableCell sx={{ verticalAlign: 'top' }}>{t(FIELD_LABELS[change.field] ?? change.field)}</TableCell>
            <TableCell sx={cell}>
              {typeof change.before === 'string' && typeof change.after === 'string'
                ? <TextDiff text={change.before} other={change.after} />
                : valueText(change.before)}
            </TableCell>
            <TableCell sx={cell}>
              {typeof change.before === 'string' && typeof change.after === 'string'
                ? <TextDiff text={change.after} other={change.before} />
                : valueText(change.after)}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
};

interface HistoryProps {
  history: ContentHistoryOutput[];
  loading?: boolean;
}

export const ContentHistoryTable: FunctionComponent<HistoryProps> = ({ history, loading = false }) => {
  const { t, nsdt } = useFormatter();
  const [opened, setOpened] = useState<string | null>(null);
  const label = (entry: ContentHistoryOutput) => {
    const action = t(HISTORY_ACTION_LABELS[entry.content_history_action ?? ''] ?? entry.content_history_action ?? '-');
    if (entry.content_history_action === 'VERSION_CHANGE' && entry.content_history_count != null) {
      return t('{count} injects moved from v{from} to v{to}', {
        count: String(entry.content_history_count),
        from: entry.content_history_from_version ?? '-',
        to: entry.content_history_version ?? '-',
      });
    }
    if (entry.content_history_from_version) {
      return `${action} (v${entry.content_history_from_version} → v${entry.content_history_version})`;
    }
    return entry.content_history_version ? `${action} v${entry.content_history_version}` : action;
  };
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
    if (entry.content_history_action === 'REVOKE' || entry.content_history_action === 'LIFT_REVOCATION') {
      if (details?.source !== 'repository') {
        parts.push(t('In this tenant'));
      } else if (details.list_serial != null) {
        parts.push(t('Revocation list #{serial} of {repository}', {
          serial: String(details.list_serial),
          repository: details.repository ?? '-',
        }));
      } else {
        parts.push(t('Revocation list of {repository}', { repository: details.repository ?? '-' }));
      }
    }
    if (details?.reason) {
      parts.push(details.reason);
    }
    return parts.length > 0 ? parts.join(' · ') : '-';
  };
  return (
    // Fixed layout: opening a long before/after value must not resize the columns.
    <Table
      size="small"
      aria-label={t('History')}
      sx={{
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
        {history.map((entry) => {
          const changes = historyDetails(entry)?.changes ?? [];
          const open = opened === entry.content_history_id;
          return (
            <Fragment key={entry.content_history_id}>
              <TableRow>
                <TableCell>{label(entry)}</TableCell>
                <TableCell sx={{ overflowWrap: 'anywhere' }}>{summary(entry)}</TableCell>
                <TableCell>{entry.content_history_user ?? (entry.content_history_automatic ? t('Platform') : '-')}</TableCell>
                <TableCell>{entry.content_history_created_at ? nsdt(entry.content_history_created_at) : '-'}</TableCell>
                <TableCell align="right">
                  {changes.length > 0 && (
                    <IconButton
                      icon={open ? <ExpandLess fontSize="small" /> : <ExpandMore fontSize="small" />}
                      aria-label={open ? t('Hide the changes') : t('Show the changes')}
                      onClick={() => setOpened(open ? null : entry.content_history_id)}
                    />
                  )}
                </TableCell>
              </TableRow>
              {open && (
                <TableRow>
                  <TableCell colSpan={5}>
                    <ChangesTable changes={changes} />
                  </TableCell>
                </TableRow>
              )}
            </Fragment>
          );
        })}
      </TableBody>
    </Table>
  );
};
