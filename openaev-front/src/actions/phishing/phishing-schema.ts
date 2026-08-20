import { schema } from 'normalizr';

export const phishingLandingPage = new schema.Entity(
  'phishinglandingpages',
  {},
  { idAttribute: 'phishing_landing_page_id' },
);
export const arrayOfPhishingLandingPages = new schema.Array(phishingLandingPage);

export const phishingEmailTemplate = new schema.Entity(
  'phishingemailtemplates',
  {},
  { idAttribute: 'phishing_email_template_id' },
);
export const arrayOfPhishingEmailTemplates = new schema.Array(phishingEmailTemplate);
