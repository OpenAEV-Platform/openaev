import { type PlatformSettings } from '../../../utils/api-types';
import { toHttpUrl } from '../../../utils/url-helper';

/**
 * Single source of truth for the visibility of the XTM One (agentic AI)
 * surface: the Ask Ariane button, the CTEM Command Center shortcut, the
 * divider grouping them in the top bar, and the chat panel itself.
 *
 * XTM One is available when the agentic AI has not been explicitly disabled
 * and the platform is connected to XTM One: `platform_xtm_one_configured`
 * (url + token set on the backend) with a syntactically valid http(s)
 * `platform_xtm_one_url` (guarded by the shared http(s)-only helper, since
 * the URL ends up in anchor hrefs). Without that, every `/api/xtmone/chat/*`
 * proxy call is rejected, so no XTM One entry point can lead anywhere.
 */
const isXtmOneAvailable = (settings: PlatformSettings): boolean => (
  settings.filigran_chatbot_ai_cgu_status !== 'disabled'
  && settings.platform_xtm_one_configured === true
  && toHttpUrl(settings.platform_xtm_one_url) !== undefined
);

export default isXtmOneAvailable;
