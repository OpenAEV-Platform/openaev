import { MoreVert } from '@mui/icons-material';
import { Button, Dialog, DialogActions, DialogContent, DialogContentText, IconButton, Menu, MenuItem } from '@mui/material';
import { type FunctionComponent, type MouseEvent, useContext, useState } from 'react';
import { type SubmitHandler } from 'react-hook-form';

import Drawer from '../../../../../components/common/Drawer';
import Transition from '../../../../../components/common/Transition';
import { useFormatter } from '../../../../../components/i18n';
import { type LessonsQuestion } from '../../../../../utils/api-types';
import { LessonContext } from '../../../common/Context';
import LessonsQuestionForm, { type LessonsQuestionFormInputs } from './LessonsQuestionForm';

interface Props {
  lessonsCategoryId: string;
  lessonsQuestion: LessonsQuestion;
}

const LessonsQuestionPopover: FunctionComponent<Props> = ({
  lessonsCategoryId,
  lessonsQuestion,
}) => {
  // utils
  const { t } = useFormatter();
  // states
  const [openDelete, setOpenDelete] = useState<boolean>(false);
  const [openEdit, setOpenEdit] = useState<boolean>(false);
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);

  // Context
  const {
    onDeleteLessonsQuestion,
    onUpdateLessonsQuestion,
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
  const onSubmitEdit: SubmitHandler<LessonsQuestionFormInputs> = (data) => {
    return onUpdateLessonsQuestion(
      lessonsCategoryId,
      lessonsQuestion.lessonsquestion_id,
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
    // The context types the deletion as synchronous whereas the thunk resolves a promise: wrapping
    // keeps the original "close once deleted" sequencing without lying about the declared type.
    Promise.resolve(onDeleteLessonsQuestion(
      lessonsCategoryId,
      lessonsQuestion.lessonsquestion_id,
    )).then(() => handleCloseDelete());
  };
  // Rendering
  const initialValues: Partial<LessonsQuestionFormInputs> = {
    lessons_question_content: lessonsQuestion.lessons_question_content,
    lessons_question_explanation: lessonsQuestion.lessons_question_explanation ?? '',
    lessons_question_order: lessonsQuestion.lessons_question_order,
  };
  return (
    <div>
      <IconButton onClick={handlePopoverOpen} aria-haspopup="true" size="small" color="primary" sx={{ borderRadius: 1 }}>
        <MoreVert fontSize="small" />
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
            {t('Do you want to delete this lessons learned question?')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button variant="outlined" color="primary" onClick={handleCloseDelete}>{t('Cancel')}</Button>
          <Button variant="contained" color="primary" onClick={submitDelete}>
            {t('Delete')}
          </Button>
        </DialogActions>
      </Dialog>
      <Drawer
        open={openEdit}
        handleClose={handleCloseEdit}
        title={t('Update the lessons learned question')}
      >
        <LessonsQuestionForm
          editing
          onSubmit={onSubmitEdit}
          handleClose={handleCloseEdit}
          initialValues={initialValues}
        />
      </Drawer>
    </div>
  );
};

export default LessonsQuestionPopover;
