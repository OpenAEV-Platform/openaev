import { ExpandLess, ExpandMore } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type CSSProperties, type FunctionComponent, type ReactNode } from 'react';

import { fdsGroupRowStyle } from './fdsTableSpec';

interface ArrowProps {
  open: boolean;
  // In the text colour, whatever holds it (a library IconButton paints its icon).
  neutral?: boolean;
}

// The arrow of a fold, on the right of what it opens.
export const FoldArrow: FunctionComponent<ArrowProps> = ({ open, neutral = false }) => {
  const style = neutral ? { color: 'var(--text-default-primary)' } : undefined;
  return open ? <ExpandLess fontSize="small" style={style} /> : <ExpandMore fontSize="small" style={style} />;
};

interface Props {
  open: boolean;
  onToggle: () => void;
  // The label, then what follows it (a count, a chip); the arrow stays on the right.
  children: ReactNode;
  style?: CSSProperties;
}

// A row that folds what is under it, as the Versions tab already drew it: the library has no Accordion yet
// (its ROADMAP entry "Accordion" is todo), and this goes when it ships.
const FoldRow: FunctionComponent<Props> = ({ open, onToggle, children, style }) => {
  const theme = useTheme();
  return (
    <button
      type="button"
      aria-expanded={open}
      onClick={onToggle}
      style={{
        ...fdsGroupRowStyle(theme),
        ...style,
      }}
    >
      {children}
      <span style={{ flex: 1 }} />
      <FoldArrow open={open} />
    </button>
  );
};

export default FoldRow;
