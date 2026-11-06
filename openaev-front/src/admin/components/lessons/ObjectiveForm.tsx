import { Button } from '@filigran/design-system';
import { zodResolver } from '@hookform/resolvers/zod';
import { type FunctionComponent, type SyntheticEvent } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import TextFieldController from '../../../components/fields/TextFieldController';
import { useFormatter } from '../../../components/i18n';
import { optionalText, requiredInteger, zodImplement } from '../../../utils/Zod';

// The form requires a title and a priority, whereas both are optional on the API input: the
// dedicated form type keeps `handleSubmit` payloads assignable to `ObjectiveInput`.
export interface ObjectiveFormInputs {
  objective_title: string;
  objective_description?: string;
  objective_priority: number;
}

interface Props {
  onSubmit: SubmitHandler<ObjectiveFormInputs>;
  handleClose: () => void;
  editing?: boolean;
  initialValues?: Partial<ObjectiveFormInputs>;
}

const ObjectiveForm: FunctionComponent<Props> = ({
  onSubmit,
  handleClose,
  editing = false,
  initialValues,
}) => {
  // Standard hooks
  const { t } = useFormatter();

  const methods = useForm<ObjectiveFormInputs>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<ObjectiveFormInputs>().with({
        objective_title: z.string().min(1, { message: t('This field is required.') }),
        objective_description: optionalText(),
        objective_priority: requiredInteger(t),
      }),
    ),
    defaultValues: {
      objective_title: '',
      objective_description: '',
      ...initialValues,
    },
  });

  const {
    handleSubmit,
    formState: { isDirty, isSubmitting },
  } = methods;

  const handleSubmitWithoutPropagation = (e: SyntheticEvent) => {
    e.preventDefault();
    e.stopPropagation();
    handleSubmit(onSubmit)(e);
  };

  return (
    <FormProvider {...methods}>
      <form id="objectiveForm" onSubmit={handleSubmitWithoutPropagation}>
        <TextFieldController
          name="objective_title"
          label={t('Title')}
        />
        <TextFieldController
          name="objective_description"
          multiline
          rows={2}
          label={t('Description')}
          style={{ marginTop: 20 }}
        />
        <TextFieldController
          name="objective_priority"
          label={t('Priority')}
          style={{ marginTop: 20 }}
          type="number"
        />
        <div style={{
          float: 'right',
          marginTop: 20,
        }}
        >
          <Button type="button" priority="secondary" onClick={handleClose} disabled={isSubmitting} style={{ marginRight: 10 }}>
            {t('Cancel')}
          </Button>
          <Button type="submit" disabled={!isDirty || isSubmitting}>
            {editing ? t('Update') : t('Create')}
          </Button>
        </div>
      </form>
    </FormProvider>
  );
};

export default ObjectiveForm;
