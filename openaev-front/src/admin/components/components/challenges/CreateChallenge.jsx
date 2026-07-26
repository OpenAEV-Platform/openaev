import { Dialog, DialogContent, DialogTitle } from '@mui/material';
import * as R from 'ramda';
import { useState } from 'react';
import { useDispatch } from 'react-redux';

import { addChallenge } from '../../../../actions/challenge-action';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Drawer from '../../../../components/common/Drawer';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import ChallengeForm from './ChallengeForm';

const CreateChallenge = (props) => {
  const { onCreate, inline } = props;
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
        // Header placement (picker top-right): compact creation button.
        <ButtonCreate onClick={handleOpen} label={t('Create a new challenge')} />
      ) : (
        <ButtonCreate onClick={handleOpen} />
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
