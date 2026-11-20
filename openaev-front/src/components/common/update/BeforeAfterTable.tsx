// fds:keep-mui the library has no Table yet; dressed to its spec by FDS_TABLE_SX
import { Table, TableBody, TableCell, TableHead, TableRow } from '@mui/material';

import { useFormatter } from '../../i18n';
import { FDS_TABLE_SX } from '../fdsTableSpec';
import TextDiff from './TextDiff';

export interface BeforeAfterRow {
  id: string;
  field: string;
  before: string;
  after: string;
}

interface Props {
  rows: BeforeAfterRow[];
  beforeLabel: string;
  afterLabel: string;
}

// What changes field by field: the value before and after, the words that differ highlighted.
const BeforeAfterTable = ({ rows, beforeLabel, afterLabel }: Props) => {
  const { t } = useFormatter();
  return (
    <Table size="small" aria-label={t('Changes')} sx={FDS_TABLE_SX}>
      <TableHead>
        <TableRow>
          <TableCell>{t('Field')}</TableCell>
          <TableCell>{beforeLabel}</TableCell>
          <TableCell>{afterLabel}</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {rows.map(row => (
          <TableRow key={row.id}>
            <TableCell>{row.field}</TableCell>
            <TableCell><TextDiff text={row.before} other={row.after} /></TableCell>
            <TableCell><TextDiff text={row.after} other={row.before} /></TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
};

export default BeforeAfterTable;
