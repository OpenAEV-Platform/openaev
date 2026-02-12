import { useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../components/i18n';
import type { PlatformSettings } from '../../../utils/api-types';
import { isEmptyField } from '../../../utils/utils';
import TopBanner from './TopBanner';

const StartTrialBanner = (settings: { settings: PlatformSettings }) => {
  const { t } = useFormatter();
  const theme = useTheme();

  if (!settings || isEmptyField(settings.settings?.xtm_hub_url) || settings.settings.platform_base_url !== 'https://demo.openaev.io') return <></>;

  // REMOVE WHEN REMOVING FEATURE FLAG OPENAEV_TRIALS_XTMHUB
  const freeTrialsEnabled = settings.settings?.enabled_dev_features?.includes('OPENAEV_TRIALS_XTMHUB') ?? false;
  if (!freeTrialsEnabled) return <></>;

  const freeTrialUrl = `${settings.settings?.xtm_hub_url}/cybersecurity-solutions/openaev-free-trial`;
  const createFreeTrialUrl = `${settings.settings?.xtm_hub_url}/redirect/create-free-trial`;

  const text = (
    <>
      {t('Come and Try OpenAEV with the')}
      <strong>{t(' Free Trial platform!')}</strong>
      <strong>
        <u>
          <a
            href={freeTrialUrl}
            style={{
              color: '#000000',
              marginLeft: theme.spacing(0.5),
            }}
            target="_blank"
            rel="noreferrer"
          >
            {t('Learn more')}
          </a>
        </u>
      </strong>
    </>
  );

  const handleOpenLink = () => {
    window.open(createFreeTrialUrl, '_blank', 'noopener,noreferrer');
  };

  return (
    <TopBanner bannerColor="gradient_blue" bannerText={text} buttonText={t('Start a trial')} onButtonClick={handleOpenLink} />);
};

export default StartTrialBanner;
