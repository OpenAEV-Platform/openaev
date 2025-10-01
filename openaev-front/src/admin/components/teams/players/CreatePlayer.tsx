import { ControlPointOutlined } from '@mui/icons-material';
import { ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type FunctionComponent, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { addPlayer } from '../../../../actions/User';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Dialog from '../../../../components/common/dialog/Dialog';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type PlayerInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { type Option } from '../../../../utils/Option';
import { type PlayerInputForm, type UserStore } from './Player';
import PlayerForm from './PlayerForm';

const useStyles = makeStyles()(theme => ({
  text: {
    fontSize: theme.typography.h2.fontSize,
    color: theme.palette.primary.main,
    fontWeight: theme.typography.h2.fontWeight,
  },
}));

interface CreatePlayerProps {
  inline?: boolean;
  onCreate: (result: UserStore) => void;
}

const CreatePlayer: FunctionComponent<CreatePlayerProps> = ({
  inline = false,
  onCreate,
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();
  const dispatch = useAppDispatch();

  const [openDialog, setOpenDialog] = useState(false);

  const handleOpen = () => setOpenDialog(true);
  const handleClose = () => setOpenDialog(false);

  const onSubmit = (data: PlayerInputForm) => {
    const inputValues: PlayerInput = {
      ...data,
      user_organization: data.user_organization?.id,
      user_country: data.user_country?.id,
      user_tags: data.user_tags?.map((tag: Option) => tag.id),
    };
    return dispatch(addPlayer(inputValues)).then(
      (result: {
        result: string;
        entities: { users: Record<string, UserStore> };
      }) => {
        if (result.result) {
          if (onCreate) {
            const created = result.entities.users[result.result];
            onCreate(created);
          }
          return handleClose();
        }
        return result;
      },
    );
  };

  return (
    <div>
      {inline ? (
        <>
          <ListItemButton divider onClick={handleOpen} color="primary">
            <ListItemIcon color="primary">
              <ControlPointOutlined color="primary" />
            </ListItemIcon>
            <ListItemText
              primary={t('Create a new player')}
              classes={{ primary: classes.text }}
            />
          </ListItemButton>
          <Dialog
            open={openDialog}
            handleClose={handleClose}
            title={t('Create a new player')}
          >
            <PlayerForm
              initialValues={{ user_tags: [] }}
              handleClose={handleClose}
              onSubmit={onSubmit}
            />
          </Dialog>
        </>
      ) : (
        <>
          <ButtonCreate onClick={handleOpen} />
          <Drawer
            open={openDialog}
            handleClose={handleClose}
            title={t('Create a new player')}
          >
            <PlayerForm
              initialValues={{ user_tags: [] }}
              handleClose={handleClose}
              onSubmit={onSubmit}
            />
          </Drawer>
        </>
      )}
    </div>
  );
};

export default CreatePlayer;
