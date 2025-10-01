import { useState } from 'react';
import { useNavigate } from 'react-router';

import { addExercise } from '../../../../actions/Exercise';
import { type LoggedHelper } from '../../../../actions/helper';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type CreateExerciseInput, type Exercise, type PlatformSettings } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import ExerciseForm from './ExerciseForm';

const ExerciseCreation = () => {
  // Standard hooks
  const [open, setOpen] = useState(false);
  const { t } = useFormatter();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const onSubmit = (data: CreateExerciseInput) => {
    dispatch(addExercise(data)).then((result: {
      result: string;
      entities: { scenarios: Record<string, Exercise> };
    }) => {
      setOpen(false);
      navigate(`/admin/simulations/${result.result}`);
    });
  };

  const { settings }: { settings: PlatformSettings } = useHelper((helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }));

  // Form
  const initialValues: CreateExerciseInput = {
    exercise_name: '',
    exercise_subtitle: '',
    exercise_description: '',
    exercise_category: 'attack-scenario',
    exercise_main_focus: 'incident-response',
    exercise_severity: 'high',
    exercise_tags: [],
    exercise_start_date: null,
    exercise_mail_from: settings.default_mailer,
    exercise_mails_reply_to: [settings.default_reply_to ? settings.default_reply_to : ''],
    exercise_message_header: t('SIMULATION HEADER'),
    exercise_message_footer: t('SIMULATION FOOTER'),
  };

  return (
    <>
      <ButtonCreate onClick={() => setOpen(true)} />
      <Drawer
        open={open}
        handleClose={() => setOpen(false)}
        title={t('Create a new simulation')}
      >
        <ExerciseForm
          onSubmit={onSubmit}
          handleClose={() => setOpen(false)}
          initialValues={initialValues}
          edit={false}
        />
      </Drawer>
    </>
  );
};

export default ExerciseCreation;
