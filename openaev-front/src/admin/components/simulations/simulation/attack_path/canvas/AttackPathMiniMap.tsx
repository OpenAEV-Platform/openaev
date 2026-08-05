import { Box } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { memo, type MouseEvent } from 'react';

import { type CanvasRect } from './canvas-geometry';

interface Props {
  rects: CanvasRect[];
  /** Normalised world size (content bounds, origin at 0,0). */
  worldWidth: number;
  worldHeight: number;
  /** Current viewport in world coordinates. */
  viewport: CanvasRect;
  /** Center the camera on a world point (minimap click-to-navigate). */
  onNavigate: (worldX: number, worldY: number) => void;
}

const MAP_W = 150;
const MAP_H = 96;

// Small custom overview map (bottom-right, like the previous canvas): every card as a tiny block,
// the current viewport as an outlined window, click anywhere to jump the camera there.
const AttackPathMiniMap = ({ rects, worldWidth, worldHeight, viewport, onNavigate }: Props) => {
  const theme = useTheme();
  const scale = Math.min(MAP_W / Math.max(1, worldWidth), MAP_H / Math.max(1, worldHeight));
  const offsetX = (MAP_W - worldWidth * scale) / 2;
  const offsetY = (MAP_H - worldHeight * scale) / 2;

  const handleClick = (e: MouseEvent<HTMLDivElement>) => {
    const bounds = e.currentTarget.getBoundingClientRect();
    const x = (e.clientX - bounds.left - offsetX) / scale;
    const y = (e.clientY - bounds.top - offsetY) / scale;
    onNavigate(x, y);
  };

  return (
    <Box
      onClick={handleClick}
      onPointerDown={e => e.stopPropagation()}
      sx={{
        width: MAP_W,
        height: MAP_H,
        borderRadius: 1,
        overflow: 'hidden',
        border: `1px solid ${theme.palette.divider}`,
        backgroundColor: theme.palette.background.paper,
        boxShadow: theme.shadows[3],
        cursor: 'pointer',
      }}
    >
      <svg width={MAP_W} height={MAP_H}>
        <g transform={`translate(${offsetX}, ${offsetY}) scale(${scale})`}>
          {rects.map((r, i) => (
            <rect
              // eslint-disable-next-line react/no-array-index-key
              key={i}
              x={r.x}
              y={r.y}
              width={r.width}
              height={r.height}
              rx={Math.min(4, r.height / 2)}
              fill={alpha(theme.palette.primary.main, 0.55)}
            />
          ))}
          <rect
            x={viewport.x}
            y={viewport.y}
            width={viewport.width}
            height={viewport.height}
            fill={alpha(theme.palette.primary.main, 0.08)}
            stroke={theme.palette.primary.main}
            strokeWidth={1.5 / scale}
          />
        </g>
      </svg>
    </Box>
  );
};

export default memo(AttackPathMiniMap);
