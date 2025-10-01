import { ReportProblem } from '@mui/icons-material';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../components/i18n';
import { type PlatformSettings } from '../../../utils/api-types';
import { isEmptyField, recordEntries, recordKeys } from '../../../utils/utils';
import { computeBanners } from './utils';

/* eslint-disable */
/* Avoid auto-lint removal using --fix with false positive finding of: */
const useStyles = makeStyles()((theme) => ({
  banner: {
    position: 'fixed',
    zIndex: 2000,
    width: '100%',
    alignContent: 'center',
    textAlign: 'center',
  },
  bannerTop: {
    top: 0,
  },
  container: {
    display: 'flex',
    justifyContent: 'center',
  },
  bannerText: {
    color: 'black',
    fontWeight: 'bold',
  },
  banner_debug: {
    background: theme.palette.success.main,
  },
  banner_info: {
    background: theme.palette.primary.main,
  },
  banner_warn: {
    background: theme.palette.warning.main,
  },
  banner_error: {
    background: '#fbcbc5',
  },
  banner_fatal: {
    background: theme.palette.error.dark,
  },
}));
/* end banner classes needing eslint-disable */
/* eslint-enable */

const SystemBanners = (settings: { settings: PlatformSettings }) => {
  // Standard hooks
  const { t } = useFormatter();
  const { classes } = useStyles();
  const bannerLevel = computeBanners(settings.settings);
  let numberOfElements = 0;
  if (bannerLevel !== undefined) {
    for (const currentBannerLevel of recordEntries(bannerLevel)) {
      numberOfElements += currentBannerLevel[1].length;
    }
  }
  if (isEmptyField(bannerLevel) || numberOfElements === 0) {
    return <></>;
  }

  return (
    <div>
      {recordKeys(bannerLevel).map((key) => {
        const topBannerClasses = [
          classes.banner,
          classes.bannerTop,
          classes[`banner_${key}`],
        ].join(' ');

        return (
          <div key={key} className={topBannerClasses}>
            {bannerLevel[key].map((message: string) => {
              return (
                <div key={`${key}.${message}`} className={classes.container}>
                  <ReportProblem color="error" fontSize="small" style={{ marginRight: 8 }} />
                  <span className={classes.bannerText}>
                    {t(message)}
                  </span>
                </div>
              );
            })}
          </div>
        );
      })}
    </div>
  );
};

export default SystemBanners;
