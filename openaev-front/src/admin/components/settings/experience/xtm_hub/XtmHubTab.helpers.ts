import { XTM_HUB_PRODUCT_NAME_QUERY_PARAM } from '../../../RedirectByPath';

export const getXtmHubProductName = (search: string) => {
  const productName = new URLSearchParams(search).get(XTM_HUB_PRODUCT_NAME_QUERY_PARAM);
  if (!productName) {
    return null;
  }
  const trimmedProductName = productName.trim();
  return trimmedProductName.length > 0 ? trimmedProductName : null;
};

export const getRegistrationPlatformTitle = ({
  autoRegistrationProductName,
  fallbackPlatformTitle,
}: {
  autoRegistrationProductName: string | null;
  fallbackPlatformTitle: string;
}) => autoRegistrationProductName ?? fallbackPlatformTitle;
