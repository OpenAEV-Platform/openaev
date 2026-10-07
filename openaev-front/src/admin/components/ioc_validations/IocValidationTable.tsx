import { useTheme } from '@mui/material/styles';
import { type CSSProperties, type ReactNode } from 'react';

import Empty from '../../../components/Empty';

export interface IocValidationTableColumn<T> {
  key: string;
  label: string;
  width?: string;
  render: (row: T) => ReactNode;
}

interface Props<T> {
  caption: string;
  columns: IocValidationTableColumn<T>[];
  rows: T[];
  rowKey: (row: T, index: number) => string;
  emptyMessage: string;
  // Height of every body row, for cells that never wrap: a table of the same rows then keeps its height whatever it
  // shows (loading placeholders or values).
  rowHeight?: CSSProperties['height'];
}

const IocValidationTable = <T, >({ caption, columns, rows, rowKey, emptyMessage, rowHeight }: Props<T>) => {
  const theme = useTheme();

  if (rows.length === 0) {
    return <Empty message={emptyMessage} />;
  }

  const cellStyle: CSSProperties = {
    padding: theme.spacing(1),
    borderBottom: `1px solid ${theme.palette.divider}`,
    textAlign: 'left',
    verticalAlign: 'middle',
    overflowWrap: 'anywhere',
  };

  return (
    <table
      style={{
        width: '100%',
        borderCollapse: 'collapse',
        tableLayout: 'fixed',
      }}
    >
      <caption className="sr-only">{caption}</caption>
      <thead>
        <tr>
          {columns.map(column => (
            <th
              key={column.key}
              scope="col"
              className="content-compact text-default-secondary"
              style={{
                ...cellStyle,
                width: column.width,
              }}
            >
              {column.label}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map((row, index) => (
          <tr key={rowKey(row, index)} style={rowHeight === undefined ? undefined : { height: rowHeight }}>
            {columns.map(column => (
              <td key={column.key} className="content-compact text-default-primary" style={cellStyle}>
                {column.render(row)}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
};

export default IocValidationTable;
