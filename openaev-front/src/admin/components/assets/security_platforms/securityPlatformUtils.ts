import { type SecurityPlatform } from '../../../../utils/api-types';

/**
 * A security platform is read-only only while a collector actively manages it
 * (`security_platform_collectors` reflects the live collector -> platform link).
 * `asset_external_reference` must NOT be used for this: it is set at creation and
 * never cleared, so platforms orphaned by a collector purge would stay locked forever.
 */
const isCollectorManaged = (securityPlatform: SecurityPlatform) =>
  (securityPlatform.security_platform_collectors ?? []).length > 0;

export default isCollectorManaged;
