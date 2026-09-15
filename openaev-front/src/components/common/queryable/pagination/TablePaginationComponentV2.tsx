import { TablePagination } from '@mui/material';
import { type ChangeEvent, type FunctionComponent, type MouseEvent } from 'react';

import { type PaginationHelpers } from './PaginationHelpers';
import { ROWS_PER_PAGE_OPTIONS } from './usePaginationState';

interface Props {
  page: number;
  size: number;
  paginationHelpers: PaginationHelpers;
}

const TablePaginationComponentV2: FunctionComponent<Props> = ({
  page,
  size,
  paginationHelpers,
}) => {
  const handleChangePage = (
    _event: MouseEvent<HTMLButtonElement> | null,
    newPage: number,
  ) => paginationHelpers.handleChangePage(newPage);

  const handleChangeRowsPerPage = (
    event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>,
  ) => paginationHelpers.handleChangeRowsPerPage(parseInt(event.target.value, 10));

  return (
    <TablePagination
      component="div"
      // MUI wraps this control in a Toolbar whose own `minHeight` is 52px, which
      // is what made the toolbar's first row 52 tall. Brought to the 40px the
      // row is designed for, and the page arrows shrink to fit inside it rather
      // than being clipped by it.
      sx={{
        '& .MuiToolbar-root': {
          minHeight: 36,
          paddingLeft: 0,
        },
        // The two captions are paragraphs, so they carry the browser's own
        // 1em vertical margins — 12.8px each side of a 19px line, which is what
        // made the row 45 tall rather than the height its Toolbar asks for.
        '& .MuiTablePagination-selectLabel, & .MuiTablePagination-displayedRows': {
          marginTop: 0,
          marginBottom: 0,
        },
        '& .MuiTablePagination-actions': { marginLeft: 1 },
        '& .MuiTablePagination-actions .MuiIconButton-root': { padding: 0.5 },
      }}
      rowsPerPageOptions={ROWS_PER_PAGE_OPTIONS}
      count={paginationHelpers.getTotalElements()}
      page={page}
      onPageChange={handleChangePage}
      rowsPerPage={size}
      onRowsPerPageChange={handleChangeRowsPerPage}
    />
  );
};

export default TablePaginationComponentV2;
