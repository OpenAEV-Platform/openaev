import { Add, ControlPointOutlined } from '@mui/icons-material';
import {
  Dialog,
  DialogContent,
  DialogTitle,
  IconButton,
  ListItemButton,
  ListItemIcon,
  ListItemText,
} from '@mui/material';
import { type FunctionComponent, useContext, useState } from 'react';
import { type SubmitHandler } from 'react-hook-form';

import Transition from '../../../../../components/common/Transition';
import { useFormatter } from '../../../../../components/i18n';
import { LessonContext } from '../../../common/Context';
import LessonsQuestionForm, { type LessonsQuestionFormInputs } from './LessonsQuestionForm';

interface Props {
  lessonsCategoryId: string;
  onCreate?: (lessonsQuestionId: string) => void;
  inline?: boolean;
}

const CreateLessonsQuestion: FunctionComponent<Props> = ({ onCreate, inline, lessonsCategoryId }) => {
  const { t } = useFormatter();
  const [open, setOpen] = useState<boolean>(false);

  // Context
  const { onAddLessonsQuestion } = useContext(LessonContext);

  const handleOpen = () => setOpen(true);
  const handleClose = () => setOpen(false);
  const onSubmit: SubmitHandler<LessonsQuestionFormInputs> = async (data) => {
    const result = await onAddLessonsQuestion(lessonsCategoryId, data);
    // The thunk resolves the normalised `{ result, entities }` payload, whereas the context types
    // it as the created `LessonsQuestion`: keep the original runtime check on `result`.
    const createdId = (result as unknown as { result?: string }).result;
    if (createdId) {
      if (onCreate) {
        onCreate(createdId);
      }
      return handleClose();
    }
    return result;
  };
  return (
    <div>
      {inline === true ? (
        <ListItemButton divider onClick={handleOpen} color="primary">
          <ListItemIcon color="primary">
            <ControlPointOutlined color="primary" />
          </ListItemIcon>
          <ListItemText
            primary={t('Create a new lessons learned question')}
            primaryTypographyProps={{
              sx: {
                fontSize: 15,
                color: 'primary.main',
                fontWeight: 500,
              },
            }}
          />
        </ListItemButton>
      ) : (
        <IconButton
          onClick={handleOpen}
          aria-haspopup="true"
          size="large"
          color="secondary"
        >
          <Add fontSize="small" />
        </IconButton>
      )}
      <Dialog
        open={open}
        TransitionComponent={Transition}
        onClose={handleClose}
        fullWidth
        maxWidth="md"
        PaperProps={{ elevation: 1 }}
      >
        <DialogTitle>{t('Create a new lessons learned question')}</DialogTitle>
        <DialogContent>
          <LessonsQuestionForm
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

export default CreateLessonsQuestion;
