import { useTheme } from '@mui/material/styles';
import { type BackgroundProps, type ReactFlowState, useStore } from '@xyflow/react';
import cc from 'classcat';
import { type CSSProperties, memo, useRef } from 'react';
import { shallow } from 'zustand/shallow';

interface Props extends BackgroundProps { minutesPerGap: number }

const selector = (s: ReactFlowState) => ({
  transform: s.transform,
  patternId: `pattern-${s.rfId}`,
});

/**
 * Custom background for the timeline
 * @param id id of the pattern
 * @param gap the default gap size
 * @param size
 * @param offset
 * @param style
 * @param className
 * @constructor
 */
function BackgroundComponent({
  id = '',
  gap = 125,
  size = 1,
  offset = 2,
  style,
  className,
}: Props) {
  const theme = useTheme();
  const ref = useRef<SVGSVGElement>(null);
  const { transform, patternId } = useStore(selector, shallow);

  const gapXY: [number, number] = Array.isArray(gap) ? gap : [gap, gap * 2];
  const scaledGap: [number, number] = [gapXY[0] * transform[2] || 1, gapXY[1] * transform[2] || 1];
  const scaledSize = size * transform[2];
  const computedOffset = Array.isArray(offset) ? offset : [offset, offset];
  const patternOffset = offset ? [scaledSize / computedOffset[0], scaledSize / computedOffset[1]] : [scaledSize, scaledSize];
  const modifiedPatternId = `${patternId}${id}`;

  return (
    <svg
      className={cc(['react-flow__background', className])}
      style={
        {
          ...style,
          position: 'absolute',
          width: '100%',
          height: '100%',
          top: 0,
          left: 0,
        } as CSSProperties
      }
      ref={ref}
      data-testid="rf__background"
    >
      <pattern
        id={modifiedPatternId}
        x={transform[0] % scaledGap[0]}
        y={transform[1] % scaledGap[1]}
        width={scaledGap[0]}
        height={scaledGap[1]}
        patternUnits="userSpaceOnUse"
        patternTransform={`translate(-${patternOffset[0]},-${patternOffset[1]})`}
      >
        <svg
          style={{ transform: `scale(${transform[2]})` }}
          xmlns="http://www.w3.org/2000/svg"
        >
          <path
            d="M0,0 L0,1000"
            stroke={theme.palette.mode === 'dark' ? '#121823' : '#ccc'}
            strokeWidth={3}
          />
        </svg>
      </pattern>
      <rect x="0" y="0" width="100%" height="100%" fill={`url(#${modifiedPatternId})`} />
    </svg>
  );
}

BackgroundComponent.displayName = 'CustomTimelineBackground';
const CustomTimelineBackground = memo(BackgroundComponent);
export default CustomTimelineBackground;
