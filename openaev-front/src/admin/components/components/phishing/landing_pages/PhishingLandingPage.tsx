import { InfoOutlined } from '@mui/icons-material';
import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { useParams } from 'react-router';

import { type PhishingLandingPagesHelper } from '../../../../../actions/phishing/phishing-helper';
import { DetailSections, Field, InformationGrid, SectionBlock } from '../../../../../components/common/detail/EntityDetailCommon';
import { useFormatter } from '../../../../../components/i18n';
import ItemBoolean from '../../../../../components/ItemBoolean';
import { useHelper } from '../../../../../store';
import { type PhishingLandingPage as PhishingLandingPageType } from '../../../../../utils/api-types';
import { emptyFilled } from '../../../../../utils/String';
import PhishingHtmlPreview from '../PhishingHtmlPreview';

const PhishingLandingPage = () => {
  const theme = useTheme();
  const { landingPageId } = useParams() as { landingPageId: PhishingLandingPageType['phishing_landing_page_id'] };
  const { t } = useFormatter();
  const { landingPage } = useHelper(
    (helper: PhishingLandingPagesHelper) => ({ landingPage: helper.getPhishingLandingPage(landingPageId) as PhishingLandingPageType }),
  );

  const previewDocument = `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><style>html,body{margin:0;padding:0;background:#ffffff;}</style><style>${landingPage.phishing_landing_page_css ?? ''}</style></head><body>${landingPage.phishing_landing_page_html ?? ''}</body></html>`;

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

          <SectionBlock title={t('Usage')}>
            <Box sx={{
              display: 'flex',
              gap: 1.5,
              alignItems: 'flex-start',
            }}
            >
              <Box sx={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                width: 32,
                height: 32,
                borderRadius: 1,
                flexShrink: 0,
                color: 'primary.main',
                backgroundColor: alpha(theme.palette.primary.main, 0.1),
              }}
              >
                <InfoOutlined sx={{ fontSize: 18 }} />
              </Box>
              <Typography
                variant="body2"
                sx={{
                  color: 'text.secondary',
                  lineHeight: 1.55,
                }}
              >
                {t('This landing page is available as a phishing action in the Threat Arsenal. Add it to an inject to launch a phishing campaign targeting teams and players.')}
              </Typography>
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
