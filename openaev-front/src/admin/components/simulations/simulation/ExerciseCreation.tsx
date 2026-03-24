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
import EngineTypeSelection from '../../common/EngineTypeSelection';
import ExerciseForm from './ExerciseForm';

type CreationStep = 'type-selection' | 'form';

const ExerciseCreation = () => {
  // Standard hooks
  const isChainingFeatureEnabled = isFeatureEnabled('INJECT_CHAINING');
  const [open, setOpen] = useState(false);
  const [creationStep, setCreationStep] = useState<CreationStep>(isChainingFeatureEnabled ? 'type-selection' : 'form');
  const [isChaining, setIsChaining] = useState<boolean>(false);
  const { t } = useFormatter();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();

  const handleClose = useCallback(() => {
    setOpen(false);
    setCreationStep(isChainingFeatureEnabled ? 'type-selection' : 'form');
    setIsChaining(false);
  }, [isChainingFeatureEnabled]);

  const handleTypeSelected = useCallback((chaining: boolean) => {
    setIsChaining(chaining);
    setCreationStep('form');
  }, []);

  const onSubmit = (data: CreateExerciseInput) => {
    const payload: CreateExerciseInput = {
      ...data,
      exercise_is_chaining: isChaining,
    };
    dispatch(addExercise(payload)).then((result: {
      result: string;
      entities: { scenarios: Record<string, Exercise> };
    }) => {
      handleClose();
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

  const drawerTitle = (creationStep === 'type-selection' && isChainingFeatureEnabled)
    ? t('Create a new simulation')
    : isChaining
      ? `${t('Create a new simulation')} — ${t('Chaining')}`
      : isChainingFeatureEnabled
        ? `${t('Create a new simulation')} — ${t('Time-based')}`
        : t('Create a new simulation');

  const renderDrawerContent = (): ReactElement => {
    if (creationStep === 'type-selection' && isChainingFeatureEnabled) {
      return (
        <EngineTypeSelection
          onSelect={handleTypeSelected}
          onCancel={handleClose}
        />
      );
    }
    return (
      <ExerciseForm
        onSubmit={onSubmit}
        handleClose={handleClose}
        initialValues={initialValues}
        edit={false}
      />
    );
  };

  return (
    <>
      <ButtonCreate onClick={() => setOpen(true)} />
      <Drawer
        open={open}
        handleClose={handleClose}
        title={drawerTitle}
      >
        {renderDrawerContent}
      </Drawer>
    </>
  );
};

export default ExerciseCreation;
