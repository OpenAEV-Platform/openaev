import { Dialog, DialogContent, DialogTitle } from '@mui/material';
import { type FunctionComponent, useState } from 'react';

import { addChallenge } from '../../../../actions/challenge-action';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Drawer from '../../../../components/common/Drawer';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { type ChallengeInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import ChallengeForm from './ChallengeForm';

interface Props {
  onCreate?: (result: string) => void;
  inline?: boolean;
}

const CreateChallenge: FunctionComponent<Props> = ({ onCreate, inline = false }) => {
  const dispatch = useAppDispatch();
  const { t } = useFormatter();
  const [open, setOpen] = useState(false);
  const handleOpen = () => setOpen(true);
  const handleClose = () => setOpen(false);

  const onSubmit = (data: ChallengeInput) => {
    return dispatch(addChallenge(data)).then((result: { result?: string }) => {
      if (result.result) {
        onCreate?.(result.result);
        return handleClose();
      }
      return result;
    });
  };

  const challengeForm = (
    <ChallengeForm
      editing={false}
      onSubmit={onSubmit}
      handleClose={handleClose}
    />
  );

  return (
    <div>
      {inline ? (
        // Header placement (picker top-right): compact creation button.
        <ButtonCreate onClick={handleOpen} label={t('Create a new challenge')} />
      ) : (
        <ButtonCreate onClick={handleOpen} />
      )}
      {inline ? (
        <Dialog
          open={open}
          slots={{ transition: Transition }}
          onClose={handleClose}
          fullWidth
          maxWidth="md"
          slotProps={{ paper: { elevation: 1 } }}
        >
          <DialogTitle>{t('Create a new challenge')}</DialogTitle>
          <DialogContent>
            {challengeForm}
          </DialogContent>
        </Dialog>
      ) : (
        <Drawer
          open={open}
          handleClose={handleClose}
          title={t('Create a new challenge')}
        >
          {challengeForm}
        </Drawer>
      )}
    </div>
  );
};

export default CreateChallenge;
