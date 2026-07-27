import { AddOutlined, CropFreeOutlined, RemoveOutlined } from '@mui/icons-material';
import { Box, Divider, IconButton, ToggleButton, ToggleButtonGroup, Tooltip } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { Panel, useReactFlow } from '@xyflow/react';
import { memo } from 'react';

import { useFormatter } from '../i18n';
import { TIME_SCALES } from './chronoUtils';

interface Props {
  scaleIndex: number;
  onScaleChange: (index: number) => void;
  onMouseEnter?: () => void;
  onMouseLeave?: () => void;
}

/**
 * The floating playground toolbar (bottom-left): fit view, zoom, and the time
 * scale segmented control (how much time a labeled gridline represents) -
 * replacing the old cryptic unfold-icon interval buttons.
 */
const TimelineControlsComponent = ({ scaleIndex, onScaleChange, onMouseEnter, onMouseLeave }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const reactFlow = useReactFlow();

  return (
    <Panel position="bottom-left">
      <Box
        onMouseEnter={onMouseEnter}
        onMouseLeave={onMouseLeave}
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 0.5,
          padding: 0.5,
          borderRadius: 1,
          backgroundColor: alpha(theme.palette.background.paper, 0.85),
          backdropFilter: 'blur(12px)',
          border: `1px solid ${theme.palette.divider}`,
          boxShadow: `0 4px 14px ${alpha(theme.palette.common.black, 0.3)}`,
        }}
      >
        <Tooltip title={t('Fit view')}>
          <IconButton size="small" onClick={() => reactFlow.fitView({ duration: 500 })}>
            <CropFreeOutlined fontSize="small" />
          </IconButton>
        </Tooltip>
        <Tooltip title={t('Zoom in')}>
          <IconButton size="small" onClick={() => reactFlow.zoomIn({ duration: 200 })}>
            <AddOutlined fontSize="small" />
          </IconButton>
        </Tooltip>
        <Tooltip title={t('Zoom out')}>
          <IconButton size="small" onClick={() => reactFlow.zoomOut({ duration: 200 })}>
            <RemoveOutlined fontSize="small" />
          </IconButton>
        </Tooltip>
        <Divider orientation="vertical" flexItem sx={{ marginInline: 0.5 }} />
        <Tooltip title={t('Time scale')}>
          <ToggleButtonGroup
            size="small"
            exclusive
            value={scaleIndex}
            sx={{
              'height': 28,
              '& .MuiToggleButton-root': {
                paddingInline: 1,
                fontSize: 11,
                fontWeight: 600,
                lineHeight: 1,
              },
            }}
          >
            {TIME_SCALES.map((scale, index) => (
              <ToggleButton
                key={scale.label}
                value={index}
                onClick={() => onScaleChange(index)}
                aria-label={scale.label}
              >
                {scale.label}
              </ToggleButton>
            ))}
          </ToggleButtonGroup>
        </Tooltip>
      </Box>
    </Panel>
  );
};

const TimelineControls = memo(TimelineControlsComponent);
export default TimelineControls;
