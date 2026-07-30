import { type SecurityPlatform } from '../../../../utils/api-types';

/**
 * A security platform is read-only only while a collector or an injector actively
 * manages it (`security_platform_collectors` / `security_platform_injectors` reflect
 * the live connector -> platform links; e.g. Nuclei registers its platform as an
 * injector). `asset_external_reference` must NOT be used for this: it is set at
 * creation and never cleared, so platforms orphaned by a connector purge would stay
 * locked forever.
 */
const isConnectorManaged = (securityPlatform: SecurityPlatform) =>
  (securityPlatform.security_platform_collectors ?? []).length > 0
  || (securityPlatform.security_platform_injectors ?? []).length > 0;

export default isConnectorManaged;
