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
  bannerText: {
    color: 'black',
    fontWeight: 'bold',
  },
  bannerLink: {
    color: 'black',
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
  const isSafeMode = settings.settings.platform_run_mode === 'safe';
  const bannerLevel = (settings.settings.platform_banner_by_level ?? {}) as Record<string, string[]>;
  const effectiveBannerLevel: Record<string, string[]> = {};
  for (const currentBannerLevel of recordEntries(bannerLevel)) {
    effectiveBannerLevel[currentBannerLevel[0]] = [...currentBannerLevel[1]];
  }
  if (isSafeMode) {
    effectiveBannerLevel.warn = [...(effectiveBannerLevel.warn ?? []), SAFE_MODE_MESSAGE_KEY];
  }

  let numberOfElements = 0;
  for (const currentBannerLevel of recordEntries(effectiveBannerLevel)) {
    numberOfElements += currentBannerLevel[1].length;
  }

  if (isEmptyField(effectiveBannerLevel) || numberOfElements === 0) {
    return <></>;
  }

  return (
    <div>
      {recordKeys(effectiveBannerLevel as BannerMessage).map((key) => {
        const topBannerClasses = [
          classes.banner,
          classes.bannerTop,
          classes[`banner_${key}`],
        ].join(' ');

        return (
          <div key={key} className={topBannerClasses}>
            {effectiveBannerLevel[key].map((message: string) => {
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
