import { useFormatter } from '../../../components/i18n';
import type { PlatformSettings } from '../../../utils/api-types';
import { isEmptyField } from '../../../utils/utils';
import TopBanner from './TopBanner';

const StartTrialBanner = (settings: { settings: PlatformSettings }) => {
  const { t } = useFormatter();

  if (!settings || isEmptyField(settings.settings?.xtm_hub_url) || settings.settings.platform_base_url !== 'https://demo.openaev.io') return <></>;

  const freeTrialUrl = `${settings.settings?.xtm_hub_url}/cybersecurity-solutions/free-trial`;
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
              marginLeft: '4px',
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
