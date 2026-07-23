export interface SortHelpers {
  handleSort: (field: string) => void;
  /** Sets both the sort field and the direction explicitly (used by sort selects). */
  handleDirectedSort: (field: string, asc: boolean) => void;
  getSortBy: () => string;
  getSortAsc: () => boolean;
}
