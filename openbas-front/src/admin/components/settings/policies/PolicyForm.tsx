import { Button } from '@mui/material';
import { type FunctionComponent, useEffect } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';

import MarkDownFieldController from '../../../../components/fields/MarkDownFieldController';
import { useFormatter } from '../../../../components/i18n';
import { type PolicyInput } from '../../../../utils/api-types';

interface Props {
  onSubmit: SubmitHandler<PolicyInput>;
  initialValues?: PolicyInput;
}

const PolicyForm: FunctionComponent<Props> = ({
  onSubmit,
  initialValues = {
    platform_login_message: '',
    platform_consent_message: '',
    platform_consent_confirm_text: '',
  },
}) => {
  const { t } = useFormatter();

  const methods = useForm<PolicyInput>({
    mode: 'onTouched',
    defaultValues: initialValues,
  });

  const {
    handleSubmit,
    formState: { isDirty, isSubmitting },
    reset,
  } = methods;

  useEffect(() => {
    reset(initialValues);
  }, [initialValues, reset]);

  return (
    <FormProvider {...methods}>
      <form id="policyForm" onSubmit={handleSubmit(onSubmit)}>
        <MarkDownFieldController
          name="platform_login_message"
          label={t('Platform login message')}
          style={{ marginTop: 0 }}
          askAi={false}
          inInject={false}
          inArticle={false}
        />
        <MarkDownFieldController
          name="platform_consent_message"
          label={t('Platform consent message')}
          style={{ marginTop: 20 }}
          askAi={false}
          inInject={false}
          inArticle={false}
        />
        <MarkDownFieldController
          name="platform_consent_confirm_text"
          label={t('Platform consent confirm text')}
          style={{ marginTop: 20 }}
          askAi={false}
          inInject={false}
          inArticle={false}
        />
        <div style={{ marginTop: 20 }}>
          <Button
            variant="contained"
            color="secondary"
            type="submit"
            disabled={!isDirty || isSubmitting}
          >
            {t('Update')}
          </Button>
        </div>
      </form>
    </FormProvider>
  );
};

export default PolicyForm;
