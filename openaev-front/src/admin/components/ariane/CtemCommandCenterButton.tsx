import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@filigran/design-system';
import { RadarOutlined } from '@mui/icons-material';

import { useFormatter } from '../../../components/i18n';
import useAuth from '../../../utils/hooks/useAuth';
import { toHttpUrl } from '../../../utils/url-helper';
import TopBarIconLink from '../nav/TopBarIconLink';
import isXtmOneAvailable from './xtmOneAvailability';

/**
 * Top-bar shortcut to the XTM One CTEM Command Center (the cross-product exposure
 * posture dashboard / XTM One home). Opens the XTM One URL in a new tab.
 *
 * Shown only when XTM One is available (shared `isXtmOneAvailable` predicate:
 * `platform_xtm_one_configured` with a valid http(s) `platform_xtm_one_url`,
 * agentic AI not disabled). NOT Enterprise-gated: the CTEM Command Center is
 * also available in full CE (metrics only).
 */
const CtemCommandCenterButton = () => {
  const { t } = useFormatter();
  const { settings } = useAuth();

  // `!xtmOneUrl` is implied by `isXtmOneAvailable` but kept for type narrowing
  // of the anchor href below.
  const xtmOneUrl = toHttpUrl(settings.platform_xtm_one_url);
  if (!isXtmOneAvailable(settings) || !xtmOneUrl) {
    return null;
  }

  return (
    <TooltipProvider delayDuration={200}>
      <Tooltip>
        <TooltipTrigger asChild>
          <TopBarIconLink
            aria-label={t('CTEM Command Center')}
            href={xtmOneUrl}
            // AI purple, like the Ask Ariane button next to it: this shortcut
            // belongs to XTM One. The rest of the anatomy - size, radius,
            // focus and hover - comes from the library's icon button contract.
            color="var(--color-filigran-ia-primary)"
            icon={<RadarOutlined fontSize="medium" />}
          />
        </TooltipTrigger>
        <TooltipContent>{t('Open CTEM Command Center in XTM One')}</TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
};

export default CtemCommandCenterButton;
