import { type Dispatch } from 'redux';

import {
  delReferential,
  getReferential,
  postReferential,
  putReferential,
  simpleDelCall,
  simplePostCall,
} from '../../utils/Action';
import { type SearchPaginationInput } from '../../utils/api-types';
import {
  arrayOfPhishingEmailTemplates,
  arrayOfPhishingLandingPages,
  phishingEmailTemplate,
  phishingLandingPage,
} from './phishing-schema';

// Bulk processing input shapes mirror the backend
// PhishingLandingPageBulkProcessingInput / PhishingEmailTemplateBulkProcessingInput DTOs.
export interface PhishingLandingPageBulkProcessingInput {
  search_pagination_input?: SearchPaginationInput;
  landing_page_ids_to_process?: string[];
  landing_page_ids_to_ignore?: string[];
}

export interface PhishingEmailTemplateBulkProcessingInput {
  search_pagination_input?: SearchPaginationInput;
  email_template_ids_to_process?: string[];
  email_template_ids_to_ignore?: string[];
}

// -- LANDING PAGES --

const LANDING_PAGES_URI = '/api/phishing/landing-pages';

export const fetchPhishingLandingPages = () => (dispatch: Dispatch) => {
  return getReferential(arrayOfPhishingLandingPages, LANDING_PAGES_URI)(dispatch);
};
export const searchPhishingLandingPages = (input: SearchPaginationInput) => {
  return simplePostCall(`${LANDING_PAGES_URI}/search`, input);
};
export const bulkDeletePhishingLandingPages = (input: PhishingLandingPageBulkProcessingInput) => {
  return simpleDelCall(LANDING_PAGES_URI, { data: input });
};
export const fetchPhishingLandingPage = (landingPageId: string) => (dispatch: Dispatch) => {
  return getReferential(phishingLandingPage, `${LANDING_PAGES_URI}/${landingPageId}`)(dispatch);
};
export const addPhishingLandingPage = (data: unknown) => (dispatch: Dispatch) => {
  return postReferential(phishingLandingPage, LANDING_PAGES_URI, data)(dispatch);
};
export const updatePhishingLandingPage = (landingPageId: string, data: unknown) => (dispatch: Dispatch) => {
  return putReferential(phishingLandingPage, `${LANDING_PAGES_URI}/${landingPageId}`, data)(dispatch);
};
export const updatePhishingLandingPageLogos = (landingPageId: string, data: unknown) => (dispatch: Dispatch) => {
  return putReferential(phishingLandingPage, `${LANDING_PAGES_URI}/${landingPageId}/logos`, data)(dispatch);
};
export const duplicatePhishingLandingPage = (landingPageId: string) => (dispatch: Dispatch) => {
  return postReferential(phishingLandingPage, `${LANDING_PAGES_URI}/${landingPageId}/duplicate`, {})(dispatch);
};
export const deletePhishingLandingPage = (landingPageId: string) => (dispatch: Dispatch) => {
  return delReferential(`${LANDING_PAGES_URI}/${landingPageId}`, 'phishinglandingpages', landingPageId)(dispatch);
};

// -- EMAIL TEMPLATES --

const EMAIL_TEMPLATES_URI = '/api/phishing/email-templates';

export const fetchPhishingEmailTemplates = () => (dispatch: Dispatch) => {
  return getReferential(arrayOfPhishingEmailTemplates, EMAIL_TEMPLATES_URI)(dispatch);
};
export const searchPhishingEmailTemplates = (input: SearchPaginationInput) => {
  return simplePostCall(`${EMAIL_TEMPLATES_URI}/search`, input);
};
export const bulkDeletePhishingEmailTemplates = (input: PhishingEmailTemplateBulkProcessingInput) => {
  return simpleDelCall(EMAIL_TEMPLATES_URI, { data: input });
};
export const fetchPhishingEmailTemplate = (emailTemplateId: string) => (dispatch: Dispatch) => {
  return getReferential(phishingEmailTemplate, `${EMAIL_TEMPLATES_URI}/${emailTemplateId}`)(dispatch);
};
export const addPhishingEmailTemplate = (data: unknown) => (dispatch: Dispatch) => {
  return postReferential(phishingEmailTemplate, EMAIL_TEMPLATES_URI, data)(dispatch);
};
export const updatePhishingEmailTemplate = (emailTemplateId: string, data: unknown) => (dispatch: Dispatch) => {
  return putReferential(phishingEmailTemplate, `${EMAIL_TEMPLATES_URI}/${emailTemplateId}`, data)(dispatch);
};
export const duplicatePhishingEmailTemplate = (emailTemplateId: string) => (dispatch: Dispatch) => {
  return postReferential(phishingEmailTemplate, `${EMAIL_TEMPLATES_URI}/${emailTemplateId}/duplicate`, {})(dispatch);
};
export const deletePhishingEmailTemplate = (emailTemplateId: string) => (dispatch: Dispatch) => {
  return delReferential(`${EMAIL_TEMPLATES_URI}/${emailTemplateId}`, 'phishingemailtemplates', emailTemplateId)(dispatch);
};
