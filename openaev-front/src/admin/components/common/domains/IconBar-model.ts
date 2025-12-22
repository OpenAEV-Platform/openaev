import type { ReactElement } from 'react';

export interface IconBarElement {
  type: string;
  icon: () => ReactElement;
  color: 'default' | 'error' | 'success';
  name: string;
  function: () => void;
  count?: number;
  results?: () => ReactElement;
}
