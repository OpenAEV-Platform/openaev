import { Text } from '@filigran/design-system';
// fds:keep-mui the library has no Table yet; dressed to its spec by FDS_TABLE_SX
import { Table, TableBody, TableCell, TableHead, TablePagination, TableRow } from '@mui/material';
import { type FunctionComponent, useEffect, useState } from 'react';

import { FDS_TABLE_SECTION_STYLE, FDS_TABLE_SX } from '../../../../../components/common/fdsTableSpec';
import { useFormatter } from '../../../../../components/i18n';
import { type PayloadUsagesOutput } from '../../../../../utils/api-types';
import useCountLabel from '../../../../../utils/hooks/useCountLabel';
import { USAGE_TYPES } from './usageTypes';

const USAGES_PER_PAGE = 10;

interface Props { usages: PayloadUsagesOutput }

const PayloadUsagesTable: FunctionComponent<Props> = ({ usages }) => {
  const { t } = useFormatter();
  const counted = useCountLabel();
  const [page, setPage] = useState(0);
  useEffect(() => setPage(0), [usages]);

  const rows = usages.payload_usages;
  const hidden = usages.payload_usages_hidden_count ?? 0;
  return (
    <>
      {rows.length === 0 && hidden === 0 && (
        <div style={FDS_TABLE_SECTION_STYLE}>
          <Text variant="content-compact" className="text-default-secondary">{t('No inject uses this version')}</Text>
        </div>
      )}
      {rows.length > 0 && (
        <Table size="small" sx={FDS_TABLE_SX} aria-label={t('Injects on this version')}>
          <TableHead>
            <TableRow>
              <TableCell>{t('Inject')}</TableCell>
              <TableCell>{t('Scenario')}</TableCell>
              <TableCell>{t('Type')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.slice(page * USAGES_PER_PAGE, (page + 1) * USAGES_PER_PAGE).map(usage => (
              <TableRow key={usage.usage_id}>
                <TableCell>{usage.usage_name ?? usage.usage_id}</TableCell>
                <TableCell>{usage.usage_scenario_name ?? '-'}</TableCell>
                <TableCell>{t(USAGE_TYPES[usage.usage_type].label)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
      {rows.length > USAGES_PER_PAGE && (
        <TablePagination
          component="div"
          count={rows.length}
          page={page}
          onPageChange={(_, next) => setPage(next)}
          rowsPerPage={USAGES_PER_PAGE}
          rowsPerPageOptions={[]}
        />
      )}
      {hidden > 0 && (
        <div style={FDS_TABLE_SECTION_STYLE}>
          <Text variant="content-compact" className="text-default-secondary">
            {counted(hidden, '1 other inject you cannot see', '{count} other injects you cannot see')}
          </Text>
        </div>
      )}
    </>
  );
};

export default PayloadUsagesTable;
