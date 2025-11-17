import type { ReactElement } from 'react';

export interface ToolTasks {
  icon: () => ReactElement;
  function: () => void;
}