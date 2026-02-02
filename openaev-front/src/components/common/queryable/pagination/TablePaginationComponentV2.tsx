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
