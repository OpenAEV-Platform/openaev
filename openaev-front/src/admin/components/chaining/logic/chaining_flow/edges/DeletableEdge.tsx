import { CloseOutlined } from '@mui/icons-material';
import { IconButton } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import {
  BaseEdge,
  type Edge,
  EdgeLabelRenderer,
  type EdgeProps,
  getSmoothStepPath,
} from '@xyflow/react';

interface DeletableEdgeData {
  onDelete?: (edgeId: string, source: string, target: string) => void;
  /** Emphasized in blue when its event is the currently selected one. */
  isHighlighted?: boolean;
  /** Faded out when outside the selected event's flow (spotlight backdrop). */
  dimmed?: boolean;

  [key: string]: unknown;
}

type DeletableEdgeType = Edge<DeletableEdgeData, 'deletable'>;

/** Opacity applied to edges outside the selected event's flow (spotlight backdrop). */
const DIMMED_OPACITY = 0.24;

const DeletableEdge = ({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  markerEnd,
  source,
  target,
  data,
}: EdgeProps<DeletableEdgeType>) => {
  const theme = useTheme();
  const [edgePath, labelX, labelY] = getSmoothStepPath({
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
  });

  const handleDelete = () => {
    if (data?.onDelete) {
      data.onDelete(id, source, target);
    }
  };

  const dimmed = !!data?.dimmed;

  return (
    <>
      <BaseEdge
        id={id}
        path={edgePath}
        markerEnd={markerEnd}
        style={{
          ...(data?.isHighlighted
            ? {
                stroke: theme.palette.primary.main,
                strokeWidth: 2,
              }
            : {}),
          ...(dimmed ? { opacity: DIMMED_OPACITY } : {}),
          transition: 'opacity 0.2s ease',
        }}
      />

      {data?.onDelete && (
        <EdgeLabelRenderer>
          <div
            style={{
              position: 'absolute',
              transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
              pointerEvents: 'all',
              zIndex: 1000,
            }}
            className="nodrag nopan"
          >
            <IconButton
              size="small"
              onMouseDown={e => e.stopPropagation()}
              onClick={handleDelete}
              sx={{
                'width': 20,
                'height': 20,
                'background': theme.palette.background.paper,
                'border': `1px solid ${theme.palette.divider}`,
                '&:hover': {
                  background: theme.palette.error.main,
                  color: theme.palette.error.contrastText,
                },
              }}
            >
              <CloseOutlined sx={{ fontSize: 12 }} />
            </IconButton>
          </div>
        </EdgeLabelRenderer>
      )}
    </>
  );
};

export default DeletableEdge;
