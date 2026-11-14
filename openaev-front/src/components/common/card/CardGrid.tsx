import { Skeleton } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type CSSProperties, type ReactNode } from 'react';

import { ENTITY_CARD_HEIGHT } from './EntityCard';

interface Props {
  children?: ReactNode;
  /** Renders placeholders of the card size instead of the children. */
  loading?: boolean;
  style?: CSSProperties;
}

const MAX_COLUMNS = 4;
const MIN_CARD_WIDTH = 280;
const SKELETON_COUNT = 8;

// The grid of every cards view. Columns of at least 280px, and never more than
// 4 (like OpenCTI): past that, cards grow with the container instead of
// multiplying. minmax(..., 1fr) keeps every track the same width even when a
// card's intrinsic content would otherwise stretch its column.
const CardGrid = ({ children, loading = false, style }: Props) => {
  const theme = useTheme();
  const gap = theme.spacing(2);
  const maxColumnsWidth = `calc((100% - ${MAX_COLUMNS - 1} * ${gap}) / ${MAX_COLUMNS})`;

  return (
    <div style={{
      display: 'grid',
      gap,
      gridTemplateColumns: `repeat(auto-fill, minmax(min(100%, max(${MIN_CARD_WIDTH}px, ${maxColumnsWidth})), 1fr))`,
      ...style,
    }}
    >
      {loading
        ? Array.from({ length: SKELETON_COUNT }).map((_, idx) => (
            <Skeleton
              key={idx}
              variant="rectangular"
              height={ENTITY_CARD_HEIGHT}
              animation="wave"
              sx={{ borderRadius: 1 }}
            />
          ))
        : children}
    </div>
  );
};

export default CardGrid;
