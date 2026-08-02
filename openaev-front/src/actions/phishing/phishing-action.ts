import { type Dispatch } from 'redux';

import {
  delReferential,
  getReferential,
  postReferential,
  putReferential,
} from '../../utils/Action';
import {
  arrayOfPhishingEmailTemplates,
  arrayOfPhishingLandingPages,
  phishingEmailTemplate,
  phishingLandingPage,
} from './phishing-schema';

// -- LANDING PAGES --

const LANDING_PAGES_URI = '/api/phishing/landing-pages';

export const fetchPhishingLandingPages = () => (dispatch: Dispatch) => {
  return getReferential(arrayOfPhishingLandingPages, LANDING_PAGES_URI)(dispatch);
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
