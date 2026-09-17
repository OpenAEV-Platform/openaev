import { Button } from '@filigran/design-system';
import { zodResolver } from '@hookform/resolvers/zod';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import TextField from '../../../../components/fields/TextField';
import { useFormatter } from '../../../../components/i18n';
import { type LessonsTemplateInput } from '../../../../utils/api-types';
import { zodImplement } from '../../../../utils/Zod';

interface Props {
  onSubmit: SubmitHandler<LessonsTemplateInput>;
  handleClose: () => void;
  editing?: boolean;
  initialValues?: LessonsTemplateInput;
}

const LessonsTemplateForm: FunctionComponent<Props> = ({
  onSubmit,
  handleClose,
  initialValues = {
    lessons_template_name: '',
    lessons_template_description: '',
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
  } = useForm<LessonsTemplateInput>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<LessonsTemplateInput>().with({
        lessons_template_name: z.string().min(1, { message: t('Should not be empty') }),
        lessons_template_description: z.string().optional(),
      }),
    ),
    defaultValues: initialValues,
  });

  return (
    <form noValidate id="lessonTemplateForm" onSubmit={handleSubmit(onSubmit)}>
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        gap: theme.spacing(2),
      }}
      >
        <TextField
          required
          label={t('Name')}
          error={!!errors.lessons_template_name}
          helperText={errors.lessons_template_name?.message}
          {...register('lessons_template_name')}
          control={control}
        />
        <TextField
          label={t('Description')}
          error={!!errors.lessons_template_description}
          helperText={errors.lessons_template_description?.message}
          {...register('lessons_template_description')}
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
        <Button priority="secondary" onClick={handleClose} disabled={isSubmitting}>
          {t('Cancel')}
        </Button>
        <Button type="submit" disabled={!isDirty || isSubmitting}>
          {editing ? t('Update') : t('Create')}
        </Button>
      </div>
    </form>
  );
};

export default LessonsTemplateForm;
