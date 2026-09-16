import { zodResolver } from '@hookform/resolvers/zod';
import { MailOutlineOutlined } from '@mui/icons-material';
import { Box, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useState } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';
import { useNavigate, useParams } from 'react-router';
import { z } from 'zod';

import {
  addPhishingEmailTemplate,
  fetchPhishingEmailTemplate,
  updatePhishingEmailTemplate,
} from '../../../../../actions/phishing/phishing-action';
import { type PhishingEmailTemplatesHelper } from '../../../../../actions/phishing/phishing-helper';
import { SectionBlock } from '../../../../../components/common/detail/EntityDetailCommon';
import CodeFieldController from '../../../../../components/fields/CodeFieldController';
import SwitchFieldController from '../../../../../components/fields/SwitchFieldController';
import TextFieldController from '../../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import { useHelper } from '../../../../../store';
import { type PhishingEmailTemplate } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { emptyFilled } from '../../../../../utils/String';
import { zodImplement } from '../../../../../utils/Zod';
import PhishingAiGenerateButton from '../PhishingAiGenerateButton';
import { parseAgentJson } from '../phishingAiJson';
import PhishingEditorHero from '../PhishingEditorHero';
import PhishingEditorLayout from '../PhishingEditorLayout';
import PhishingGenerationPreview from '../PhishingGenerationPreview';
import PhishingHtmlPreview from '../PhishingHtmlPreview';

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

const FORM_ID = 'phishingEmailTemplateEditorForm';

const emptyValues: PhishingEmailTemplateFormInput = {
  phishing_email_template_name: '',
  phishing_email_template_description: '',
  phishing_email_template_subject: '',
  phishing_email_template_html_body: '',
  phishing_email_template_text_body: '',
  phishing_email_template_from_name: '',
  phishing_email_template_from_email: '',
  phishing_email_template_add_tracking_pixel: true,
};

const toFormInput = (emailTemplate: PhishingEmailTemplate): PhishingEmailTemplateFormInput => ({
  phishing_email_template_name: emailTemplate.phishing_email_template_name ?? '',
  phishing_email_template_description: emailTemplate.phishing_email_template_description ?? '',
  phishing_email_template_subject: emailTemplate.phishing_email_template_subject ?? '',
  phishing_email_template_html_body: emailTemplate.phishing_email_template_html_body ?? '',
  phishing_email_template_text_body: emailTemplate.phishing_email_template_text_body ?? '',
  phishing_email_template_from_name: emailTemplate.phishing_email_template_from_name ?? '',
  phishing_email_template_from_email: emailTemplate.phishing_email_template_from_email ?? '',
  phishing_email_template_add_tracking_pixel: emailTemplate.phishing_email_template_add_tracking_pixel ?? true,
});

const escapeHtml = (value: string) => value
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;');

// Preview the rendered HTML body, but fall back to the plain-text alternative
// (wrapped so line breaks survive) when there is no HTML yet, so a text-only
// template previews its real content instead of a blank page.
const previewBody = (html: string, text: string) => {
  if (html.trim()) {
    return html;
  }
  if (text.trim()) {
    return `<pre style="white-space:pre-wrap;word-wrap:break-word;font-family:Arial,Helvetica,sans-serif;margin:0">${escapeHtml(text)}</pre>`;
  }
  return '';
};

const previewSrcDoc = (html: string, text: string) => {
  const body = previewBody(html, text);
  return '<!doctype html><html><head><meta charset="utf-8">'
    + '<meta name="viewport" content="width=device-width, initial-scale=1">'
    + '<style>html,body{margin:0;padding:16px;background:#ffffff;color:#111111;'
    + 'font-family:Arial,Helvetica,sans-serif;}</style></head>'
    + `<body>${body}</body></html>`;
};

