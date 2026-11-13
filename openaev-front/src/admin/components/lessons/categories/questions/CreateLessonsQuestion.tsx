import { IconButton } from '@filigran/design-system';
import { Add, ControlPointOutlined } from '@mui/icons-material';
import { Dialog, DialogContent, DialogTitle, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
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
    const createdId = result.result;
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
            slotProps={{
              primary: {
                sx: {
                  fontSize: 15,
                  color: 'primary.main',
                  fontWeight: 500,
                },
              },
            }}
          />
        </ListItemButton>
      ) : (
        <IconButton
          icon={<Add fontSize="small" />}
          aria-label={t('Add')}
          onClick={handleOpen}
          aria-haspopup="true"
          priority="tertiary"
          size="md"
        />
      )}
      <Dialog
        open={open}
        onClose={handleClose}
        fullWidth
        maxWidth="md"
        slots={{ transition: Transition }}
        slotProps={{ paper: { elevation: 1 } }}
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
