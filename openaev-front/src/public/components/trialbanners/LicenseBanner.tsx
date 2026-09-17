import { Button } from '@filigran/design-system';
import { zodResolver } from '@hookform/resolvers/zod';
import { useTheme } from '@mui/material/styles';
import moment from 'moment/moment';
import type React from 'react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import Dialog from '../../../components/common/dialog/Dialog';
import TextFieldFds from '../../../components/fields/TextFieldFds';
import { useFormatter } from '../../../components/i18n';
import { simplePostCall } from '../../../utils/Action';
import { type License, type PlatformSettings } from '../../../utils/api-types';
import { daysBetweenDates } from '../../../utils/Time';
import { zodImplement } from '../../../utils/Zod';
import { LICENSE_OPTION_TRIAL } from './constants';
import TopBanner, { type BannerButtonColor, type TopBannerColor } from './TopBanner';

const TRIAL_YELLOW_DAYS = 8;
const TRIAL_GREEN_DAYS = 22;
interface ContactUsInput { message: string }
interface BannerInfo {
  message: React.ReactNode;
  bannerColor: TopBannerColor;
  buttonText?: string;
  buttonColor?: BannerButtonColor;
  onButtonClick?: () => void;
}

const getBannerColor = (remainingDays: number) => {
  if (remainingDays <= TRIAL_YELLOW_DAYS) return 'gradient_yellow';
  if (remainingDays <= TRIAL_GREEN_DAYS) return 'gradient_green';
  return 'gradient_blue';
};

// The action button is filled with the band's own urgency colour, through the
// library's exceptional `color` override (its RFC §9.1). The three tokens carry
// the hexes the banner used, except the blue: #007399 has no primitive, and
// `blue-700` (#0079a8) is the nearest step.
const getButtonColor = (remainingDays: number): BannerButtonColor => {
  if (remainingDays <= TRIAL_YELLOW_DAYS) return 'orange-700';
  if (remainingDays <= TRIAL_GREEN_DAYS) return 'turquoise-800';
  return 'blue-700';
};
const computeBannerError = (message: string): BannerInfo => {
  return {
    message,
    bannerColor: 'red',
    buttonColor: getButtonColor(0),
  };
};

const computeBannerInfo = (t: (text: string) => string, eeSettings: License, onButtonClick?: () => void): BannerInfo | undefined => {
  if (!eeSettings.license_is_validated) {
    return computeBannerError(`The current ${eeSettings.license_type} license has expired, Enterprise Edition is disabled.`);
  }
  if (eeSettings.license_is_extra_expiration) {
    return computeBannerError(`The current ${eeSettings.license_type} license has expired, Enterprise Edition will be disabled in ${eeSettings.license_extra_expiration_days} days.`);
  }
  if (eeSettings.license_type === LICENSE_OPTION_TRIAL) {
    const remainingDays = daysBetweenDates(moment(), moment(eeSettings.license_expiration_date));
    const bannerColor = getBannerColor(remainingDays);
    return {
      buttonText: t('Reach out to sales'),
      bannerColor,
      message: (
        <>
          {t('Your OpenAEV Enterprise Edition free trial is active: ')}
          <strong>
            {remainingDays}
            {' '}
            {remainingDays === 1 ? t('Day remaining') : t('Days remaining')}
          </strong>
        </>
      ),
      onButtonClick,
    };
  }
  return undefined;
};

const LicenseBanner = (settings: { settings: PlatformSettings }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const [showThankYouDialog, setShowThankYouDialog] = useState(false);
  const [showFormDialog, setShowFormDialog] = useState(false);
  const eeSettings = settings.settings?.platform_license;

  const onSubmit = (values: ContactUsInput) => {
    return simplePostCall(`/api/xtmhub/contact-us`, { message: values.message })
      .then(() => {
        setShowThankYouDialog(true);
        setShowFormDialog(false);
      });
  };

  const {
    register,
    handleSubmit,
    reset,
    formState: { isValid },
  } = useForm<ContactUsInput>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<ContactUsInput>().with({ message: z.string().min(1, { message: t('Should not be empty') }) }),
    ),
    defaultValues: { message: '' },
  });

  const isEE = eeSettings?.license_is_enterprise;
  if (!isEE) return null;

  const bannerInfo = computeBannerInfo(t, eeSettings, () => {
    setShowFormDialog(true);
  });
  if (!bannerInfo) return null;
  return (
    <>
      <TopBanner
        bannerText={bannerInfo.message}
        bannerColor={bannerInfo.bannerColor}
        buttonText={bannerInfo.buttonText}
        buttonColor={bannerInfo.buttonColor}
        onButtonClick={bannerInfo.onButtonClick}
      />
      <Dialog
        open={showFormDialog}
        title={t('Contact Us')}
        handleClose={() => setShowFormDialog(false)}
      >
        <form id="contactUsForm" onSubmit={handleSubmit(onSubmit)}>
          <TextFieldFds
            {...register('message')}
            multiline
            rows={5}
            label={t('Your message')}
          />
          <div style={{
            display: 'flex',
            justifyContent: 'flex-end',
            gap: theme.spacing(1),
            marginTop: theme.spacing(2),
          }}
          >
            <Button
              type="button"
              priority="secondary"
              onClick={() => {
                setShowFormDialog(false);
                reset();
              }}
            >
              {t('Cancel')}
            </Button>
            <Button type="submit" disabled={!isValid}>
              {t('Validate')}
            </Button>
          </div>
        </form>
      </Dialog>
      <Dialog title={t('Thank you!')} open={showThankYouDialog} handleClose={() => setShowThankYouDialog(false)}>
        <>
          {t('Thank you for reaching out, we\'ll get back to you shortly.')}
          <div style={{
            float: 'right',
            marginTop: theme.spacing(2),
          }}
          >
            <Button type="button" priority="tertiary" onClick={() => setShowThankYouDialog(false)}>
              {t('Close')}
            </Button>
          </div>
        </>
      </Dialog>
    </>
  );
};

export default LicenseBanner;
