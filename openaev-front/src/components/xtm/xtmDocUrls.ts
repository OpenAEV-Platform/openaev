import docsUrl from '../../utils/docsUrl';

// The XTM One connection is deployment configuration (openaev.xtm.one.*), documented on its page.
export const CONNECT_XTM_ONE_DOC_URL = docsUrl('xtm_one');

// The OpenCTI connection of a tenant is deployment configuration (openaev.xtm.opencti.{tenant}.*),
// so the emblem of a configurer opens its documentation page.
export const CONNECT_OPENCTI_DOC_URL = docsUrl('xtm_suite_connector');

// The OpenCRQ connection of a tenant is deployment configuration (openaev.xtm.opencrq.{tenant}.*),
// documented on the same XTM integration page as the OpenCTI one.
export const CONNECT_OPENCRQ_DOC_URL = CONNECT_OPENCTI_DOC_URL;
