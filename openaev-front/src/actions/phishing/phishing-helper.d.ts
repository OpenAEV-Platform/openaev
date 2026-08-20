import { type PhishingEmailTemplate, type PhishingLandingPage } from '../../utils/api-types';

export interface PhishingLandingPagesHelper {
  getPhishingLandingPage: (landingPageId: PhishingLandingPage['phishing_landing_page_id']) => PhishingLandingPage;
  getPhishingLandingPagesMap: () => Record<string, PhishingLandingPage>;
  getPhishingLandingPages: () => PhishingLandingPage[];
}

export interface PhishingEmailTemplatesHelper {
  getPhishingEmailTemplate: (emailTemplateId: PhishingEmailTemplate['phishing_email_template_id']) => PhishingEmailTemplate;
  getPhishingEmailTemplatesMap: () => Record<string, PhishingEmailTemplate>;
  getPhishingEmailTemplates: () => PhishingEmailTemplate[];
}
