import { Skeleton, Text } from '@filigran/design-system';
// fds:keep-mui the library has no Table yet
import { Table, TableBody, TableCell, TableHead, TableRow } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { type CatalogHistoryOutput } from '../../../utils/api-types';
import { useFormatter } from '../../i18n';
import { FDS_TABLE_SX, fdsTableFrameStyle, fdsTableSectionStyle } from '../fdsTableSpec';

const ACTION_LABELS: Record<CatalogHistoryOutput['catalog_history_action'], string> = {
  INSTALL: 'Installed',
  UNINSTALL: 'Uninstalled',
  REVOKE: 'Revoked',
  LIFT_REVOCATION: 'Revocation lifted',
};

interface Props { history: CatalogHistoryOutput[] | null }

const CatalogHistoryTable: FunctionComponent<Props> = ({ history }) => {
  const { t, nsdt } = useFormatter();
  const theme = useTheme();

  if (history === null) {
    return <Skeleton className="h-24" />;
  }
  const author = (entry: CatalogHistoryOutput) => (entry.catalog_history_mode === 'AUTO'
    ? t('Automatic')
    : entry.catalog_history_user ?? '-');
  return (
    <div style={fdsTableFrameStyle(theme)}>
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
                  <TableCell>{t('Action')}</TableCell>
                  <TableCell>{t('Version')}</TableCell>
                  <TableCell>{t('User')}</TableCell>
                  <TableCell>{t('Date')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {history.map(entry => (
                  <TableRow key={entry.catalog_history_id}>
                    <TableCell>{t(ACTION_LABELS[entry.catalog_history_action])}</TableCell>
                    <TableCell>{`v${entry.catalog_history_version}`}</TableCell>
                    <TableCell>{author(entry)}</TableCell>
                    <TableCell>{nsdt(entry.catalog_history_created_at)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
    </div>
  );
};

export default CatalogHistoryTable;
