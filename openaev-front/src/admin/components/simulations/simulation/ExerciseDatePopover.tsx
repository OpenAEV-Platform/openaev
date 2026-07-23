import { UpdateOutlined } from '@mui/icons-material';
import { Dialog, DialogContent, DialogTitle, IconButton, Tooltip } from '@mui/material';
import { type FunctionComponent, useState } from 'react';

import { updateExerciseStartDate } from '../../../../actions/Exercise';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { type Exercise, type SimulationDetails } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import ExerciseDateForm from './ExerciseDateForm';

interface BaseProps {
  exercise: SimulationDetails;
  showTrigger?: boolean;
}

// Controlled mode: when `open` is provided the dialog is driven by the parent
// (e.g. from a menu item), so `onOpenChange` is required - without it the
// dialog could never close. Left uncontrolled, it renders its own icon button.
type Props = BaseProps & (
  {
    open: boolean;
    onOpenChange: (open: boolean) => void;
  } | {
    open?: undefined;
    onOpenChange?: undefined;
  }
);

const ExerciseDatePopover: FunctionComponent<Props> = ({ exercise, open, onOpenChange, showTrigger = true }) => {
  const [internalOpen, setInternalOpen] = useState(false);
  const isControlled = open !== undefined;
  const openEdit = isControlled ? open : internalOpen;
  const setOpenEdit = (value: boolean) => {
    if (isControlled) {
      onOpenChange?.(value);
    } else {
      setInternalOpen(value);
    }
  };
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const onSubmitEdit = async (data: Pick<Exercise, 'exercise_start_date'>) => {
    await dispatch(updateExerciseStartDate(exercise.exercise_id, data));
    setOpenEdit(false);
  };
  const initialValues = { exercise_start_date: exercise.exercise_start_date };
  return (
    <>
      {showTrigger && (
        <Tooltip title={(t('Modify the scheduling'))}>
          <span>
            <IconButton size="small" color="primary" onClick={() => setOpenEdit(true)} style={{ marginRight: 5 }} disabled={exercise.exercise_status !== 'SCHEDULED'}>
              <UpdateOutlined />
            </IconButton>
          </span>
        </Tooltip>
      )}
      <Dialog
        TransitionComponent={Transition}
        open={openEdit}
        onClose={() => setOpenEdit(false)}
        PaperProps={{ elevation: 1 }}
        maxWidth="xs"
        fullWidth
      >
        <DialogTitle>{t('Update simulation start date and time')}</DialogTitle>
        <DialogContent>
          <ExerciseDateForm
            initialValues={initialValues}
            onSubmit={onSubmitEdit}
            handleClose={() => setOpenEdit(false)}
          />
        </DialogContent>
      </Dialog>
    </>
  );
};

export default ExerciseDatePopover;
