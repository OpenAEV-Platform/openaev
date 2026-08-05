import { alpha, useTheme } from '@mui/material/styles';
import { Fragment, memo } from 'react';

import { useFormatter } from '../../../../../../components/i18n';
import attackPathStatusColor, { attackPathCausalColor, attackPathStatusLabel } from '../attack-path-colors';
import { AP_FLOW_CAUSAL_EDGE_TYPE } from '../attack-path-flow-helpers';
import { type EdgeGeometry } from './canvas-geometry';

interface Props {
  geometries: EdgeGeometry[];
  width: number;
  height: number;
}

// SVG connector layer in world coordinates, in the chaining Logic view's language. Two edge kinds:
// - grouped execution/finding edges: solid, coloured by the prevention/detection verdict, with a
//   pill label carrying the contract/finding-type (or the "+N" grouped count);
// - causal kill-chain edges: the dedicated causal colour, dashed when they are pure dependsOn
//   sequencing, solid when a produced finding actually feeds the consumer, labelled in italics.
const AttackPathConnectors = ({ geometries, width, height }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();

  return (
    <svg
      width={width}
      height={height}
      style={{
        position: 'absolute',
        top: 0,
        left: 0,
        overflow: 'visible',
        pointerEvents: 'none',
      }}
    >
      {geometries.map(({ id, path, labelX, labelY, edge }) => {
        const data = edge.data;
        const isCausal = edge.type === AP_FLOW_CAUSAL_EDGE_TYPE;
        const dimmed = data?.dimmed ?? false;
        const color = isCausal
          ? attackPathCausalColor(theme)
          : attackPathStatusColor(theme, data?.status);
        const dashed = isCausal && data?.causalKind !== 'finding';
        const opacity = dimmed ? 0.08 : 0.85;
        const count = data?.count ?? 1;
        const label = data?.label ?? (count > 1 ? `+${count}` : undefined);
        const showLabel = !dimmed && !!label;
        return (
          <Fragment key={id}>
            <path
              d={path}
              fill="none"
              stroke={color}
              strokeWidth={1.5}
              strokeDasharray={dashed ? '6 4' : undefined}
              style={{
                opacity,
                transition: 'opacity 0.2s ease',
              }}
            />
            {showLabel && (
              <g style={{ opacity: dimmed ? 0.08 : 1 }}>
                <title>
                  {isCausal
                    ? label
                    : `${label} — ${t(attackPathStatusLabel(data?.status))}`}
                </title>
                <rect
                  x={labelX - (label!.length * 3.4 + 6)}
                  y={labelY - 8}
                  width={label!.length * 6.8 + 12}
                  height={16}
                  rx={8}
                  fill={theme.palette.background.paper}
                  stroke={alpha(color, 0.5)}
                  strokeWidth={0.75}
                />
                <text
                  x={labelX}
                  y={labelY + 3}
                  textAnchor="middle"
                  fontSize={10}
                  fontWeight={600}
                  fontStyle={isCausal ? 'italic' : undefined}
                  fill={color}
                >
                  {label}
                </text>
              </g>
            )}
          </Fragment>
        );
      })}
    </svg>
  );
};

export default memo(AttackPathConnectors);
