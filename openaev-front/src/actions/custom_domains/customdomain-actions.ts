import { simpleCall, simpleDelCall, simplePostCall } from '../../utils/Action';
import { type CustomDomainInput, type SearchPaginationInput } from '../../utils/api-types';

const CUSTOM_DOMAINS_URI = '/api/custom-domains';

export const searchCustomDomains = (searchPaginationInput: SearchPaginationInput) => {
  return simplePostCall(`${CUSTOM_DOMAINS_URI}/search`, searchPaginationInput);
};

export const fetchCustomDomain = (customDomainId: string) => {
  return simpleCall(`${CUSTOM_DOMAINS_URI}/${customDomainId}`);
};

export const fetchCustomDomainInstructions = (customDomainId: string) => {
  return simpleCall(`${CUSTOM_DOMAINS_URI}/${customDomainId}/instructions`);
};

export const addCustomDomain = (data: CustomDomainInput) => {
  return simplePostCall(CUSTOM_DOMAINS_URI, data);
};

export const verifyCustomDomain = (customDomainId: string) => {
  return simplePostCall(`${CUSTOM_DOMAINS_URI}/${customDomainId}/verify`, {});
};

export const deleteCustomDomain = (customDomainId: string) => {
  return simpleDelCall(`${CUSTOM_DOMAINS_URI}/${customDomainId}`);
};
