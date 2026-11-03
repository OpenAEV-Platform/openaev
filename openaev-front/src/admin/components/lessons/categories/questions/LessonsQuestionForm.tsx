import { Button } from '@filigran/design-system';
import { zodResolver } from '@hookform/resolvers/zod';
import { type FunctionComponent, type SyntheticEvent } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import TextFieldController from '../../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../../components/i18n';
import { requiredNumber, zodImplement } from '../../../../../utils/Zod';

// The form requires a content and an order, whereas the order is optional on the API inputs: the
// dedicated form type keeps `handleSubmit` payloads assignable to
// `LessonsQuestionCreateInput` / `LessonsQuestionUpdateInput`.
export interface LessonsQuestionFormInputs {
  lessons_question_content: string;
  lessons_question_explanation?: string;
  lessons_question_order: number;
}

interface Props {
  onSubmit: SubmitHandler<LessonsQuestionFormInputs>;
  handleClose: () => void;
  editing?: boolean;
  initialValues?: Partial<LessonsQuestionFormInputs>;
}

const LessonsQuestionForm: FunctionComponent<Props> = ({
  onSubmit,
  handleClose,
  editing = false,
  initialValues,
}) => {
  const { t } = useFormatter();

  const methods = useForm<LessonsQuestionFormInputs>({
    mode: 'onChange',
    resolver: zodResolver(
      zodImplement<LessonsQuestionFormInputs>().with({
        lessons_question_content: z.string().min(1, { message: t('This field is required.') }),
        lessons_question_explanation: z.string().optional(),
        lessons_question_order: requiredNumber(t('This field is required.')),
      }),
    ),
    defaultValues: {
      lessons_question_content: '',
      lessons_question_explanation: '',
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
    // An empty explanation is left out of the payload so the API stores null, not ''.
    handleSubmit((data, event) => onSubmit({
      ...data,
      lessons_question_explanation: data.lessons_question_explanation || undefined,
    }, event))(e);
  };

  // Rendering
  return (
    <FormProvider {...methods}>
      <form id="lessonsQuestionForm" onSubmit={handleSubmitWithoutPropagation}>
        <TextFieldController
          name="lessons_question_content"
          label={t('Content')}
        />
        <TextFieldController
          name="lessons_question_explanation"
          label={t('Explanation')}
          style={{ marginTop: 20 }}
        />
        <TextFieldController
          name="lessons_question_order"
          label={t('Order')}
          type="number"
          style={{ marginTop: 20 }}
        />
        <div style={{
          float: 'right',
          marginTop: 20,
        }}
        >
          <Button type="button" priority="secondary" onClick={handleClose} disabled={isSubmitting} style={{ marginRight: 10 }}>
            {t('Cancel')}
          </Button>
          <Button type="submit" disabled={isSubmitting || !isValid}>
            {editing ? t('Update') : t('Create')}
          </Button>
        </div>
      </form>
    </FormProvider>
  );
};

export default LessonsQuestionForm;
