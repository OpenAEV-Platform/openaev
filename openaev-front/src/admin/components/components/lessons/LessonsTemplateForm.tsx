import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@mui/material';
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
    <form id="lessonTemplateForm" onSubmit={handleSubmit(onSubmit)}>
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        gap: theme.spacing(2),
      }}
      >
        <TextField
          variant="standard"
          fullWidth
          label={t('Name')}
          error={!!errors.lessons_template_name}
          helperText={errors.lessons_template_name?.message}
          inputProps={register('lessons_template_name')}
          InputLabelProps={{ required: true }}
          control={control}
        />
        <TextField
          variant="standard"
          fullWidth
          label={t('Description')}
          error={!!errors.lessons_template_description}
          helperText={errors.lessons_template_description?.message}
          inputProps={register('lessons_template_description')}
          control={control}
        />
      </div>
      <div style={{
        display: 'flex',
        float: 'right',
        margin: theme.spacing(2),
        gap: theme.spacing(1),
      }}
      >
        <Button
          variant="contained"
          onClick={handleClose}
          disabled={isSubmitting}
        >
          {t('Cancel')}
        </Button>
        <Button
          variant="contained"
          color="secondary"
          type="submit"
          disabled={!isDirty || isSubmitting}
        >
          {editing ? t('Update') : t('Create')}
        </Button>
      </div>
    </form>
  );
};

export default LessonsTemplateForm;