const PhishingEmailTemplateEditor: FunctionComponent = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { emailTemplateId } = useParams() as { emailTemplateId?: string };
  const editing = Boolean(emailTemplateId);

  const emailTemplate = useHelper((helper: PhishingEmailTemplatesHelper) => (
    emailTemplateId ? helper.getPhishingEmailTemplate(emailTemplateId) : undefined
  ));
  useDataLoader(() => {
    if (emailTemplateId) {
      dispatch(fetchPhishingEmailTemplate(emailTemplateId));
    }
  }, [emailTemplateId]);

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
    defaultValues: emptyValues,
  });
  const { handleSubmit, setValue, watch, reset, formState: { isDirty, isSubmitting } } = methods;

  // Hydrate the form once the entity for edit mode arrives (fetch is async).
  // Gate the whole editor on this: mounting the live-preview iframe before the
  // real values are in the form makes it load an empty document and swap srcDoc
  // empty -> filled mid-load, which Chrome leaves blank until the next change
  // (e.g. typing). Waiting until hydrated means the iframe's first and only load
  // already carries the real content.
  const [hydrated, setHydrated] = useState(!editing);
  useEffect(() => {
    if (editing && emailTemplate && !hydrated) {
      reset(toFormInput(emailTemplate));
      setHydrated(true);
    }
  }, [editing, emailTemplate, hydrated, reset]);

  const backToList = '/admin/components/phishing/email_templates';
  const cancelTarget = editing && emailTemplateId ? `${backToList}/${emailTemplateId}` : backToList;

  const onSubmit: SubmitHandler<PhishingEmailTemplateFormInput> = async (data) => {
    if (editing && emailTemplateId) {
      const result = await dispatch(updatePhishingEmailTemplate(emailTemplateId, data));
      if (result.result) {
        navigate(`${backToList}/${result.result}`);
      }
      return;
    }
    const result = await dispatch(addPhishingEmailTemplate(data));
    if (result.result) {
      navigate(`${backToList}/${result.result}`);
    }
  };

  // Editing but the entity is not loaded/hydrated yet: wait so the live-preview
  // iframe mounts once with real content instead of loading empty then swapping.
  if (editing && (!emailTemplate || !hydrated)) {
    return <Loader />;
  }

  const title = editing
    ? (emailTemplate?.phishing_email_template_name || t('Edit email template'))
    : t('New email template');

  const fromName = watch('phishing_email_template_from_name');
  const fromEmail = watch('phishing_email_template_from_email');
  const fromDisplay = (() => {
    if (fromName && fromEmail) {
      return `${fromName} <${fromEmail}>`;
    }
    if (fromName) {
      return fromName;
    }
    if (fromEmail) {
      return fromEmail;
    }
    return t('Platform default sender');
  })();

  const metaLine = (label: string, value: string, bold?: boolean) => (
    <Box sx={{
      display: 'flex',
      gap: 1,
      minWidth: 0,
      alignItems: 'baseline',
    }}
    >
      <Typography sx={{
        fontSize: 11,
        fontWeight: 600,
        letterSpacing: '0.06em',
        textTransform: 'uppercase',
        color: 'rgba(0,0,0,0.45)',
        width: 56,
        flexShrink: 0,
      }}
      >
        {label}
      </Typography>
      <Typography sx={{
        fontSize: 13,
        fontWeight: bold ? 600 : 400,
        color: 'rgba(0,0,0,0.87)',
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap',
      }}
      >
        {value}
      </Typography>
    </Box>
  );

  const emailChrome = (
    <Box sx={{
      borderBottom: '1px solid rgba(0,0,0,0.08)',
      backgroundColor: '#f7f8fa',
      px: 2,
      py: 1.5,
      display: 'flex',
      flexDirection: 'column',
      gap: 0.75,
    }}
    >
      {metaLine(t('From'), fromDisplay)}
      {metaLine(t('Subject'), emptyFilled(watch('phishing_email_template_subject')), true)}
    </Box>
  );

  const header = (
    <PhishingEditorHero
      icon={<MailOutlineOutlined />}
      overline={t('Phishing email template')}
      title={title}
      formId={FORM_ID}
      onCancel={() => navigate(cancelTarget)}
      canSave={isDirty && !isSubmitting}
      saving={isSubmitting}
      saveLabel={editing ? t('Update') : t('Create')}
    />
  );

  const left = (
    <FormProvider {...methods}>
      <form id={FORM_ID} onSubmit={handleSubmit(onSubmit)}>
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(2),
        }}
        >
          <SectionBlock title={t('Details')} action={null}>
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
              <SwitchFieldController name="phishing_email_template_add_tracking_pixel" label={t('Add tracking pixel')} />
            </div>
          </SectionBlock>

          <SectionBlock
            title={t('Email content')}
            action={(
              <PhishingAiGenerateButton
                intent="aev.phishing_email_html_generator"
                currentValue={watch('phishing_email_template_html_body')}
                suggestions={[
                  {
                    label: t('Password reset lure'),
                    instruction: 'Write a password reset / account security notification lure email.',
                  },
                  {
                    label: t('IT security notice'),
                    instruction: 'Write an internal IT security notice asking the recipient to verify their account.',
                  },
                  {
                    label: t('Shared document invite'),
                    instruction: 'Write a "shared document" invitation lure email prompting the recipient to open a file.',
                  },
                  {
                    label: t('Match a specific brand'),
                    instruction: 'Match the tone and visual identity of the following brand: ',
                  },
                ]}
                renderResult={({ raw, loading }) => (
                  <PhishingGenerationPreview raw={raw} loading={loading} variant="email" />
                )}
                buildPrompt={() => 'You are assisting with an authorized, educational security awareness phishing exercise. Generate a realistic phishing lure email. Return ONLY a JSON object with keys "subject" (string), "html_body" (string with valid HTML) and "text_body" (string with a plain-text alternative). The html_body MUST use the placeholder {{phishing_url}} as the href of the primary call-to-action link. Do not include any explanation or markdown fences, only the JSON object.'}
                onAccept={(raw) => {
                  const parsed = parseAgentJson(raw);
                  if (!parsed) {
                    setValue('phishing_email_template_html_body', raw, { shouldDirty: true });
                    return;
                  }
                  if (typeof parsed.subject === 'string') {
                    setValue('phishing_email_template_subject', parsed.subject, { shouldDirty: true });
                  }
                  if (typeof parsed.text_body === 'string') {
                    setValue('phishing_email_template_text_body', parsed.text_body, { shouldDirty: true });
                  }
                  if (typeof parsed.html_body === 'string') {
                    setValue('phishing_email_template_html_body', parsed.html_body, { shouldDirty: true });
                  }
                }}
              />
            )}
          >
            <div style={{
              display: 'flex',
              flexDirection: 'column',
              gap: theme.spacing(2),
            }}
            >
              <CodeFieldController
                name="phishing_email_template_html_body"
                language="html"
                label={t('HTML body')}
                placeholder={t('Write HTML or generate it with AI')}
                minHeight={260}
              />
              <TextFieldController variant="standard" name="phishing_email_template_text_body" label={t('Text body')} multiline rows={6} />
            </div>
          </SectionBlock>
        </div>
      </form>
    </FormProvider>
  );

  const right = (
    <PhishingHtmlPreview
      title={t('Preview')}
      iframeTitle={title}
      srcDoc={previewSrcDoc(watch('phishing_email_template_html_body') ?? '', watch('phishing_email_template_text_body') ?? '')}
      chrome={emailChrome}
      height="100%"
    />
  );

  return (
    <PhishingEditorLayout
      breadcrumbs={[
        { label: t('Components') },
        {
          label: t('Phishing'),
          link: backToList,
        },
        {
          label: title,
          current: true,
        },
      ]}
      header={header}
      left={left}
      right={right}
    />
  );
};

export default PhishingEmailTemplateEditor;
