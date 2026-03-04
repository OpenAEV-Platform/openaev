import { Add, BoltOutlined, PlayArrowOutlined } from '@mui/icons-material';
import { Avatar, Button, List, ListItemAvatar, ListItemButton, ListItemText } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useState } from 'react';

import Dialog from '../../../../../components/common/dialog/Dialog';
import { useFormatter } from '../../../../../components/i18n';

interface Props {
  onAddAction: () => void;
  onAddEvent: () => void;
}

const LogicAddComponentDialog: FunctionComponent<Props> = ({ onAddAction, onAddEvent }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const [open, setOpen] = useState(false);

  return (
    <>
      <Button
        variant="contained"
        color="primary"
        startIcon={<Add />}
        onClick={() => setOpen(true)}
      >
        {t('Add component')}
      </Button>
      <Dialog
        open={open}
        handleClose={() => setOpen(false)}
        title={t('Add a component')}
        maxWidth="xs"
      >
        <List sx={{ py: 0 }}>
          <ListItemButton
            onClick={() => {
              setOpen(false);
              onAddAction();
            }}
            sx={{ py: 2 }}
          >
            <ListItemAvatar>
              <Avatar sx={{ bgcolor: `${theme.palette.primary.main}20` }}>
                <PlayArrowOutlined sx={{ color: theme.palette.primary.main }} />
              </Avatar>
            </ListItemAvatar>
            <ListItemText
              primary={t('Action')}
              secondary={t('Execute an inject as part of your attack chain')}
              primaryTypographyProps={{ fontWeight: 'bold' }}
            />
          </ListItemButton>
          <ListItemButton
            onClick={() => {
              setOpen(false);
              onAddEvent();
            }}
            sx={{ py: 2 }}
          >
            <ListItemAvatar>
              <Avatar sx={{ bgcolor: `${theme.palette.warning.main}20` }}>
                <BoltOutlined sx={{ color: theme.palette.warning.main }} />
              </Avatar>
            </ListItemAvatar>
            <ListItemText
              primary={t('Event')}
              secondary={t('A trigger with conditions that controls execution flow')}
              primaryTypographyProps={{ fontWeight: 'bold' }}
            />
          </ListItemButton>
        </List>
      </Dialog>
    </>
  );
};

export default LogicAddComponentDialog;
