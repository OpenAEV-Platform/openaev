import { Paper as FdsPaper } from '@filigran/design-system';
import { type FunctionComponent, type ReactNode } from 'react';

interface PaperProps {
  children: ReactNode;
  className?: string;
}

// Shared surface wrapper. `padding={16}` is the product's `theme.spacing(2)`,
// the same value on the library's scale. `className` reaches the surface, so
// callers keep their own overrides.
const Paper: FunctionComponent<PaperProps> = ({ children, className = '' }) => (
  <FdsPaper padding={16} className={className || undefined}>
    {children}
  </FdsPaper>
);

export default Paper;
