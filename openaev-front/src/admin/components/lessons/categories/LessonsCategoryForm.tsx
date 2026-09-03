import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@mui/material';
import { type FunctionComponent, type SyntheticEvent } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import TextFieldController from '../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../components/i18n';
import { zodImplement } from '../../../../utils/Zod';

// The form requires a name and an order, whereas the order is optional on the API inputs: the
// dedicated form type keeps `handleSubmit` payloads assignable to
// `LessonsCategoryCreateInput` / `LessonsCategoryUpdateInput`.
export interface LessonsCategoryFormInputs {
  lessons_category_name: string;
  lessons_category_description?: string;
  lessons_category_order: number;
}

interface Props {
  onSubmit: SubmitHandler<LessonsCategoryFormInputs>;
  handleClose: () => void;
  editing?: boolean;
  initialValues?: Partial<LessonsCategoryFormInputs>;
}

const LessonsCategoryForm: FunctionComponent<Props> = ({
  onSubmit,
  handleClose,
  editing = false,
  initialValues,
}) => {
  const { t } = useFormatter();

  // TextFieldController stores its value as a string, even with type="number": coerce it back to a
  // number before validation, an emptied input becoming `undefined` so it fails the required check.
  const requiredNumber = z.preprocess(
    value => (value === '' || value === null ? undefined : value),
    z.coerce.number({ error: () => t('This field is required.') }),
  ) as unknown as z.ZodType<number, number>;

  const methods = useForm<LessonsCategoryFormInputs>({
    mode: 'onChange',
    resolver: zodResolver(
      zodImplement<LessonsCategoryFormInputs>().with({
        lessons_category_name: z.string().min(1, { message: t('This field is required.') }),
        lessons_category_description: z.string().optional(),
        lessons_category_order: requiredNumber,
      }),
    ),
    defaultValues: {
      lessons_category_name: '',
      lessons_category_description: '',
      ...initialValues,
    },
  });

  const {
    handleSubmit,
    formState: { isSubmitting, isValid },
  } = methods;

  const handleSubmitWithoutPropagation = (e: SyntheticEvent) => {
    e.preventDefault();
    e.stopPropagation();
    handleSubmit(onSubmit)(e);
  };

  // Rendering
  return (
    <FormProvider {...methods}>
      <form id="lessonsCategoryForm" onSubmit={handleSubmitWithoutPropagation}>
        <TextFieldController
          variant="standard"
          name="lessons_category_name"
          label={t('Name')}
        />
        <TextFieldController
          variant="standard"
          name="lessons_category_description"
          label={t('Description')}
          style={{ marginTop: 20 }}
        />
        <TextFieldController
          variant="standard"
          name="lessons_category_order"
          label={t('Order')}
          type="number"
          style={{ marginTop: 20 }}
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
            disabled={isSubmitting || !isValid}
          >
            {editing ? t('Update') : t('Create')}
          </Button>
        </div>
      </form>
    </FormProvider>
  );
};

export default LessonsCategoryForm;
