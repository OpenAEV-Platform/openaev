import { PublicOutlined } from '@mui/icons-material';
import { Chip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { useParams } from 'react-router';

import { type PhishingLandingPagesHelper } from '../../../../../actions/phishing/phishing-helper';
import { DetailHero } from '../../../../../components/common/detail/EntityDetailCommon';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type PhishingLandingPage } from '../../../../../utils/api-types';
import PhishingLandingPagePopover from './PhishingLandingPagePopover';

const PhishingLandingPageHeader = () => {
  const theme = useTheme();
  const { landingPageId } = useParams() as { landingPageId: PhishingLandingPage['phishing_landing_page_id'] };
  const { t } = useFormatter();
  const { landingPage } = useHelper(
    (helper: PhishingLandingPagesHelper) => ({ landingPage: helper.getPhishingLandingPage(landingPageId) as PhishingLandingPage }),
  );

  const captureOn = landingPage.phishing_landing_page_capture_submitted_data === true;
  const passwordsOn = landingPage.phishing_landing_page_capture_passwords === true;
  const chipSx = (on: boolean) => ({
    height: 22,
    fontSize: 11,
    borderRadius: 1,
    color: on ? theme.palette.success.main : theme.palette.text.secondary,
    borderColor: alpha(on ? theme.palette.success.main : theme.palette.text.secondary, 0.45),
    backgroundColor: alpha(on ? theme.palette.success.main : theme.palette.text.secondary, 0.08),
  });

  return (
    <DetailHero
      iconNode={<PublicOutlined />}
      overline={t('Phishing landing page')}
      title={landingPage.phishing_landing_page_name ?? '-'}
      chips={(
        <>
          <Chip
            size="small"
            variant="outlined"
            label={captureOn ? t('Capture submitted data') : t('No data capture')}
            sx={chipSx(captureOn)}
          />
          <Chip
            size="small"
            variant="outlined"
            label={passwordsOn ? t('Capture passwords') : t('No password capture')}
            sx={chipSx(passwordsOn)}
          />
        </>
      )}
      action={<PhishingLandingPagePopover landingPage={landingPage} />}
      footer={landingPage.phishing_landing_page_description
        ? (
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
              {landingPage.phishing_landing_page_description}
            </Typography>
          )
        : undefined}
    />
  );
};

export default PhishingLandingPageHeader;
