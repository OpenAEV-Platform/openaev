import { zodResolver } from '@hookform/resolvers/zod';
import { PublicOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useRef, useState } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';
import { useNavigate, useParams } from 'react-router';
import { z } from 'zod';

import { searchCustomDomains } from '../../../../../actions/custom_domains/customdomain-actions';
import {
  addPhishingLandingPage,
  fetchPhishingLandingPage,
  updatePhishingLandingPage,
} from '../../../../../actions/phishing/phishing-action';
import { type PhishingLandingPagesHelper } from '../../../../../actions/phishing/phishing-helper';
import { SectionBlock } from '../../../../../components/common/detail/EntityDetailCommon';
import { buildSearchPagination } from '../../../../../components/common/queryable/QueryableUtils';
import CodeFieldController from '../../../../../components/fields/CodeFieldController';
import SelectFieldController, { type Item } from '../../../../../components/fields/SelectFieldController';
import SwitchFieldController from '../../../../../components/fields/SwitchFieldController';
import TextFieldController from '../../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import { useHelper } from '../../../../../store';
import { type CustomDomain, type PhishingLandingPage } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { zodImplement } from '../../../../../utils/Zod';
import PhishingAiGenerateButton from '../PhishingAiGenerateButton';
import { parseAgentJson } from '../phishingAiJson';
import PhishingEditorHero from '../PhishingEditorHero';
import PhishingEditorLayout from '../PhishingEditorLayout';
import PhishingGenerationPreview from '../PhishingGenerationPreview';
import PhishingHtmlPreview from '../PhishingHtmlPreview';

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
  phishing_landing_page_custom_domain?: string;
}

const FORM_ID = 'phishingLandingPageEditorForm';

// Sentinel select value for "no custom domain" (serve on the platform domain). The Select component
// cannot use an empty string as a meaningful option value, so map to/from null at the boundary.
const PLATFORM_DOMAIN_VALUE = '';

const emptyValues: PhishingLandingPageFormInput = {
  phishing_landing_page_name: '',
  phishing_landing_page_description: '',
  phishing_landing_page_html: '',
  phishing_landing_page_css: '',
  phishing_landing_page_capture_submitted_data: true,
  phishing_landing_page_capture_passwords: true,
  phishing_landing_page_redirect_url: '',
  phishing_landing_page_primary_color_dark: '',
  phishing_landing_page_primary_color_light: '',
  phishing_landing_page_custom_domain: PLATFORM_DOMAIN_VALUE,
};

const toFormInput = (landingPage: PhishingLandingPage): PhishingLandingPageFormInput => ({
  phishing_landing_page_name: landingPage.phishing_landing_page_name ?? '',
  phishing_landing_page_description: landingPage.phishing_landing_page_description ?? '',
  phishing_landing_page_html: landingPage.phishing_landing_page_html ?? '',
  phishing_landing_page_css: landingPage.phishing_landing_page_css ?? '',
  phishing_landing_page_capture_submitted_data: landingPage.phishing_landing_page_capture_submitted_data ?? true,
  phishing_landing_page_capture_passwords: landingPage.phishing_landing_page_capture_passwords ?? true,
  phishing_landing_page_redirect_url: landingPage.phishing_landing_page_redirect_url ?? '',
  phishing_landing_page_primary_color_dark: landingPage.phishing_landing_page_primary_color_dark ?? '',
  phishing_landing_page_primary_color_light: landingPage.phishing_landing_page_primary_color_light ?? '',
  phishing_landing_page_custom_domain: landingPage.phishing_landing_page_custom_domain ?? PLATFORM_DOMAIN_VALUE,
});

const previewSrcDoc = (html: string, css: string) =>
  `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><style>html,body{margin:0;padding:0;background:#ffffff;}</style><style>${css}</style></head><body>${html}</body></html>`;

