import { CloseOutlined } from '@mui/icons-material';
import { IconButton } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import {
  BaseEdge,
  EdgeLabelRenderer,
  type EdgeProps,
  getSmoothStepPath,
} from '@xyflow/react';

interface DeletableEdgeData {
  onDelete?: (edgeId: string, source: string, target: string) => void;
  [key: string]: unknown;
}

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
}: EdgeProps & { data?: DeletableEdgeData }) => {
  const theme = useTheme();
  const [edgePath, labelX, labelY] = getSmoothStepPath({
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
  });

  return (
    <>
      <BaseEdge id={id} path={edgePath} markerEnd={markerEnd} />
      <EdgeLabelRenderer>
        <div
          style={{
            position: 'absolute',
            transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
            pointerEvents: 'all',
          }}
          className="nodrag nopan"
        >
          <IconButton
            size="small"
            onClick={() => data?.onDelete?.(id, source, target)}
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
    </>
  );
};

export default DeletableEdge;
