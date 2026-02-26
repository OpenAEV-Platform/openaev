import { Add } from '@mui/icons-material';
import { Button, ListItemIcon, ListItemText, Menu, MenuItem } from '@mui/material';
import { BoltOutlined, PlayArrowOutlined } from '@mui/icons-material';
import { type FunctionComponent, type MouseEvent, useState } from 'react';

import { useFormatter } from '../../../../../components/i18n';

interface Props {
  onAddAction: () => void;
  onAddEvent: () => void;
}

const LogicAddComponentButton: FunctionComponent<Props> = ({ onAddAction, onAddEvent }) => {
  const { t } = useFormatter();
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);

  const handleClick = (event: MouseEvent<HTMLButtonElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  return (
    <>
      <Button
        variant="contained"
        color="primary"
        startIcon={<Add />}
        onClick={handleClick}
      >
        {t('Add component')}
      </Button>
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={handleClose}
      >
        <MenuItem
          onClick={() => {
            handleClose();
            onAddAction();
          }}
        >
          <ListItemIcon>
            <PlayArrowOutlined />
          </ListItemIcon>
          <ListItemText primary={t('Action')} secondary={t('An inject to execute')} />
        </MenuItem>
        <MenuItem
          onClick={() => {
            handleClose();
            onAddEvent();
          }}
        >
          <ListItemIcon>
            <BoltOutlined />
          </ListItemIcon>
          <ListItemText primary={t('Event')} secondary={t('A trigger with conditions')} />
        </MenuItem>
      </Menu>
    </>
  );
};

export default LogicAddComponentButton;
