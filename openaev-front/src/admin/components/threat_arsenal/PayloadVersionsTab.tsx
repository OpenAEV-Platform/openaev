import { Chip } from '@filigran/design-system';
import { Table, TableBody, TableCell, TableHead, TableRow } from '@mui/material';
import { type FunctionComponent, useEffect, useState } from 'react';

import { fetchThreatArsenalHistory, fetchThreatArsenalVersions } from '../../../actions/threat_arsenals/threatArsenal-version-actions';
import { useFormatter } from '../../../components/i18n';
import { type ContentHistoryOutput, type PayloadVersionOutput } from '../../../utils/api-types';

interface Props { actionId: string }

const HISTORY_ACTION_LABELS: Record<string, string> = {
  INSTALL: 'Installed',
  UPDATE: 'Updated',
  ROLLBACK: 'Rolled back',
  DUPLICATE: 'Duplicated',
  VERSION_CHANGE: 'Version changed',
  UNINSTALL: 'Uninstalled',
  REVOKE: 'Revoked',
  LIFT_REVOCATION: 'Revocation lifted',
};

// F7: every version of the payload, then who did what and when.
const PayloadVersionsTab: FunctionComponent<Props> = ({ actionId }) => {
  const { t, nsdt } = useFormatter();
  const [versions, setVersions] = useState<PayloadVersionOutput[]>([]);
  const [history, setHistory] = useState<ContentHistoryOutput[]>([]);

  useEffect(() => {
    fetchThreatArsenalVersions(actionId).then(response => setVersions(response.data as PayloadVersionOutput[]));
    fetchThreatArsenalHistory(actionId).then(response => setHistory(response.data as ContentHistoryOutput[]));
  }, [actionId]);

  const historyLabel = (entry: ContentHistoryOutput) => {
    const label = t(HISTORY_ACTION_LABELS[entry.content_history_action ?? ''] ?? entry.content_history_action ?? '-');
    return entry.content_history_from_version
      ? `${label} (v${entry.content_history_from_version} → v${entry.content_history_version})`
      : label;
  };

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 24,
    }}
    >
      <Table size="small" aria-label={t('Versions')}>
        <TableHead>
          <TableRow>
            <TableCell>{t('Version')}</TableCell>
            <TableCell>{t('Status')}</TableCell>
            <TableCell>{t('Created')}</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {versions.map(version => (
            <TableRow key={version.payload_id}>
              <TableCell>{version.payload_version ? `v${version.payload_version}` : t('Local copy')}</TableCell>
              <TableCell>
                <div style={{
                  display: 'flex',
                  gap: 4,
                  flexWrap: 'wrap',
                }}
                >
                  {version.payload_current && <Chip label={t('Current version')} />}
                  {version.payload_is_latest && <Chip label={t('Latest version')} severity="info" />}
                  {version.payload_revoked && <Chip label={t('Revoked')} severity="critical" />}
                </div>
              </TableCell>
              <TableCell>{version.payload_created_at ? nsdt(version.payload_created_at) : '-'}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <Table size="small" aria-label={t('History')}>
        <TableHead>
          <TableRow>
            <TableCell>{t('History')}</TableCell>
            <TableCell>{t('User')}</TableCell>
            <TableCell>{t('Date')}</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {history.length === 0 && (
            <TableRow>
              <TableCell colSpan={3}>{t('No history yet')}</TableCell>
            </TableRow>
          )}
          {history.map(entry => (
            <TableRow key={entry.content_history_id}>
              <TableCell>{historyLabel(entry)}</TableCell>
              <TableCell>{entry.content_history_user ?? '-'}</TableCell>
              <TableCell>{entry.content_history_created_at ? nsdt(entry.content_history_created_at) : '-'}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
};

export default PayloadVersionsTab;
