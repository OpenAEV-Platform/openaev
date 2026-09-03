import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@mui/material';
import { type FunctionComponent, type SyntheticEvent } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import TextFieldController from '../../../components/fields/TextFieldController';
import { useFormatter } from '../../../components/i18n';
import { zodImplement } from '../../../utils/Zod';

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

  // TextFieldController stores its value as a string, even with type="number": coerce it back to a
  // number before validation, an emptied input becoming `undefined` so it fails the required check.
  const requiredNumber = z.preprocess(
    value => (value === '' || value === null ? undefined : value),
    z.coerce.number({ error: () => t('This field is required.') }),
  ) as unknown as z.ZodType<number, number>;

  const methods = useForm<ObjectiveFormInputs>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<ObjectiveFormInputs>().with({
        objective_title: z.string().min(1, { message: t('This field is required.') }),
        objective_description: z.string().optional(),
        objective_priority: requiredNumber,
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
          variant="standard"
          name="objective_title"
          label={t('Title')}
        />
        <TextFieldController
          variant="standard"
          name="objective_description"
          multiline
          rows={2}
          label={t('Description')}
          style={{ marginTop: 20 }}
        />
        <TextFieldController
          variant="standard"
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
          <Button
            variant="outlined"
            color="primary"
            onClick={handleClose}
            style={{ marginRight: 10 }}
            disabled={isSubmitting}
          >
            {t('Cancel')}
          </Button>
          <Button
            variant="contained"
            color="primary"
            type="submit"
            disabled={!isDirty || isSubmitting}
          >
            {editing ? t('Update') : t('Create')}
          </Button>
        </div>
      </form>
    </FormProvider>
  );
};

export default ObjectiveForm;
