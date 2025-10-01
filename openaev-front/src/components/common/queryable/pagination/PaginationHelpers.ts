export interface PaginationHelpers {
  handleChangePage: (newPage: number) => void;
  handleChangeRowsPerPage: (rowsPerPage: number) => void;
  handleChangeTotalElements: (value: number) => void;
  getTotalElements: () => number;
}
