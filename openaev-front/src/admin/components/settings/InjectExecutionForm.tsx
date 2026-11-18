import { Button } from '@filigran/design-system';
import { zodResolver } from '@hookform/resolvers/zod';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type SyntheticEvent, useEffect } from 'react';
import { FormProvider, useForm } from 'react-hook-form';
import { z } from 'zod';

import TextFieldController from '../../../components/fields/TextFieldController';
import { useFormatter } from '../../../components/i18n';
import { type SettingsInjectExecutionUpdateInput } from '../../../utils/api-types';
import { Can } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { requiredInteger, zodImplement } from '../../../utils/Zod';

export const INJECT_EXECUTION_THRESHOLD_MIN = 1;
export const INJECT_EXECUTION_THRESHOLD_MAX = 1440;

interface InjectExecutionFormProps {
  onSubmit: (data: SettingsInjectExecutionUpdateInput) => void;
  initialValues: SettingsInjectExecutionUpdateInput;
  configuredThresholdMinutes?: number;
  canNotManage: boolean;
}

const InjectExecutionForm: FunctionComponent<InjectExecutionFormProps> = ({
  onSubmit,
  initialValues,
  configuredThresholdMinutes,
  canNotManage,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const methods = useForm<SettingsInjectExecutionUpdateInput>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<SettingsInjectExecutionUpdateInput>().with({
        inject_execution_threshold_minutes: requiredInteger(t)
          .refine(
            value => value >= INJECT_EXECUTION_THRESHOLD_MIN && value <= INJECT_EXECUTION_THRESHOLD_MAX,
            { message: t('Must be between 1 and 1440 minutes') },
          ),
      }),
    ),
    defaultValues: initialValues,
  });
  const {
    handleSubmit,
    formState: { isSubmitting, isDirty },
    reset,
  } = methods;

  const handleSubmitWithoutPropagation = (e: SyntheticEvent) => {
    e.preventDefault();
    e.stopPropagation();
    handleSubmit(onSubmit)(e);
  };

  useEffect(() => {
    reset(initialValues);
  }, [initialValues, reset]);

  const sideEffects = t('Pending injects past this delay end in error. Agent-based injects are waited on longer and implants are kept alive longer when it is raised.');
  const helperText = configuredThresholdMinutes !== undefined
    ? `${sideEffects} ${t('Configured value')}: ${configuredThresholdMinutes} ${t('minutes')}`
    : sideEffects;

  return (
    <FormProvider {...methods}>
      <form
        noValidate
        id="injectExecutionForm"
        onSubmit={handleSubmitWithoutPropagation}
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(2.5),
        }}
      >
        <TextFieldController
          required
          type="number"
          min={INJECT_EXECUTION_THRESHOLD_MIN}
          step={1}
          name="inject_execution_threshold_minutes"
          label={t('Inject execution threshold')}
          endAdornmentLabel={t('minutes')}
          helperText={helperText}
          disabled={canNotManage}
        />
        <div>
          <Can I={ACTIONS.MANAGE} a={SUBJECTS.PLATFORM_SETTINGS}>
            <Button type="submit" disabled={!isDirty || isSubmitting}>
              {t('Update')}
            </Button>
          </Can>
        </div>
      </form>
    </FormProvider>
  );
};

export default InjectExecutionForm;
