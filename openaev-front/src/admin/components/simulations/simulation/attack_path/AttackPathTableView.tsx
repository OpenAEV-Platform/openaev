import { FileDownloadOutlined, LocalFireDepartment } from '@mui/icons-material';
import { Box, Button, Table, TableBody, TableCell, TableHead, TableRow, TableSortLabel, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useMemo, useState } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import { attackPathChokepointColor } from './attack-path-colors';

export interface AttackPathEndpointRow {
  nodeId: string;
  ref: string;
  label: string;
  ip?: string;
  score: number;
  findingCounts: Record<string, number>;
}

interface Props {
  rows: AttackPathEndpointRow[];
  // Finding-type columns to show a per-type breakdown, in a stable order.
  typeColumns: string[];
  // Rank threshold (top-N) used to flag chokepoints with the flame, matching the graph badges.
  chokepointTopN: number;
  onRowFocus: (row: AttackPathEndpointRow) => void;
}

type SortKey = 'label' | 'ip' | 'score' | `type:${string}`;
type SortDir = 'asc' | 'desc';

const cellValue = (row: AttackPathEndpointRow, key: SortKey): string | number => {
  if (key === 'label') {
    return row.label.toLowerCase();
  }
  if (key === 'ip') {
    return row.ip ?? '';
  }
  if (key === 'score') {
    return row.score;
  }
  return row.findingCounts[key.slice('type:'.length)] ?? 0;
};

// Escape a CSV field: wrap in quotes and double any embedded quotes so commas/newlines are safe.
const csvField = (v: string | number): string => `"${String(v).replace(/"/g, '""')}"`;

// A table alternative to the node-link graph: the most-exposed endpoints (chokepoints) and their
// per-type finding breakdown, sortable and exportable to CSV. Reuses the already-loaded endpoint data
// (no extra fetch); clicking a row focuses that endpoint's path back on the graph.
const AttackPathTableView = ({ rows, typeColumns, chokepointTopN, onRowFocus }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const [sortKey, setSortKey] = useState<SortKey>('score');
  const [sortDir, setSortDir] = useState<SortDir>('desc');
  const chokepointColor = attackPathChokepointColor(theme);

  const sortedRows = useMemo(() => {
    const sorted = [...rows].sort((a, b) => {
      const av = cellValue(a, sortKey);
      const bv = cellValue(b, sortKey);
      if (av < bv) {
        return sortDir === 'asc' ? -1 : 1;
      }
      if (av > bv) {
        return sortDir === 'asc' ? 1 : -1;
      }
      return 0;
    });
    return sorted;
  }, [rows, sortKey, sortDir]);

  // Stable chokepoint rank (by score desc), independent of the current display sort, so the exported
  // "Rank" column always means the exposure rank — not the current row position.
  const rankByNodeId = useMemo(() => {
    const m = new Map<string, number>();
    [...rows].sort((a, b) => b.score - a.score).forEach((r, i) => m.set(r.nodeId, i + 1));
    return m;
  }, [rows]);

  const onSort = (key: SortKey) => {
    if (key === sortKey) {
      setSortDir(d => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortKey(key);
      // Text columns default to ascending, numeric columns to descending (most exposed first).
      setSortDir(key === 'label' || key === 'ip' ? 'asc' : 'desc');
    }
  };

  const exportCsv = () => {
    const header = [t('Rank'), t('Endpoint'), t('IP'), t('Total findings'), ...typeColumns];
    const body = sortedRows.map(r => [
      rankByNodeId.get(r.nodeId) ?? 0,
      r.label,
      r.ip ?? '',
      r.score,
      ...typeColumns.map(tc => r.findingCounts[tc] ?? 0),
    ]);
    const csv = [header, ...body].map(line => line.map(csvField).join(',')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'attack-path-chokepoints.csv';
    a.click();
    URL.revokeObjectURL(url);
  };

  const headSort = (key: SortKey) => ({
    active: sortKey === key,
    direction: sortKey === key ? sortDir : 'asc' as SortDir,
    onClick: () => onSort(key),
  });

  return (
    <Box sx={{
      flex: 1,
      minWidth: 0,
      overflow: 'auto',
      p: 1.5,
    }}
    >
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        mb: 1,
      }}
      >
        <Typography variant="subtitle2" color="text.secondary">
          {`${t('Most exposed endpoints')} (${rows.length})`}
        </Typography>
        <Button
          size="small"
          variant="outlined"
          color="secondary"
          startIcon={<FileDownloadOutlined />}
          onClick={exportCsv}
          disabled={rows.length === 0}
        >
          {t('Export CSV')}
        </Button>
      </Box>
      {rows.length === 0
        ? (
            <Typography variant="body2" color="text.secondary">{t('No exposed endpoints')}</Typography>
          )
        : (
            <Table size="small" stickyHeader>
              <TableHead>
                <TableRow>
                  <TableCell>#</TableCell>
                  <TableCell sortDirection={sortKey === 'label' ? sortDir : false}>
                    <TableSortLabel {...headSort('label')}>{t('Endpoint')}</TableSortLabel>
                  </TableCell>
                  <TableCell sortDirection={sortKey === 'ip' ? sortDir : false}>
                    <TableSortLabel {...headSort('ip')}>{t('IP')}</TableSortLabel>
                  </TableCell>
                  <TableCell align="right" sortDirection={sortKey === 'score' ? sortDir : false}>
                    <TableSortLabel {...headSort('score')}>{t('Total findings')}</TableSortLabel>
                  </TableCell>
                  {typeColumns.map(tc => (
                    <TableCell key={tc} align="right" sortDirection={sortKey === `type:${tc}` ? sortDir : false}>
                      <TableSortLabel {...headSort(`type:${tc}`)}>{tc}</TableSortLabel>
                    </TableCell>
                  ))}
                </TableRow>
              </TableHead>
              <TableBody>
                {sortedRows.map((r, i) => (
                  <TableRow
                    key={r.nodeId}
                    hover
                    onClick={() => onRowFocus(r)}
                    sx={{ cursor: 'pointer' }}
                  >
                    <TableCell>
                      <Box sx={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: 0.5,
                      }}
                      >
                        {i < chokepointTopN && sortKey === 'score' && sortDir === 'desc' && (
                          <LocalFireDepartment sx={{
                            fontSize: 15,
                            color: chokepointColor,
                          }}
                          />
                        )}
                        {i + 1}
                      </Box>
                    </TableCell>
                    <TableCell title={r.label}>{r.label}</TableCell>
                    <TableCell>{r.ip ?? '—'}</TableCell>
                    <TableCell align="right">{r.score}</TableCell>
                    {typeColumns.map(tc => (
                      <TableCell key={tc} align="right">{r.findingCounts[tc] ?? 0}</TableCell>
                    ))}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
    </Box>
  );
};

export default AttackPathTableView;
