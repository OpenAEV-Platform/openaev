import { Paper as FdsPaper } from '@filigran/design-system';
import { type FunctionComponent, type ReactNode } from 'react';

interface PaperProps {
  children: ReactNode;
  className?: string;
}

// Shared surface wrapper, used 8 times across the profile, atomic testing and
// expectation screens. The library Paper carries the 16px padding (the product
// asked for `theme.spacing(2)`, the same value on the library's scale) and its
// own radius, so the local `makeStyles` block that used to hold both is gone.
// `className` still reaches the surface, so callers keep their own overrides.
const Paper: FunctionComponent<PaperProps> = ({ children, className = '' }) => (
  <FdsPaper padding={16} className={className || undefined}>
    {children}
  </FdsPaper>
);

export default Paper;
