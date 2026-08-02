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

export interface PhishingLandingPageFormInput {
  phishing_landing_page_name: string;
  phishing_landing_page_description?: string;
  phishing_landing_page_html?: string;
  phishing_landing_page_css?: string;
  phishing_landing_page_capture_submitted_data: boolean;
  phishing_landing_page_capture_passwords: boolean;
  phishing_landing_page_redirect_url?: string;
  phishing_landing_page_primary_color_dark?: string;
  phishing_landing_page_primary_color_light?: string;
}

interface Props {
  onSubmit: SubmitHandler<PhishingLandingPageFormInput>;
  handleClose: () => void;
  editing?: boolean;
  initialValues?: PhishingLandingPageFormInput;
}

const defaultInitialValues: PhishingLandingPageFormInput = {
  phishing_landing_page_name: '',
  phishing_landing_page_description: '',
  phishing_landing_page_html: '',
  phishing_landing_page_css: '',
  phishing_landing_page_capture_submitted_data: true,
  phishing_landing_page_capture_passwords: true,
  phishing_landing_page_redirect_url: '',
  phishing_landing_page_primary_color_dark: '',
  phishing_landing_page_primary_color_light: '',
};

const PhishingLandingPageForm: FunctionComponent<Props> = ({
  onSubmit,
  handleClose,
  initialValues = defaultInitialValues,
  editing = false,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const methods = useForm<PhishingLandingPageFormInput>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<PhishingLandingPageFormInput>().with({
        phishing_landing_page_name: z.string().min(1, { message: t('Should not be empty') }),
        phishing_landing_page_description: z.string().optional(),
        phishing_landing_page_html: z.string().optional(),
        phishing_landing_page_css: z.string().optional(),
        phishing_landing_page_capture_submitted_data: z.boolean(),
        phishing_landing_page_capture_passwords: z.boolean(),
        phishing_landing_page_redirect_url: z.string().optional(),
        phishing_landing_page_primary_color_dark: z.string().optional(),
        phishing_landing_page_primary_color_light: z.string().optional(),
      }),
    ),
    defaultValues: initialValues,
  });
  const { handleSubmit, formState: { isDirty, isSubmitting } } = methods;

  return (
    <FormProvider {...methods}>
      <form id="phishingLandingPageForm" onSubmit={handleSubmit(onSubmit)}>
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(2),
        }}
        >
          <TextFieldController variant="standard" name="phishing_landing_page_name" label={t('Name')} required />
          <TextFieldController variant="standard" name="phishing_landing_page_description" label={t('Description')} />
          <TextFieldController variant="standard" name="phishing_landing_page_html" label={t('HTML content')} multiline rows={10} />
          <TextFieldController variant="standard" name="phishing_landing_page_css" label={t('CSS content')} multiline rows={6} />
          <TextFieldController variant="standard" name="phishing_landing_page_redirect_url" label={t('Redirect URL after submit')} />
          <SwitchFieldController name="phishing_landing_page_capture_submitted_data" label={t('Capture submitted data')} />
          <SwitchFieldController name="phishing_landing_page_capture_passwords" label={t('Capture passwords')} />
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

export default PhishingLandingPageForm;
