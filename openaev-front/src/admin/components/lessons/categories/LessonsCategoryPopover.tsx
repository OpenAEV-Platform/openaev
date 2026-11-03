import { Button, IconButton } from '@filigran/design-system';
import { MoreVert } from '@mui/icons-material';
import { Dialog, DialogActions, DialogContent, DialogContentText, Menu, MenuItem } from '@mui/material';
import { type FunctionComponent, type MouseEvent, useContext, useState } from 'react';
import { type SubmitHandler } from 'react-hook-form';

import Drawer from '../../../../components/common/Drawer';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { type LessonsCategory } from '../../../../utils/api-types';
import { LessonContext } from '../../common/Context';
import LessonsCategoryForm, { type LessonsCategoryFormInputs } from './LessonsCategoryForm';

interface Props { lessonsCategory: LessonsCategory }

const LessonsCategoryPopover: FunctionComponent<Props> = ({ lessonsCategory }) => {
  // utils
  const { t } = useFormatter();
  // states
  const [openDelete, setOpenDelete] = useState<boolean>(false);
  const [openEdit, setOpenEdit] = useState<boolean>(false);
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);

  // Context
  const {
    onDeleteLessonsCategory,
    onUpdateLessonsCategory,
  } = useContext(LessonContext);
  // popover management
  const handlePopoverOpen = (event: MouseEvent<HTMLElement>) => {
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
  const onSubmitEdit: SubmitHandler<LessonsCategoryFormInputs> = (data) => {
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
    onDeleteLessonsCategory(lessonsCategory.lessonscategory_id)
      .then(() => handleCloseDelete());
  };
  // Rendering
  const initialValues: Partial<LessonsCategoryFormInputs> = {
    lessons_category_name: lessonsCategory.lessons_category_name,
    lessons_category_description: lessonsCategory.lessons_category_description ?? '',
    lessons_category_order: lessonsCategory.lessons_category_order,
  };
  return (
    <>
      <IconButton
        icon={<MoreVert />}
        aria-label={t('More actions')}
        onClick={handlePopoverOpen}
        aria-haspopup="true"
        priority="tertiary"
        size="sm"
      />
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
        onClose={handleCloseDelete}
        slots={{ transition: Transition }}
        slotProps={{ paper: { elevation: 1 } }}
      >
        <DialogContent>
          <DialogContentText>
            {t('Do you want to delete this lessons learned category?')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button type="button" priority="secondary" onClick={handleCloseDelete}>{t('Cancel')}</Button>
          <Button type="button" onClick={submitDelete}>
            {t('Delete')}
          </Button>
        </DialogActions>
      </Dialog>
      <Drawer
        open={openEdit}
        handleClose={handleCloseEdit}
        title={t('Update the lessons learned category')}
      >
        <LessonsCategoryForm
          editing
          onSubmit={onSubmitEdit}
          handleClose={handleCloseEdit}
          initialValues={initialValues}
        />
      </Drawer>
    </>
  );
};

export default LessonsCategoryPopover;
