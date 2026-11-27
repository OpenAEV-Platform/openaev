import { type ReactElement } from 'react';

export interface Header {
  field: string;
  label: string;
  isSortable: boolean;
  /* eslint-disable-next-line @typescript-eslint/no-explicit-any */
  value?: (...values: any[]) => ReactElement | string | undefined;
}
