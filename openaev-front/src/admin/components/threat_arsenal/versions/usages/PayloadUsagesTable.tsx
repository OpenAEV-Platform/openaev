import { Checkbox, Text } from '@filigran/design-system';
// fds:keep-mui the library has no Table yet; dressed to its spec by FDS_TABLE_SX
import { Table, TableBody, TableCell, TableHead, TablePagination, TableRow } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useState } from 'react';

import { FDS_TABLE_SX, fdsTableSectionStyle } from '../../../../../components/common/fdsTableSpec';
import { useFormatter } from '../../../../../components/i18n';
import { type PayloadUsageOutput, type PayloadUsagesOutput } from '../../../../../utils/api-types';
import useCountLabel from '../../../../../utils/hooks/useCountLabel';
import { type UseEntityToggle } from '../../../../../utils/hooks/useEntityToggle';
import { USAGE_TYPES } from './usageTypes';

const USAGES_PER_PAGE = 10;

interface Props {
  usages: PayloadUsagesOutput;
  // Given, rows can be picked: the header box picks every row, on every page.
  selection?: UseEntityToggle<PayloadUsageOutput>;
}

const PayloadUsagesTable: FunctionComponent<Props> = ({ usages, selection }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const counted = useCountLabel();
  const [page, setPage] = useState(0);
  useEffect(() => setPage(0), [usages]);

  const rows = usages.payload_usages;
  const hidden = usages.payload_usages_hidden_count ?? 0;
  const isSelected = (usage: PayloadUsageOutput) => !!selection && (selection.selectAll
    ? !(usage.usage_id in selection.deSelectedElements)
    : usage.usage_id in selection.selectedElements);
  return (
    <>
      {rows.length === 0 && hidden === 0 && (
        <div style={fdsTableSectionStyle(theme)}>
          <Text variant="content-compact" className="text-default-secondary">{t('No inject uses this version')}</Text>
        </div>
      )}
      {rows.length > 0 && (
        <Table size="small" sx={FDS_TABLE_SX} aria-label={t('Injects on this version')}>
          <TableHead>
            <TableRow>
              {selection && (
                <TableCell padding="checkbox">
                  <Checkbox
                    aria-label={t('Select all')}
                    checked={selection.selectAll && Object.keys(selection.deSelectedElements).length === 0}
                    onCheckedChange={selection.handleToggleSelectAll}
                  />
                </TableCell>
              )}
              <TableCell>{t('Inject')}</TableCell>
              <TableCell>{t('Scenario')}</TableCell>
              <TableCell>{t('Type')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.slice(page * USAGES_PER_PAGE, (page + 1) * USAGES_PER_PAGE).map(usage => (
              <TableRow
                key={usage.usage_id}
                hover={!!selection}
                selected={isSelected(usage)}
                onClick={selection ? () => selection.onToggleEntity(usage) : undefined}
                sx={{ cursor: selection ? 'pointer' : 'default' }}
              >
                {selection && (
                  <TableCell padding="checkbox" onClick={event => event.stopPropagation()}>
                    <Checkbox
                      aria-label={usage.usage_name ?? usage.usage_id}
                      checked={isSelected(usage)}
                      onCheckedChange={() => selection.onToggleEntity(usage)}
                    />
                  </TableCell>
                )}
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
        <div style={fdsTableSectionStyle(theme)}>
          <Text variant="content-compact" className="text-default-secondary">
            {counted(hidden, '1 other inject you cannot see', '{count} other injects you cannot see')}
          </Text>
        </div>
      )}
    </>
  );
};

export default PayloadUsagesTable;
