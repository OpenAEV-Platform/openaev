import { MoreVert } from '@mui/icons-material';
import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, IconButton, Menu, MenuItem, Slide } from '@mui/material';
import * as R from 'ramda';
import { forwardRef, useContext, useState } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { LessonContext } from '../../common/Context';
import LessonsCategoryForm from './LessonsCategoryForm';

const Transition = forwardRef((props, ref) => (
  <Slide direction="up" ref={ref} {...props} />
));
Transition.displayName = 'TransitionSlide';

const LessonsCategoryPopover = ({ lessonsCategory }) => {
  // utils
  const { t } = useFormatter();
  // states
  const [openDelete, setOpenDelete] = useState(false);
  const [openEdit, setOpenEdit] = useState(false);
  const [anchorEl, setAnchorEl] = useState(null);

  // Context
  const {
    onDeleteLessonsCategory,
    onUpdateLessonsCategory,
  } = useContext(LessonContext);
  // popover management
  const handlePopoverOpen = (event) => {
    event.stopPropagation();
    setAnchorEl(event.currentTarget);
  };
  const handlePopoverClose = () => setAnchorEl(null);
  // Edit action
  const handleOpenEdit = () => {
    setOpenEdit(true);
    handlePopoverClose();
  };
  const handleCloseEdit = () => setOpenEdit(false);
  const onSubmitEdit = (data) => {
    return onUpdateLessonsCategory(
      lessonsCategory.lessonscategory_id,
      data,
    ).then(() => handleCloseEdit());
  };
  // Delete action
  const handleOpenDelete = () => {
    setOpenDelete(true);
    handlePopoverClose();
  };
  const handleCloseDelete = () => setOpenDelete(false);
  const submitDelete = () => {
    onDeleteLessonsCategory(lessonsCategory.lessonscategory_id).then(() => handleCloseDelete());
  };
  // Rendering
  const initialValues = R.pipe(
    R.pick([
      'lessons_category_name',
      'lessons_category_description',
      'lessons_category_order',
    ]),
  )(lessonsCategory);
  return (
    <>
      <IconButton
        onClick={handlePopoverOpen}
        aria-haspopup="true"
        size="small"
      >
        <MoreVert />
      </IconButton>
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={handlePopoverClose}
      >
        <MenuItem onClick={handleOpenEdit}>{t('Update')}</MenuItem>
        <MenuItem onClick={handleOpenDelete}>{t('Delete')}</MenuItem>
      </Menu>
      <Dialog
        open={openDelete}
        TransitionComponent={Transition}
        onClose={handleCloseDelete}
        PaperProps={{ elevation: 1 }}
      >
        <DialogContent>
          <DialogContentText>
            {t('Do you want to delete this lessons learned category?')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDelete}>{t('Cancel')}</Button>
          <Button color="secondary" onClick={submitDelete}>
            {t('Delete')}
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog
        TransitionComponent={Transition}
        open={openEdit}
        onClose={handleCloseEdit}
        fullWidth
        maxWidth="md"
        PaperProps={{ elevation: 1 }}
      >
        <DialogTitle>{t('Update the lessons learned category')}</DialogTitle>
        <DialogContent>
          <LessonsCategoryForm
            editing
            onSubmit={onSubmitEdit}
            handleClose={handleCloseEdit}
            initialValues={initialValues}
          />
        </DialogContent>
      </Dialog>
    </>
  );
};

export default LessonsCategoryPopover;
