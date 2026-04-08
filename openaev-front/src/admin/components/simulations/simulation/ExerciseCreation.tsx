import { type ReactElement, useCallback, useState } from 'react';
import { useNavigate } from 'react-router';

import { addExercise } from '../../../../actions/Exercise';
import { type LoggedHelper } from '../../../../actions/helper';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type CreateExerciseInput, type Exercise, type PlatformSettings } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { isFeatureEnabled } from '../../../../utils/utils';
import EngineTypeSelection, { type EngineType } from '../../common/EngineTypeSelection';
import ExerciseForm from './ExerciseForm';
import ExerciseFormChaining from './ExerciseFormChaining';

const ExerciseCreation = () => {
  // Standard hooks
  const isChainingFeatureEnabled = isFeatureEnabled('INJECT_CHAINING');
  const [open, setOpen] = useState(false);
  const [engineType, setEngineType] = useState<EngineType>(isChainingFeatureEnabled ? null : 'time-based');
  const { t } = useFormatter();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();

  const handleTypeSelected = useCallback((type: EngineType) => {
    setEngineType(type);
  }, []);

  const onSubmit = (data: CreateExerciseInput) => {
    const payload: CreateExerciseInput = {
      ...data,
      exercise_is_chaining: engineType === 'chaining',
    };
    dispatch(addExercise(payload)).then((result: {
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

  const renderDrawerContent = (): ReactElement => {
    // if feature flag is disabled we just display the old form
    if (!isChainingFeatureEnabled) {
      return (
        <ExerciseForm
          onSubmit={onSubmit}
          handleClose={() => setOpen(false)}
          initialValues={initialValues}
          edit={false}
        />
      );
    }

    return (
      <>
        <EngineTypeSelection
          selected={engineType}
          onSelect={handleTypeSelected}
        />
        {/* if scenario type is selected (standard or chaining), then display the form */}
        {engineType !== null && (
          <ExerciseFormChaining
            onSubmit={onSubmit}
            handleClose={() => setOpen(false)}
            initialValues={initialValues}
            edit={false}
            isChaining={engineType === 'chaining'}
          />
        )}
      </>
    );
  };

  return (
    <>
      <ButtonCreate onClick={() => setOpen(true)} />
      <Drawer
        open={open}
        handleClose={() => setOpen(false)}
        title={t('Create a new simulation')}
      >
        {renderDrawerContent}
      </Drawer>
    </>
  );
};

export default ExerciseCreation;
