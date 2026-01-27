import type { ReactElement } from 'react';

export interface IconBarElement {
  type: string | undefined;
  selectedType?: string | null;
  icon: () => ReactElement;
  color: string | undefined;
  name: string;
  function: () => void;
  count?: number;
  results?: () => ReactElement;
  expandedResults?: () => ReactElement;
}
