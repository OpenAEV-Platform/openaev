import { zodResolver } from '@hookform/resolvers/zod';
import { TextField, type Theme } from '@mui/material';
import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type SxProps } from '@mui/system';
import moment from 'moment/moment';
import type React from 'react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import Dialog from '../../../components/common/dialog/Dialog';
import { useFormatter } from '../../../components/i18n';
import { simplePostCall } from '../../../utils/Action';
import { type License, type PlatformSettings } from '../../../utils/api-types';
import { daysBetweenDates } from '../../../utils/Time';
import { zodImplement } from '../../../utils/Zod';
import TopBanner, { type TopBannerColor } from './TopBanner';

export const LICENSE_OPTION_TRIAL = 'trial';
const TRIAL_YELLOW_DAYS = 8;
const TRIAL_GREEN_DAYS = 22;
interface ContactUsInput { message: string }
interface BannerInfo {
  message: React.ReactNode;
  bannerColor: TopBannerColor;
  buttonText?: string;
  buttonStyle?: SxProps<Theme>;
  onButtonClick?: () => void;
}

const getBannerColor = (remainingDays: number) => {
  if (remainingDays <= TRIAL_YELLOW_DAYS) return 'gradient_yellow';
  if (remainingDays <= TRIAL_GREEN_DAYS) return 'gradient_green';
  return 'gradient_blue';
};

const getButtonColor = (remainingDays: number): string => {
  if (remainingDays <= TRIAL_YELLOW_DAYS) return '#884106';
  if (remainingDays <= TRIAL_GREEN_DAYS) return '#005744';
  return '#007399';
};

const getButtonStyle = (remainingDays: number): SxProps<Theme> => {
  const buttonColor = getButtonColor(remainingDays);

  return {
    color: 'white',
    fontWeight: 'bold',
    backgroundColor: buttonColor,
  };
};
const computeBannerError = (message: string): BannerInfo => {
  return {
    message,
    bannerColor: 'red',
    buttonStyle: getButtonStyle(0),
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
      buttonStyle: getButtonStyle(remainingDays),
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
  const isTrialLicense = eeSettings?.license_type === LICENSE_OPTION_TRIAL;
  if (!isTrialLicense) return null;

  const onSubmit = (values: ContactUsInput) => {
    return simplePostCall(`/api/xtmhub/contact-us`, { message: values.message })
      .then(() => {
        setShowThankYouDialog(true);
        setShowFormDialog(false);
      });
  };

  const bannerInfo = computeBannerInfo(t, eeSettings, () => {
    setShowFormDialog(true);
  });
  if (!bannerInfo) return null;
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
  return (
    <>
      <TopBanner
        bannerText={bannerInfo.message}
        bannerColor={bannerInfo.bannerColor}
        buttonStyle={bannerInfo.buttonStyle}
        buttonText={bannerInfo.buttonText}
        onButtonClick={bannerInfo.onButtonClick}
      />
      <Dialog
        open={showFormDialog}
        title={t('Contact Us')}
        handleClose={() => setShowFormDialog(false)}
      >
        <form id="contactUsForm" onSubmit={handleSubmit(onSubmit)}>
          <TextField
            {...register('message')}
            variant="standard"
            fullWidth
            multiline
            rows={5}
            label={t('Your message')}
          />
          <div style={{
            float: 'right',
            marginTop: theme.spacing(2),
          }}
          >
            <Button
              onClick={() => {
                setShowFormDialog(false);
                reset();
              }}
            >
              {t('Cancel')}
            </Button>
            <Button type="submit" disabled={!isValid} color="secondary">
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
            <Button onClick={() => setShowThankYouDialog(false)} color="primary">
              {t('Close')}
            </Button>
          </div>
        </>
      </Dialog>
    </>
  );
};

export default LicenseBanner;
