import { useTheme } from '@mui/material/styles';
import { Fragment } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import type { LogicGraphEdge } from './layout';

interface ConnectorsProps {
  edges: LogicGraphEdge[];
  width: number;
  height: number;
  /** Ids of the edges on the selected trigger's data-flow path (emphasized; the rest are dimmed). */
  highlightedEdgeIds: Set<string>;
  /** Whether a trigger is currently selected (enables the spotlight dimming + edge labels). */
  selectionActive: boolean;
  /** Remove a real `trigger -> action` link (unlink, without deleting either node). */
  onDeleteEdge?: (edge: LogicGraphEdge) => void;
  readOnly?: boolean;
}

const DIMMED_OPACITY = 0.16;

/**
 * SVG layer drawing the causal connectors in world coordinates:
 * - solid edges are the real `trigger -> action` links (`step_condition_ids`);
 * - dashed, finding-labeled edges are the inferred `producerAction -> trigger` links (by output-type
 *   match), so they are never mistaken for a stored link.
 *
 * When a trigger is selected, edges on its path are emphasized and labeled while the rest fade out.
 */
const Connectors = ({
  edges,
  width,
  height,
  highlightedEdgeIds,
  selectionActive,
  onDeleteEdge,
  readOnly = false,
}: ConnectorsProps) => {
  const theme = useTheme();
  const { t } = useFormatter();

  const realColor = theme.palette.text.disabled;
  const realHighlightColor = theme.palette.primary.main;
  const inferredColor = theme.palette.warning.main;

  const markers = [
    {
      id: 'logic-arrow-real',
      color: realColor,
    },
    {
      id: 'logic-arrow-real-hl',
      color: realHighlightColor,
    },
    {
      id: 'logic-arrow-inferred',
      color: inferredColor,
    },
  ];

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
      <defs>
        {markers.map(marker => (
          <marker
            key={marker.id}
            id={marker.id}
            viewBox="0 0 10 10"
            refX="8"
            refY="5"
            markerWidth="7"
            markerHeight="7"
            orient="auto-start-reverse"
          >
            <path d="M 0 0 L 10 5 L 0 10 z" fill={marker.color} />
          </marker>
        ))}
      </defs>

      {edges.map((edge) => {
        const highlighted = highlightedEdgeIds.has(edge.id);
        const dimmed = selectionActive && !highlighted;
        const isInferred = edge.kind === 'inferred';

        let stroke = isInferred ? inferredColor : realColor;
        let markerId = isInferred ? 'logic-arrow-inferred' : 'logic-arrow-real';
        if (highlighted && !isInferred) {
          stroke = realHighlightColor;
          markerId = 'logic-arrow-real-hl';
        }

        // Inferred (output-match) edges are kept very light by default so they read as a faint hint,
        // not a hard causal link; they only gain weight + a label when their trigger is spotlighted.
        const baseOpacity = isInferred && !highlighted ? 0.2 : 1;
        const opacity = dimmed ? DIMMED_OPACITY : baseOpacity;
        const showLabel = isInferred && edge.label && highlighted;
        // Only real (stored) links can be removed; inferred edges are computed, not persisted.
        const canDelete = !readOnly && !isInferred && !dimmed && !!onDeleteEdge;

        return (
          <Fragment key={edge.id}>
            <path
              d={edge.path}
              fill="none"
              stroke={stroke}
              strokeWidth={(() => {
                if (highlighted) return 2;
                return isInferred ? 1 : 1.5;
              })()}
              strokeDasharray={isInferred ? '5 4' : undefined}
              markerEnd={isInferred && !highlighted ? undefined : `url(#${markerId})`}
              style={{
                opacity,
                transition: 'opacity 0.2s ease',
              }}
            />
            {canDelete && (
              <g
                transform={`translate(${edge.labelX}, ${edge.labelY})`}
                onPointerDown={e => e.stopPropagation()}
                onClick={(e) => {
                  e.stopPropagation();
                  onDeleteEdge!(edge);
                }}
                style={{
                  cursor: 'pointer',
                  pointerEvents: 'auto',
                  opacity,
                }}
              >
                <title>{t('Remove link')}</title>
                <circle r={8} fill={theme.palette.background.paper} stroke={realColor} strokeWidth={1} />
                <line x1={-3} y1={-3} x2={3} y2={3} stroke={theme.palette.text.secondary} strokeWidth={1.5} strokeLinecap="round" />
                <line x1={-3} y1={3} x2={3} y2={-3} stroke={theme.palette.text.secondary} strokeWidth={1.5} strokeLinecap="round" />
              </g>
            )}
            {showLabel && (
              <g style={{ opacity }}>
                <rect
                  x={edge.labelX - (edge.label!.length * 3.4 + 6)}
                  y={edge.labelY - 9}
                  width={edge.label!.length * 6.8 + 12}
                  height={16}
                  rx={3}
                  fill={theme.palette.background.paper}
                  stroke={inferredColor}
                  strokeWidth={0.75}
                />
                <text
                  x={edge.labelX}
                  y={edge.labelY + 2}
                  textAnchor="middle"
                  fontSize={10}
                  fontWeight={600}
                  fill={inferredColor}
                >
                  {edge.label}
                </text>
              </g>
            )}
          </Fragment>
        );
      })}
    </svg>
  );
};

export default Connectors;
