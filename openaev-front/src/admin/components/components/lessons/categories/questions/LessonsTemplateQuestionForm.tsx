import { Button } from '@filigran/design-system';
import { zodResolver } from '@hookform/resolvers/zod';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import TextField from '../../../../../../components/fields/TextField';
import { useFormatter } from '../../../../../../components/i18n';
import { type LessonsTemplateQuestionInput } from '../../../../../../utils/api-types';
import { zodImplement } from '../../../../../../utils/Zod';

export type LessonsTemplateQuestionInputForm = Omit<LessonsTemplateQuestionInput, 'lessons_template_question_order'> & { lessons_template_question_order: string };

interface Props {
  onSubmit: SubmitHandler<LessonsTemplateQuestionInputForm>;
  handleClose: () => void;
  editing?: boolean;
  initialValues?: LessonsTemplateQuestionInputForm;
}

const LessonsTemplateQuestionForm: FunctionComponent<Props> = ({
  onSubmit,
  handleClose,
  initialValues = {
    lessons_template_question_content: '',
    lessons_template_question_explanation: '',
    lessons_template_question_order: '0',
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
  } = useForm<LessonsTemplateQuestionInputForm>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<LessonsTemplateQuestionInputForm>().with({
        lessons_template_question_content: z.string().min(1, { message: t('Should not be empty') }),
        lessons_template_question_explanation: z.string().optional(),
        lessons_template_question_order: z.string().min(1, { message: t('Should not be empty') }),
      }),
    ),
    defaultValues: initialValues,
  });

  return (
    <form noValidate id="lessonsTemplateQuestionForm" onSubmit={handleSubmit(onSubmit)}>
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        gap: theme.spacing(2),
      }}
      >
        <TextField
          required
          label={t('Content')}
          error={!!errors.lessons_template_question_content}
          helperText={errors.lessons_template_question_content?.message}
          {...register('lessons_template_question_content')}
          control={control}
        />
        <TextField
          label={t('Explanation')}
          error={!!errors.lessons_template_question_explanation}
          helperText={errors.lessons_template_question_explanation?.message}
          {...register('lessons_template_question_explanation')}
          control={control}
        />
        <TextField
          required
          label={t('Order')}
          error={!!errors.lessons_template_question_order}
          helperText={errors.lessons_template_question_order?.message}
          {...register('lessons_template_question_order')}
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

export default LessonsTemplateQuestionForm;
