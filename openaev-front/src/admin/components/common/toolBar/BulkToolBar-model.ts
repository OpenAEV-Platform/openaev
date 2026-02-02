import type { ReactElement } from 'react';

export interface ToolTasks {
  type: string;
  icon: () => ReactElement;
  function: () => void;
}
