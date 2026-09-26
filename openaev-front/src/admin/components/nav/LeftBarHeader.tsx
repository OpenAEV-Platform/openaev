import { ProductSwitcher } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useContext, useEffect } from 'react';

import { type LoggedHelper } from '../../../actions/helper';
import { fetchXtmHubRegistration } from '../../../actions/xtmhub/xtmhub-actions';
import { useFormatter } from '../../../components/i18n';
import { REDIRECT_CONNECT_XTM_HUB_URL } from '../../../constants/BaseUrls';
import logoOpenCtiDark from '../../../static/images/logo_open_cti_dark.svg';
import logoOpenCtiLight from '../../../static/images/logo_open_cti_light.svg';
import logoXtmHubDark from '../../../static/images/logo_xtm_hub_dark.svg';
import logoXtmHubLight from '../../../static/images/logo_xtm_hub_light.svg';
import { useHelper } from '../../../store';
import { fileUri, XTM_HUB_DEFAULT_URL } from '../../../utils/Environment';
import { useAppDispatch } from '../../../utils/hooks';
import useAuth from '../../../utils/hooks/useAuth';
import { AbilityContext } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { computeTenantBasename } from '../../../utils/url-helper';

const LeftBarHeader: FunctionComponent = () => {
  const theme = useTheme();
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);
  const { settings, isXTMHubAccessible } = useAuth();

  useEffect(() => {
    if (ability.can(ACTIONS.ACCESS, SUBJECTS.TENANT_SETTINGS)) {
      dispatch(fetchXtmHubRegistration());
    }
  }, []);

  const tenantSettings = useHelper((helper: LoggedHelper) => helper.getTenantSettings());
  const registration = useHelper((helper: LoggedHelper) => helper.getXtmHubRegistration());
  const isRegistered = registration?.tenant_xtmhub_registration_status === 'REGISTERED';
  const shouldXtmHubRedirectToSite = isRegistered || !isXTMHubAccessible || !ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS);

  const isDark = theme.palette.mode === 'dark';
  const isOpenCtiConnected = !!(tenantSettings?.xtm_opencti_enable && tenantSettings?.xtm_opencti_url);
  const openCtiUrl = isOpenCtiConnected
    ? tenantSettings.xtm_opencti_url
    : 'https://filigran.io/platform/opencti/';
  const xtmHubUrl = settings.xtm_hub_enable && settings.xtm_hub_url
    ? settings.xtm_hub_url
    : XTM_HUB_DEFAULT_URL;

  const productLogo = (src: string, alt: string) => (
    <img
      src={fileUri(src)}
      alt={alt}
      style={{
        width: '100%',
        height: 'auto',
        objectFit: 'contain',
      }}
    />
  );

  return (
    <ProductSwitcher
      label={t('Filigran products')}
      logo={(
        <img
          src={theme.logo}
          alt=""
          // Inline geometry: no Tailwind build, see the adapter README rule.
          // A class here silently stretched the logo to the slot's width.
          style={{
            height: 28,
            width: '100%',
            objectFit: 'contain',
            objectPosition: 'left center',
          }}
        />
      )}
      logoCollapsed={(
        <img
          src={theme.logo_collapsed}
          alt=""
          // The library's collapsed slot is a 28px square that clips rather
          // than scales its child, and gives it no height of its own — so the
          // asset has to be sized here.
          style={{
            height: 28,
            width: 28,
            objectFit: 'contain',
          }}
        />
      )}
      // The library renders the destination as a plain anchor, with no router
      // integration, so the tenant basename has to be prefixed by hand — a
      // bare "/admin" would drop the tenant segment on a tenant deployment.
      // See fds-migration/LIBRARY-FEEDBACK.md.
      logoHref={`${computeTenantBasename()}/admin`}
      logoLabel={t('Home')}
      options={[
        {
          id: 'opencti',
          label: 'OpenCTI',
          logo: productLogo(isDark ? logoOpenCtiDark : logoOpenCtiLight, 'OpenCTI'),
          tooltip: isOpenCtiConnected ? t('Platform connected') : t('Get OpenCTI now'),
          href: openCtiUrl,
        },
        shouldXtmHubRedirectToSite
          ? {
              id: 'xtm-hub',
              label: 'XTM Hub',
              logo: productLogo(isDark ? logoXtmHubDark : logoXtmHubLight, 'XTM Hub'),
              tooltip: isRegistered ? t('Platform connected') : t('Get XTM Hub now'),
              href: xtmHubUrl,
            }
          : {
              id: 'xtm-hub',
              label: 'XTM Hub',
              logo: productLogo(isDark ? logoXtmHubDark : logoXtmHubLight, 'XTM Hub'),
              tooltip: t('Connect your product'),
              to: REDIRECT_CONNECT_XTM_HUB_URL,
            },
      ]}
    />
  );
};

export default LeftBarHeader;
