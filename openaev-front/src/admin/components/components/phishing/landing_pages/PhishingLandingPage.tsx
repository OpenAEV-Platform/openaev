import { MailOutline, PublicOutlined, SportsEsportsOutlined } from '@mui/icons-material';
import { Box, Typography } from '@mui/material';
import { useParams } from 'react-router';

import { type PhishingLandingPagesHelper } from '../../../../../actions/phishing/phishing-helper';
import { DetailSections, Field, InformationGrid, SectionBlock } from '../../../../../components/common/detail/EntityDetailCommon';
import { useFormatter } from '../../../../../components/i18n';
import ItemBoolean from '../../../../../components/ItemBoolean';
import { useHelper } from '../../../../../store';
import { type PhishingLandingPage as PhishingLandingPageType } from '../../../../../utils/api-types';
import { emptyFilled } from '../../../../../utils/String';
import PhishingHtmlPreview from '../PhishingHtmlPreview';
import PhishingUsageStep from '../PhishingUsageStep';

const PhishingLandingPage = () => {
  const { landingPageId } = useParams() as { landingPageId: PhishingLandingPageType['phishing_landing_page_id'] };
  const { t } = useFormatter();
  const { landingPage } = useHelper(
    (helper: PhishingLandingPagesHelper) => ({ landingPage: helper.getPhishingLandingPage(landingPageId) as PhishingLandingPageType }),
  );

  const previewDocument = `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><style>html,body{margin:0;padding:0;background:#ffffff;}</style><style>${landingPage.phishing_landing_page_css ?? ''}</style></head><body>${landingPage.phishing_landing_page_html ?? ''}</body></html>`;
  const arsenalName = `Phishing: ${landingPage.phishing_landing_page_name || '-'}`;

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 16,
      paddingBottom: 40,
    }}
    >
      <DetailSections columns="minmax(320px, 1fr) minmax(420px, 1.45fr)">
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
        }}
        >
          {/* action={null} keeps the 32px header row so this Paper top-aligns
              with the Preview column (whose header holds the fullscreen
              button), same geometry as Channel Parameters / Live preview. */}
          <InformationGrid title={t('Configuration')} action={null}>
            <Field label={t('Capture submitted data')}>
              <ItemBoolean
                status={landingPage.phishing_landing_page_capture_submitted_data === true}
                label={landingPage.phishing_landing_page_capture_submitted_data ? t('Yes') : t('No')}
                variant="inList"
              />
            </Field>
            <Field label={t('Capture passwords')}>
              <ItemBoolean
                status={landingPage.phishing_landing_page_capture_passwords === true}
                label={landingPage.phishing_landing_page_capture_passwords ? t('Yes') : t('No')}
                variant="inList"
              />
            </Field>
            <Field label={t('Redirect URL after submit')}>
              <Typography
                variant="body2"
                sx={{
                  wordBreak: 'break-all',
                  color: landingPage.phishing_landing_page_redirect_url ? 'text.primary' : 'text.secondary',
                }}
              >
                {emptyFilled(landingPage.phishing_landing_page_redirect_url)}
              </Typography>
            </Field>
          </InformationGrid>

          <SectionBlock title={t('How to use')}>
            <Box sx={{
              display: 'flex',
              flexDirection: 'column',
              gap: 2,
            }}
            >
              <PhishingUsageStep
                icon={<SportsEsportsOutlined sx={{ fontSize: 20 }} />}
                title={t('Add from Threat Arsenal')}
                body={t('This landing page is published as the phishing action "{name}". Pick it when adding an inject to a scenario or simulation.', { name: arsenalName })}
              />
              <PhishingUsageStep
                icon={<MailOutline sx={{ fontSize: 20 }} />}
                title={t('Choose the lure email')}
                body={t('In the inject form, select which email template to send and which teams to target. Recipients who click the lure open this page.')}
              />
              <PhishingUsageStep
                icon={<PublicOutlined sx={{ fontSize: 20 }} />}
                title={t('Capture and redirect')}
                body={t('Submitted form data is tracked per recipient. Optional redirect runs after submit when a URL is configured above.')}
              />
            </Box>
          </SectionBlock>
        </div>

        <PhishingHtmlPreview
          title={t('Preview')}
          iframeTitle={landingPage.phishing_landing_page_name ?? t('Preview')}
          srcDoc={previewDocument}
          height={560}
        />
      </DetailSections>
    </div>
  );
};

export default PhishingLandingPage;
