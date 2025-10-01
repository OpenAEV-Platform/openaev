import { Skeleton, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material';
import { type ReactNode } from 'react';

export interface TableConfig<T> {
  label: string;
  value: (data: T) => ReactNode | string;
  width?: string;
  align?: 'left' | 'center' | 'right';
}

interface Props<T> {
  configs: TableConfig<T>[];
  datas: T[];
  loading: boolean;
}

const TableData = <T extends object>({
  configs,
  datas,
  loading,
}: Props<T>) => {
  return (
    <Table size="small">
      <TableHead>
        <TableRow>
          {configs.map(({ label, width, align = 'center' }) => (
            <TableCell
              key={label}
              style={{
                width: width,
                textAlign: align,
              }}
            >
              <Typography variant="h5">
                {label}
              </Typography>
            </TableCell>
          ))}
        </TableRow>
      </TableHead>
      <TableBody>
        {(loading ? [...Array(21)] : datas).map((data: T, idx: number) => {
          return (
            <TableRow key={idx}>
              {configs.map(({ label, width, align = 'center', value }) => (
                <TableCell
                  key={label}
                  style={{
                    width: width,
                    textAlign: align,
                  }}
                >
                  {loading ? <Skeleton height={40} /> : value(data)}
                </TableCell>
              ))}
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
};

export default TableData;
