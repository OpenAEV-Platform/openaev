import { Dialog, DialogContent, DialogTitle } from '@mui/material';
import { type FunctionComponent, useContext, useState } from 'react';
import { type SubmitHandler } from 'react-hook-form';

import ButtonCreate from '../../../components/common/ButtonCreate';
import Transition from '../../../components/common/Transition';
import { useFormatter } from '../../../components/i18n';
import { LessonContext } from '../common/Context';
import ObjectiveForm, { type ObjectiveFormInputs } from './ObjectiveForm';

const CreateObjective: FunctionComponent = () => {
  // Standard hooks
  const { t } = useFormatter();
  const [open, setOpen] = useState<boolean>(false);

  // Context
  const { onAddObjective } = useContext(LessonContext);

  const handleOpen = () => setOpen(true);
  const handleClose = () => setOpen(false);

  const onSubmit: SubmitHandler<ObjectiveFormInputs> = data => onAddObjective(data)
    .then((result) => {
      // The thunk resolves the normalised `{ result, entities }` payload, whereas the context
      // types it as the created `Objective`: keep the original runtime check on `result`.
      if ((result as unknown as { result?: string }).result) {
        handleClose();
      }
      return result;
    });

  return (
    <>
      <ButtonCreate onClick={handleOpen} />
      <Dialog
        open={open}
        TransitionComponent={Transition}
        onClose={handleClose}
        fullWidth
        maxWidth="md"
        PaperProps={{ elevation: 1 }}
      >
        <DialogTitle>{t('Create a new objective')}</DialogTitle>
        <DialogContent>
          <ObjectiveForm
            initialValues={{ objective_priority: 1 }}
            onSubmit={onSubmit}
            handleClose={handleClose}
          />
        </DialogContent>
      </Dialog>
    </>
  );
};

export default CreateObjective;
