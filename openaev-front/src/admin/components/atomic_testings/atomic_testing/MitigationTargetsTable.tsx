import { InputAdornment, Table, TableBody, TableCell, TableHead, TablePagination, TableRow, TextField } from '@mui/material';
import { useMemo, useState } from 'react';

import { useFormatter } from '../../../../components/i18n';

// PROTOTYPE: searchable, paginated list of the assets / targets a mitigation covers. Shared by the
// simulation level view and the atomic testing view so 100 targets never make the screen longer.

export interface TargetRow {
  name: string;
  environment: string;
  findings: string;
}

const PAGE_SIZE = 8;

const MitigationTargetsTable = ({ rows }: { rows: TargetRow[] }) => {
  const { t } = useFormatter();
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const filtered = useMemo(
    () => rows.filter(row => row.name.toLowerCase().includes(search.toLowerCase())),
    [rows, search],
  );
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 8,
    }}
    >
      <TextField
        size="small"
        placeholder={t('Search assets')}
        value={search}
        onChange={(event) => {
          setSearch(event.target.value);
          setPage(0);
        }}
        slotProps={{ input: { startAdornment: <InputAdornment position="start">🔍</InputAdornment> } }}
        style={{ maxWidth: 320 }}
      />
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>{t('Asset')}</TableCell>
            <TableCell>{t('Environment')}</TableCell>
            <TableCell>{t('Findings')}</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {filtered.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE).map(row => (
            <TableRow key={row.name}>
              <TableCell>{row.name}</TableCell>
              <TableCell>{row.environment}</TableCell>
              <TableCell>{row.findings}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <TablePagination
        component="div"
        count={filtered.length}
        page={page}
        rowsPerPage={PAGE_SIZE}
        rowsPerPageOptions={[PAGE_SIZE]}
        onPageChange={(_, next) => setPage(next)}
      />
    </div>
  );
};

export default MitigationTargetsTable;
