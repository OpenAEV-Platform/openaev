const XTM_HUB_REGISTRATION_REDIRECT_PATH = 'redirect/openaev-registration';

/**
 * Page of this tenant's registration on XTM Hub. The Hub resolves the platform and tenant ids into
 * the registration page, and falls back to its home page when it cannot; without a platform id the
 * link goes to the Hub itself.
 */
export const buildXtmHubRegistrationUrl = (hubUrl: string, platformId?: string, tenantId?: string): string => {
  if (!platformId) return hubUrl;
  const query = new URLSearchParams({ platform_id: platformId });
  if (tenantId) query.set('tenant_id', tenantId);
  return `${hubUrl.replace(/\/+$/, '')}/${XTM_HUB_REGISTRATION_REDIRECT_PATH}?${query.toString()}`;
};
