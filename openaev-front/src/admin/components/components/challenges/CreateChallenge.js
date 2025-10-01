import { Add, ControlPointOutlined } from '@mui/icons-material';
import { Dialog, DialogContent, DialogTitle, Fab, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import * as R from 'ramda';
import { useState } from 'react';
import { useDispatch } from 'react-redux';
import { makeStyles } from 'tss-react/mui';

import { addChallenge } from '../../../../actions/challenge-action.js';
import Drawer from '../../../../components/common/Drawer';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import ChallengeForm from './ChallengeForm';

const useStyles = makeStyles()(theme => ({
  createButton: {
    position: 'fixed',
    bottom: 30,
    right: 30,
  },
  text: {
    fontSize: 15,
    color: theme.palette.primary.main,
    fontWeight: 500,
  },
}));

const CreateChallenge = (props) => {
  const { onCreate, inline } = props;
  const { classes } = useStyles();
  const dispatch = useDispatch();
  const { t } = useFormatter();
  const [open, setOpen] = useState(false);
  const handleOpen = () => setOpen(true);
  const handleClose = () => setOpen(false);
  const onSubmit = (data) => {
    const inputValues = R.pipe(
      R.assoc('challenge_tags', R.pluck('id', data.challenge_tags)),
    )(data);
    return dispatch(addChallenge(inputValues)).then((result) => {
      if (result.result) {
        if (onCreate) {
          onCreate(result.result);
        }
        return handleClose();
      }
      return result;
    });
  };
  return (
    <div>
      {inline === true ? (
        <ListItemButton divider={true} onClick={handleOpen} color="primary">
          <ListItemIcon color="primary">
            <ControlPointOutlined color="primary" />
          </ListItemIcon>
          <ListItemText
            primary={t('Create a new challenge')}
            classes={{ primary: classes.text }}
          />
        </ListItemButton>
      ) : (
        <Fab
          onClick={handleOpen}
          color="primary"
          aria-label="Add"
          className={classes.createButton}
        >
          <Add />
        </Fab>
      )}
      {inline ? (
        <Dialog
          open={open}
          TransitionComponent={Transition}
          onClose={handleClose}
          fullWidth
          maxWidth="md"
          PaperProps={{ elevation: 1 }}
        >
          <DialogTitle>{t('Create a new challenge')}</DialogTitle>
          <DialogContent>
            <ChallengeForm
              editing={false}
              onSubmit={onSubmit}
              handleClose={handleClose}
              initialValues={{ challenge_tags: [] }}
            />
          </DialogContent>
        </Dialog>
      ) : (
        <Drawer
          open={open}
          handleClose={() => setOpen(false)}
          title={t('Create a new challenge')}
        >
          <ChallengeForm
            editing={false}
            onSubmit={onSubmit}
            handleClose={handleClose}
            initialValues={{ challenge_tags: [] }}
          />
        </Drawer>
      )}
    </div>
  );
};

export default CreateChallenge;
