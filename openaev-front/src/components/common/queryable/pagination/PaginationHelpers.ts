export interface PaginationHelpers {
  handleChangePage: (newPage: number) => void;
  handleChangeRowsPerPage: (rowsPerPage: number) => void;
  handleChangeTotalElements: (value: number) => void;
  handleChangePagination: (object: {
    size: number;
    page: number;
  }) => void;
  getTotalElements: () => number;
  page: number;
  elementsPerPage: number;
}
