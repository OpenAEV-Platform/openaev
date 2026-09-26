import { ReportProblem } from '@mui/icons-material';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../components/i18n';
import { type PlatformSettings } from '../../../utils/api-types';
import { isEmptyField, recordEntries, recordKeys } from '../../../utils/utils';
import { type BannerMessage } from './utils';

const SAFE_MODE_MESSAGE_KEY = 'Safe mode is active: background processing is disabled.';
const SAFE_MODE_DOCUMENTATION_URL = 'https://docs.openaev.io/latest/deployment/platform/run-modes/';

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
  // The band is filled with a feedback colour, so its text reads in the ink
  // meant for a filled surface, not in a literal black.
  bannerText: {
    color: 'var(--text-negative-primary)',
    fontWeight: 'bold',
  },
  bannerLink: {
    color: 'var(--text-negative-primary)',
    marginLeft: 4,
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
  const bannerLevel = settings.settings.platform_banner_by_level as BannerMessage;

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
              const isSafeModeMessage = message === SAFE_MODE_MESSAGE_KEY;
              return (
                <div key={`${key}.${message}`} className={classes.container}>
                  <ReportProblem color="error" fontSize="small" style={{ marginRight: 8 }} />
                  <span className={classes.bannerText}>
                    {t(message)}
                    {isSafeModeMessage && (
                      <a
                        href={SAFE_MODE_DOCUMENTATION_URL}
                        target="_blank"
                        rel="noopener noreferrer"
                        className={classes.bannerLink}
                      >
                        {t('Learn more')}
                      </a>
                    )}
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
