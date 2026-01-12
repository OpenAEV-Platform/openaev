import { ControlPointOutlined } from '@mui/icons-material';
import { Dialog, DialogContent, DialogTitle, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { useContext, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import ButtonCreate from '../../../../components/common/ButtonCreate';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { LessonContext } from '../../common/Context';
import LessonsCategoryForm from './LessonsCategoryForm';

const useStyles = makeStyles()(theme => ({
  text: {
    fontSize: 15,
    color: theme.palette.primary.main,
    fontWeight: 500,
  },
}));

const CreateLessonsCategory = (props) => {
  const { onCreate, inline } = props;
  const { classes } = useStyles();
  const { t } = useFormatter();
  const [open, setOpen] = useState(false);

  // Context
  const { onAddLessonsCategory } = useContext(LessonContext);

  const handleOpen = () => setOpen(true);
  const handleClose = () => setOpen(false);
  const onSubmit = (data) => {
    return onAddLessonsCategory(data).then((result) => {
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
        <ListItemButton divider onClick={handleOpen} color="primary">
          <ListItemIcon color="primary">
            <ControlPointOutlined color="primary" />
          </ListItemIcon>
          <ListItemText
            primary={t('Create a new lessons learned category')}
            classes={{ primary: classes.text }}
          />
        </ListItemButton>
      ) : (
        <ButtonCreate onClick={handleOpen} />
      )}
      <Dialog
        open={open}
        TransitionComponent={Transition}
        onClose={handleClose}
        fullWidth
        maxWidth="md"
        PaperProps={{ elevation: 1 }}
      >
        <DialogTitle>{t('Create a new lessons learned category')}</DialogTitle>
        <DialogContent>
          <LessonsCategoryForm
            editing={false}
            onSubmit={onSubmit}
            handleClose={handleClose}
            initialValues={{}}
          />
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default CreateLessonsCategory;
