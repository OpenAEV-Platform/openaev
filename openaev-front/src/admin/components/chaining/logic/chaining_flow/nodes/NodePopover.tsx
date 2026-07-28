import { DeleteOutlined, EditOutlined } from '@mui/icons-material';
import { ListItemIcon, ListItemText, Menu, MenuItem } from '@mui/material';

import { useFormatter } from '../../../../../../components/i18n';

interface NodePopoverProps {
  anchorEl: HTMLElement | null;
  onClose: () => void;
  onEdit: () => void;
  onDelete: () => void;
}

const NodePopover = ({ anchorEl, onClose, onEdit, onDelete }: NodePopoverProps) => {
  const { t } = useFormatter();

  return (
    <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={onClose}>
      <MenuItem onClick={(e) => {
        e.stopPropagation();
        onEdit();
      }}
      >
        <ListItemIcon><EditOutlined fontSize="small" /></ListItemIcon>
        <ListItemText>{t('Edit')}</ListItemText>
      </MenuItem>
      <MenuItem onClick={(e) => {
        e.stopPropagation();
        onDelete();
      }}
      >
        <ListItemIcon><DeleteOutlined fontSize="small" /></ListItemIcon>
        <ListItemText>{t('Delete')}</ListItemText>
      </MenuItem>
    </Menu>
  );
};

export default NodePopover;
