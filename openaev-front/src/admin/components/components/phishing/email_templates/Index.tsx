import { lazy } from 'react';
import { Route, Routes, useParams } from 'react-router';

import { fetchPhishingEmailTemplate } from '../../../../../actions/phishing/phishing-action';
import { type PhishingEmailTemplatesHelper } from '../../../../../actions/phishing/phishing-helper';
import Breadcrumbs from '../../../../../components/Breadcrumbs';
import { errorWrapper } from '../../../../../components/Error';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import NotFound from '../../../../../components/NotFound';
import { useHelper } from '../../../../../store';
import { type PhishingEmailTemplate as PhishingEmailTemplateType } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import PhishingEmailTemplateHeader from './PhishingEmailTemplateHeader';

const PhishingEmailTemplate = lazy(() => import('./PhishingEmailTemplate'));

const Index = () => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { emailTemplateId } = useParams() as { emailTemplateId: PhishingEmailTemplateType['phishing_email_template_id'] };
  const { emailTemplate } = useHelper(
    (helper: PhishingEmailTemplatesHelper) => ({ emailTemplate: helper.getPhishingEmailTemplate(emailTemplateId) }),
  );
  useDataLoader(() => {
    dispatch(fetchPhishingEmailTemplate(emailTemplateId));
  });
  if (emailTemplate) {
    return (
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 16,
      }}
      >
        <Breadcrumbs
          variant="object"
          elements={[
            { label: t('Components') },
            {
              label: t('Phishing emails'),
              link: '/admin/components/phishing/email_templates',
            },
            {
              label: emailTemplate.phishing_email_template_name,
              current: true,
            },
          ]}
        />
        <PhishingEmailTemplateHeader />
        <Routes>
          <Route path="" element={errorWrapper(PhishingEmailTemplate)()} />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </div>
    );
  }
  return <Loader />;
};

export default Index;
