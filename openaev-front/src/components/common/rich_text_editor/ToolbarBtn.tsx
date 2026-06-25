import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Tooltip from '@mui/material/Tooltip';
import { type FC, type MouseEvent, useState } from 'react';

// ── Toolbar button ─────────────────────────────────────────────────────────
interface ToolbarBtnProps {
  title: string;
  onClick: (e: React.MouseEvent<HTMLButtonElement>) => void;
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
          'color': active ? 'primary.main' : 'text.secondary',
          'bgcolor': active ? 'action.selected' : 'transparent',
          'borderRadius': 0.5,
          'p': '4px',
          '&:hover': { bgcolor: 'action.hover' },
          '& svg': { fontSize: '1.25rem' },
        }}
      >
        {icon}
      </IconButton>
    </span>
  </Tooltip>
);

// ── Toolbar dropdown button ────────────────────────────────────────────────
interface ToolbarDropdownBtnProps {
  label: string;
  tooltip: string;
  options: {
    label: string;
    value: string;
    icon?: React.ReactElement;
  }[];
  value: string;
  onSelect: (value: string) => void;
  disabled?: boolean;
  minWidth?: number;
  menuMinWidth?: number;
  icon?: React.ReactElement;
}

export const ToolbarDropdownBtn: FC<ToolbarDropdownBtnProps> = ({
  label,
  tooltip,
  options,
  value,
  onSelect,
  disabled = false,
  minWidth = 72,
  menuMinWidth = 140,
  icon,
}) => {
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);

  const handleOpen = (e: MouseEvent<HTMLButtonElement>) => setAnchor(e.currentTarget);
  const handleClose = () => setAnchor(null);
  const handleSelect = (v: string) => { onSelect(v); handleClose(); };

  const btnSx = {
    'display': 'flex',
    'alignItems': 'center',
    'gap': 0,
    'height': 26,
    minWidth,
    'flexShrink': 0,
    'borderRadius': 0.5,
    'px': '4px',
    'color': 'text.secondary',
    'bgcolor': 'action.hover',
    '&:hover': { bgcolor: 'action.selected' },
    '& .MuiSvgIcon-root': { fontSize: '1.25rem' },
    'textTransform': 'none',
    'fontSize': '0.75rem',
    'fontWeight': 500,
    'justifyContent': 'space-between',
  };

  return (
    <>
      <Tooltip title={tooltip} placement="top">
        <span style={{ flexShrink: 0 }}>
          <IconButton onClick={handleOpen} disabled={disabled} size="small" sx={btnSx}>
            {icon ?? (
              <span style={{
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
              >
                {label}
              </span>
            )}
            <ArrowDropDownIcon />
          </IconButton>
        </span>
      </Tooltip>
      <Menu
        anchorEl={anchor}
        open={Boolean(anchor)}
        onClose={handleClose}
        slotProps={{ paper: { sx: { maxHeight: 280 } } }}
      >
        {options.map(opt => (
          <MenuItem
            key={opt.value || '__default__'}
            selected={opt.value === value}
            onClick={() => handleSelect(opt.value)}
            sx={{
              fontSize: '0.8rem',
              minWidth: menuMinWidth,
              gap: 1,
            }}
          >
            {opt.icon}
            {!opt.icon && opt.label}
          </MenuItem>
        ))}
      </Menu>
    </>
  );
};

export const Sep: FC = () => (
  <Divider
    orientation="vertical"
    flexItem
    sx={{
      mx: 0.5,
      alignSelf: 'center',
      height: 16,
      flexShrink: 0,
    }}
  />
);
