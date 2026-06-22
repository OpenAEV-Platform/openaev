import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import { type FC } from 'react';

// ── Toolbar button ─────────────────────────────────────────────────────────
interface ToolbarBtnProps {
  title: string;
  onClick: () => void;
  active?: boolean;
  disabled?: boolean;
  icon: React.ReactElement;
}

export const ToolbarBtn: FC<ToolbarBtnProps> = ({ title, onClick, active = false, disabled = false, icon }) => (
  <Tooltip title={title} placement="top">
    <span style={{ flexShrink: 0 }}>
      <IconButton
        onClick={onClick}
        disabled={disabled}
        size="small"
        sx={{
          color: active ? 'primary.main' : 'text.secondary',
          bgcolor: active ? 'action.selected' : 'transparent',
          borderRadius: 0.5,
          p: '2px',
          '&:hover': { bgcolor: 'action.hover' },
          '& svg': { fontSize: '1rem' },
        }}
      >
        {icon}
      </IconButton>
    </span>
  </Tooltip>
);

export const Sep: FC = () => (
  <Divider orientation="vertical" flexItem sx={{ mx: 0.5, alignSelf: 'center', height: 16, flexShrink: 0 }} />
);

