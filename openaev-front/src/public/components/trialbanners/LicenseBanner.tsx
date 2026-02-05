import { zodResolver } from '@hookform/resolvers/zod';
import { TextField, type Theme } from '@mui/material';
import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';
import { type SxProps } from '@mui/system';
import moment from 'moment/moment';
import type React from 'react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { useFormatter } from '../../../components/i18n';
import { simplePostCall } from '../../../utils/Action';
import { type License, type PlatformSettings } from '../../../utils/api-types';
import { daysBetweenDates } from '../../../utils/Time';
import { zodImplement } from '../../../utils/Zod';
import TopBanner, { type TopBannerColor } from './TopBanner';

export const LICENSE_OPTION_TRIAL = 'trial';

interface ContactUsInput { message: string }
interface BannerInfo {
  message: React.ReactNode;
  bannerColor: TopBannerColor;
  buttonText?: string;
  buttonStyle?: SxProps<Theme>;
  onButtonClick?: () => void;
}

const getBannerColor = (remainingDays: number) => {
  if (remainingDays <= 8) return 'gradient_yellow';
  if (remainingDays <= 22) return 'gradient_green';
  return 'gradient_blue';
};

const getButtonColor = (remainingDays: number): string => {
  if (remainingDays <= 8) return '#884106';
  if (remainingDays <= 22) return '#005744';
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

const computeBannerInfo = (eeSettings: License, onButtonClick?: () => void): BannerInfo | undefined => {
  const { t } = useFormatter();
  if (!eeSettings.license_is_validated) {
    return {
      message: `The current ${eeSettings.license_type} license has expired, Enterprise Edition is disabled.`,
      bannerColor: 'red',
      buttonStyle: getButtonStyle(0),
    };
  }
  if (eeSettings.license_is_extra_expiration) {
    return {
      message: `The current ${eeSettings.license_type} license has expired, Enterprise Edition will be disabled in ${eeSettings.license_extra_expiration_days} days.`,
      bannerColor: 'red',
      buttonStyle: getButtonStyle(0),
    };
  }
  if (eeSettings.license_type === LICENSE_OPTION_TRIAL) {
    const remainingDays = daysBetweenDates(moment(), moment(eeSettings.license_expiration_date));
    const bannerColor = getBannerColor(remainingDays);
    return {
      buttonText: t('Reach out to sales'),
      bannerColor,
      message: (
        <>
          {t('Your OpenCTI Enterprise Edition free trial is active: ')}
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

  const [showThankYouDialog, setShowThankYouDialog] = useState(false);
  const [showFormDialog, setShowFormDialog] = useState(false);
  const eeSettings = settings.settings?.platform_license;
  const isEE = eeSettings?.license_type === 'trial';
  if (!isEE) return null;

  const onSubmit = (values: ContactUsInput) => {
    return simplePostCall(`/api/xtmhub/contact-us`, { message: values.message })
      .then(() => {
        setShowThankYouDialog(true);
        setShowFormDialog(false);
      });
  };

  const bannerInfo = computeBannerInfo(eeSettings, () => {
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
        fullWidth={true}
        open={showFormDialog}
        onClose={() => setShowFormDialog(false)}
      >
        <DialogTitle>{t('Thank you!')}</DialogTitle>
        <form id="contactUsForm" onSubmit={handleSubmit(onSubmit)}>
          <DialogContent>
            <TextField
              {...register('message')}
              variant="standard"
              fullWidth
              multiline
              rows={5}
              label={t('Your message')}
            />
          </DialogContent>
          <DialogActions>
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
          </DialogActions>
        </form>
      </Dialog>
      <Dialog open={showThankYouDialog} onClose={() => setShowThankYouDialog(false)}>
        <DialogTitle>{t('Thank you!')}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t('Thank you for reaching out, we\'ll get back to you shortly.')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowThankYouDialog(false)} color="primary">
            {t('Close')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default LicenseBanner;
