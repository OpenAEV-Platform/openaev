import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import SwitchFieldController from '../../../../../components/fields/SwitchFieldController';
import TextFieldController from '../../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../../components/i18n';
import { zodImplement } from '../../../../../utils/Zod';

export interface PhishingEmailTemplateFormInput {
  phishing_email_template_name: string;
  phishing_email_template_description?: string;
  phishing_email_template_subject: string;
  phishing_email_template_html_body?: string;
  phishing_email_template_text_body?: string;
  phishing_email_template_from_name?: string;
  phishing_email_template_from_email?: string;
  phishing_email_template_add_tracking_pixel: boolean;
}

interface Props {
  onSubmit: SubmitHandler<PhishingEmailTemplateFormInput>;
  handleClose: () => void;
  editing?: boolean;
  initialValues?: PhishingEmailTemplateFormInput;
}

const defaultInitialValues: PhishingEmailTemplateFormInput = {
  phishing_email_template_name: '',
  phishing_email_template_description: '',
  phishing_email_template_subject: '',
  phishing_email_template_html_body: '',
  phishing_email_template_text_body: '',
  phishing_email_template_from_name: '',
  phishing_email_template_from_email: '',
  phishing_email_template_add_tracking_pixel: true,
};

const PhishingEmailTemplateForm: FunctionComponent<Props> = ({
  onSubmit,
  handleClose,
  initialValues = defaultInitialValues,
  editing = false,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const methods = useForm<PhishingEmailTemplateFormInput>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<PhishingEmailTemplateFormInput>().with({
        phishing_email_template_name: z.string().min(1, { message: t('Should not be empty') }),
        phishing_email_template_description: z.string().optional(),
        phishing_email_template_subject: z.string().min(1, { message: t('Should not be empty') }),
        phishing_email_template_html_body: z.string().optional(),
        phishing_email_template_text_body: z.string().optional(),
        phishing_email_template_from_name: z.string().optional(),
        phishing_email_template_from_email: z.string().optional(),
        phishing_email_template_add_tracking_pixel: z.boolean(),
      }),
    ),
    defaultValues: initialValues,
  });
  const { handleSubmit, formState: { isDirty, isSubmitting } } = methods;

  return (
    <FormProvider {...methods}>
      <form id="phishingEmailTemplateForm" onSubmit={handleSubmit(onSubmit)}>
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(2),
        }}
        >
          <TextFieldController variant="standard" name="phishing_email_template_name" label={t('Name')} required />
          <TextFieldController variant="standard" name="phishing_email_template_description" label={t('Description')} />
          <TextFieldController variant="standard" name="phishing_email_template_subject" label={t('Subject')} required />
          <TextFieldController variant="standard" name="phishing_email_template_from_name" label={t('Sender name override')} />
          <TextFieldController variant="standard" name="phishing_email_template_from_email" label={t('Sender email override')} />
          <TextFieldController variant="standard" name="phishing_email_template_html_body" label={t('HTML body')} multiline rows={10} />
          <TextFieldController variant="standard" name="phishing_email_template_text_body" label={t('Text body')} multiline rows={6} />
          <SwitchFieldController name="phishing_email_template_add_tracking_pixel" label={t('Add tracking pixel')} />
        </div>
        <div style={{
          display: 'flex',
          justifyContent: 'flex-end',
          gap: theme.spacing(1),
          marginTop: theme.spacing(2),
        }}
        >
          <Button variant="outlined" color="primary" onClick={handleClose} disabled={isSubmitting}>
            {t('Cancel')}
          </Button>
          <Button variant="contained" color="primary" type="submit" disabled={!isDirty || isSubmitting}>
            {editing ? t('Update') : t('Create')}
          </Button>
        </div>
      </form>
    </FormProvider>
  );
};

export default PhishingEmailTemplateForm;
