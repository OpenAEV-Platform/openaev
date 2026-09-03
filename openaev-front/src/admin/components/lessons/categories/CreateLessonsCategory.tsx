import { ControlPointOutlined } from '@mui/icons-material';
import { Dialog, DialogContent, DialogTitle, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type FunctionComponent, useContext, useState } from 'react';
import { type SubmitHandler } from 'react-hook-form';

import ButtonCreate from '../../../../components/common/ButtonCreate';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { LessonContext } from '../../common/Context';
import LessonsCategoryForm, { type LessonsCategoryFormInputs } from './LessonsCategoryForm';

interface Props {
  onCreate?: (lessonsCategoryId: string) => void;
  inline?: boolean;
}

const CreateLessonsCategory: FunctionComponent<Props> = ({ onCreate, inline }) => {
  const { t } = useFormatter();
  const [open, setOpen] = useState<boolean>(false);

  // Context
  const { onAddLessonsCategory } = useContext(LessonContext);

  const handleOpen = () => setOpen(true);
  const handleClose = () => setOpen(false);
  const onSubmit: SubmitHandler<LessonsCategoryFormInputs> = (data) => {
    return onAddLessonsCategory(data).then((result) => {
      // The thunk resolves the normalised `{ result, entities }` payload, whereas the context
      // types it as the created `LessonsCategory`: keep the original runtime check on `result`.
      const createdId = (result as unknown as { result?: string }).result;
      if (createdId) {
        if (onCreate) {
          onCreate(createdId);
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
