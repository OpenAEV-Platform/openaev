export interface SortHelpers {
  handleSort: (field: string) => void;
  getSortBy: () => string;
  getSortAsc: () => boolean;
}
