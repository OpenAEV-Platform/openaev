import { simpleCall, simplePostCall, simplePutCall } from '../../utils/Action';
import type { IocValidationRejectInput, IocValidationSettingsInput, SearchPaginationInput } from '../../utils/api-types';

const IOC_VALIDATION_URI = '/api/ioc-validations';

export const searchIocValidations = (searchPaginationInput: SearchPaginationInput) => {
  return simplePostCall(`${IOC_VALIDATION_URI}/search`, searchPaginationInput);
};

export const fetchIocValidation = (iocValidationId: string) => {
  return simpleCall(`${IOC_VALIDATION_URI}/${iocValidationId}`);
};

export const approveIocValidation = (iocValidationId: string) => {
  return simplePostCall(`${IOC_VALIDATION_URI}/${iocValidationId}/approve`);
};

export const rejectIocValidation = (iocValidationId: string, data: IocValidationRejectInput) => {
  return simplePostCall(`${IOC_VALIDATION_URI}/${iocValidationId}/reject`, data);
};

export const fetchIocValidationSettings = () => {
  return simpleCall(`${IOC_VALIDATION_URI}/settings`);
};

export const searchIocValidationAssetGroupOptions = (searchText: string = '') => {
  return simpleCall(`${IOC_VALIDATION_URI}/settings/asset-group-options`, { params: { searchText } });
};

export const updateIocValidationSettings = (data: IocValidationSettingsInput) => {
  return simplePutCall(`${IOC_VALIDATION_URI}/settings`, data, undefined, true, false);
};
