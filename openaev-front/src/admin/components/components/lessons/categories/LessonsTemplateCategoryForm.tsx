import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import TextField from '../../../../../components/fields/TextField';
import { useFormatter } from '../../../../../components/i18n';
import { type LessonsTemplateCategoryInput } from '../../../../../utils/api-types';
import { zodImplement } from '../../../../../utils/Zod';

export type LessonsTemplateCategoryInputForm = Omit<LessonsTemplateCategoryInput, 'lessons_template_category_order'> & { lessons_template_category_order: string };

interface Props {
  onSubmit: SubmitHandler<LessonsTemplateCategoryInputForm>;
  handleClose: () => void;
  editing?: boolean;
  initialValues?: LessonsTemplateCategoryInputForm;
}

const LessonsTemplateCategoryForm: FunctionComponent<Props> = ({
  onSubmit,
  handleClose,
  initialValues = {
    lessons_template_category_name: '',
    lessons_template_category_description: '',
    lessons_template_category_order: '0',
  },
  editing = false,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const theme = useTheme();

  const {
    register,
    handleSubmit,
    formState: { errors, isDirty, isSubmitting },
    control,
  } = useForm<LessonsTemplateCategoryInputForm>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<LessonsTemplateCategoryInputForm>().with({
        lessons_template_category_name: z.string().min(1, { message: t('Should not be empty') }),
        lessons_template_category_description: z.string().optional(),
        lessons_template_category_order: z.string().min(1, { message: t('Should not be empty') }),
      }),
    ),
    defaultValues: initialValues,
  });

  return (
    <form noValidate id="lessonTemplateCategoryForm" onSubmit={handleSubmit(onSubmit)}>
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        gap: theme.spacing(2),
      }}
      >
        <TextField
          required
          label={t('Name')}
          error={!!errors.lessons_template_category_name}
          helperText={errors.lessons_template_category_name?.message}
          {...register('lessons_template_category_name')}
          control={control}
        />
        <TextField
          label={t('Description')}
          error={!!errors.lessons_template_category_description}
          helperText={errors.lessons_template_category_description?.message}
          {...register('lessons_template_category_description')}
          control={control}
        />
        <TextField
          required
          label={t('Order')}
          error={!!errors.lessons_template_category_order}
          helperText={errors.lessons_template_category_order?.message}
          {...register('lessons_template_category_order')}
          type="number"
          control={control}
        />
      </div>
      <div style={{
        display: 'flex',
        justifyContent: 'flex-end',
        marginTop: theme.spacing(2.5),
        gap: theme.spacing(1),
      }}
      >
        <Button
          variant="outlined"
          color="primary"
          onClick={handleClose}
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
  );
};

export default LessonsTemplateCategoryForm;