const PhishingLandingPageEditor: FunctionComponent = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { landingPageId } = useParams() as { landingPageId?: string };
  const editing = Boolean(landingPageId);

  // Only verified domains can host a page (the backend rejects unverified ones), so the picker only
  // offers those plus the platform default.
  const [domainItems, setDomainItems] = useState<Item[]>([]);
  useEffect(() => {
    let active = true;
    searchCustomDomains(buildSearchPagination({ size: 100 })).then(
      (result: { data: { content: CustomDomain[] } }) => {
        if (!active) return;
        const verified = (result?.data?.content ?? []).filter(
          d => d.custom_domain_status === 'VERIFIED',
        );
        setDomainItems([
          {
            value: PLATFORM_DOMAIN_VALUE,
            label: t('Platform default domain'),
          },
          ...verified.map(d => ({
            value: d.custom_domain_id,
            label: d.custom_domain_hostname,
          })),
        ]);
      },
    );
    return () => {
      active = false;
    };
  }, []);

  const { landingPage } = useHelper((helper: PhishingLandingPagesHelper) => ({ landingPage: landingPageId ? helper.getPhishingLandingPage(landingPageId) : undefined }));
  useDataLoader(() => {
    if (landingPageId) {
      dispatch(fetchPhishingLandingPage(landingPageId));
    }
  }, [landingPageId]);

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
        phishing_landing_page_custom_domain: z.string().optional(),
      }),
    ),
    defaultValues: emptyValues,
  });
  const { handleSubmit, setValue, watch, reset, formState: { isDirty, isSubmitting } } = methods;

  // Hydrate the form once the entity for edit mode arrives (fetch is async).
  const hydratedRef = useRef(false);
  useEffect(() => {
    if (editing && landingPage && !hydratedRef.current) {
      reset(toFormInput(landingPage));
      hydratedRef.current = true;
    }
  }, [editing, landingPage, reset]);

  const backToList = '/admin/components/phishing/landing_pages';
  const cancelTarget = editing && landingPageId ? `${backToList}/${landingPageId}` : backToList;

  const onSubmit: SubmitHandler<PhishingLandingPageFormInput> = async (data) => {
    if (editing && landingPageId) {
      const result = await dispatch(updatePhishingLandingPage(landingPageId, data));
      if (result.result) {
        navigate(`${backToList}/${result.result}`);
      }
      return;
    }
    const result = await dispatch(addPhishingLandingPage(data));
    if (result.result) {
      navigate(`${backToList}/${result.result}`);
    }
  };

  const suggestions = [
    {
      label: t('Microsoft 365 sign-in'),
      instruction: 'Emulate a Microsoft 365 / Office 365 sign-in page with the Microsoft logo and layout.',
    },
    {
      label: t('Google Workspace login'),
      instruction: 'Emulate a Google Workspace / Gmail login page with the Google logo and layout.',
    },
    {
      label: t('Corporate VPN portal'),
      instruction: 'Emulate a corporate VPN / SSO portal login page with a generic enterprise look.',
    },
    {
      label: t('Minimal and modern'),
      instruction: 'Use a clean, minimal and modern visual style with plenty of whitespace.',
    },
    {
      label: t('Match a specific brand'),
      instruction: 'Match the visual identity (colors, logo, typography) of the following brand: ',
    },
  ];

  // Editing but the entity is not loaded yet: wait so the form hydrates cleanly.
  if (editing && !landingPage) {
    return <Loader />;
  }

  const title = editing
    ? (landingPage?.phishing_landing_page_name || t('Edit landing page'))
    : t('New landing page');

  const header = (
    <PhishingEditorHero
      icon={<PublicOutlined />}
      overline={t('Phishing landing page')}
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
          <SectionBlock title={t('Details')}>
            <div style={{
              display: 'flex',
              flexDirection: 'column',
              gap: theme.spacing(2),
            }}
            >
              <TextFieldController variant="standard" name="phishing_landing_page_name" label={t('Name')} required />
              <TextFieldController variant="standard" name="phishing_landing_page_description" label={t('Description')} />
              <TextFieldController variant="standard" name="phishing_landing_page_redirect_url" label={t('Redirect URL after submit')} />
              <SelectFieldController
                name="phishing_landing_page_custom_domain"
                label={t('Serve on domain')}
                items={domainItems}
              />
              <SwitchFieldController name="phishing_landing_page_capture_submitted_data" label={t('Capture submitted data')} />
              <SwitchFieldController name="phishing_landing_page_capture_passwords" label={t('Capture passwords')} />
            </div>
          </SectionBlock>

          <SectionBlock
            title={t('Landing page content')}
            action={(
              <PhishingAiGenerateButton
                intent="aev.phishing_landing_page_html_generator"
                currentValue={watch('phishing_landing_page_html')}
                suggestions={suggestions}
                renderResult={({ raw, loading }) => (
                  <PhishingGenerationPreview raw={raw} loading={loading} variant="landing" />
                )}
                buildPrompt={() => 'You are assisting with an authorized, educational security awareness phishing exercise. Generate a realistic phishing landing page. Return ONLY a JSON object with keys "html" (string with valid HTML body content) and "css" (string with a CSS stylesheet). Do not include any explanation or markdown fences, only the JSON object.'}
                onAccept={(raw) => {
                  const parsed = parseAgentJson(raw);
                  if (!parsed) {
                    setValue('phishing_landing_page_html', raw, { shouldDirty: true });
                    return;
                  }
                  if (typeof parsed.html === 'string') {
                    setValue('phishing_landing_page_html', parsed.html, { shouldDirty: true });
                  }
                  if (typeof parsed.css === 'string') {
                    setValue('phishing_landing_page_css', parsed.css, { shouldDirty: true });
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
                name="phishing_landing_page_html"
                language="html"
                label={t('HTML content')}
                placeholder={t('Write HTML or generate it with AI')}
                minHeight={260}
              />
              <CodeFieldController
                name="phishing_landing_page_css"
                language="css"
                label={t('CSS content')}
                placeholder={t('Write CSS or generate it with AI')}
                minHeight={180}
              />
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
      srcDoc={previewSrcDoc(watch('phishing_landing_page_html') ?? '', watch('phishing_landing_page_css') ?? '')}
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

export default PhishingLandingPageEditor;
