import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { errorWrapper } from '../../../components/Error';
import Loader from '../../../components/Loader';
import NotFound from '../../../components/NotFound';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import ProtectedRoute from '../../../utils/permissions/ProtectedRoute';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';

const IndexChannel = lazy(() => import('./channels/Index'));
const Channels = lazy(() => import('./channels/Channels'));
const Phishing = lazy(() => import('./phishing/Phishing'));
const IndexPhishingLandingPage = lazy(() => import('./phishing/landing_pages/Index'));
const IndexPhishingEmailTemplate = lazy(() => import('./phishing/email_templates/Index'));
const PhishingLandingPageEditor = lazy(() => import('./phishing/landing_pages/PhishingLandingPageEditor'));
const PhishingEmailTemplateEditor = lazy(() => import('./phishing/email_templates/PhishingEmailTemplateEditor'));
const Documents = lazy(() => import('./documents/Documents'));
const Challenges = lazy(() => import('./challenges/Challenges'));

const useStyles = makeStyles()(() => ({ root: { flexGrow: 1 } }));

// Lessons learned templates moved to Settings > Customization; old bookmarks
// under /admin/components/lessons keep working through this redirect.
const LegacyLessonsRedirect = () => {
  const location = useLocation();
  return (
    <Navigate
      to={location.pathname.replace('/admin/components/lessons', '/admin/settings/customization/lessons')}
      replace={true}
    />
  );
};

const Index = () => {
  const { classes } = useStyles();
  const ability = useAbility();

  const order = ['DOCUMENTS', 'CHANNELS', 'PHISHING', 'CHALLENGES'] as const;

  const subjectToRoute: Record<typeof order[number], string> = {
    DOCUMENTS: 'documents',
    CHANNELS: 'channels',
    PHISHING: 'phishing',
    CHALLENGES: 'challenges',
  };

  const accessibleSubject = order.find(subject => ability.can(ACTIONS.ACCESS, subject));

  const navigation = accessibleSubject ? subjectToRoute[accessibleSubject] : '/';

  return (
    <div className={classes.root}>
      <Suspense fallback={<Loader />}>
        <Routes>
          <Route path="" element={<Navigate to={navigation} replace={true} />} />
          <Route
            path="documents"
            element={(
              <ProtectedRoute
                checks={[{
                  action: ACTIONS.ACCESS,
                  subject: SUBJECTS.DOCUMENTS,
                }]}
                Component={errorWrapper(Documents)()}
              />
            )}
          />
          <Route
            path="channels"
            element={(
              <ProtectedRoute
                checks={[{
                  action: ACTIONS.ACCESS,
                  subject: SUBJECTS.CHANNELS,
                }]}
                Component={errorWrapper(Channels)()}
              />
            )}
          />
          <Route
            path="channels/:channelId/*"
            element={(
              <ProtectedRoute
                checks={[{
                  action: ACTIONS.ACCESS,
                  subject: SUBJECTS.CHANNELS,
                }]}
                Component={errorWrapper(IndexChannel)()}
              />
            )}
          />
          {/* Single "Phishing" page with a Pages / Emails tab; the tab is a URL
              segment so old /phishing/landing_pages and /phishing/email_templates
              bookmarks land directly on the right tab. */}
          <Route
            path="phishing"
            element={(
              <ProtectedRoute
                checks={[{
                  action: ACTIONS.ACCESS,
                  subject: SUBJECTS.PHISHING,
                }]}
                Component={errorWrapper(Phishing)()}
              />
            )}
          />
          <Route
            path="phishing/:tab"
            element={(
              <ProtectedRoute
                checks={[{
                  action: ACTIONS.ACCESS,
                  subject: SUBJECTS.PHISHING,
                }]}
                Component={errorWrapper(Phishing)()}
              />
            )}
          />
          {/* Full-page create / edit editors (replace the old drawer). Static
              "create" and "edit" segments out-rank the :id detail routes. */}
          <Route
            path="phishing/landing_pages/create"
            element={(
              <ProtectedRoute
                checks={[{
                  action: ACTIONS.MANAGE,
                  subject: SUBJECTS.PHISHING,
                }]}
                Component={errorWrapper(PhishingLandingPageEditor)()}
              />
            )}
          />
          <Route
            path="phishing/landing_pages/:landingPageId/edit"
            element={(
              <ProtectedRoute
                checks={[{
                  action: ACTIONS.MANAGE,
                  subject: SUBJECTS.PHISHING,
                }]}
                Component={errorWrapper(PhishingLandingPageEditor)()}
              />
            )}
          />
          <Route
            path="phishing/email_templates/create"
            element={(
              <ProtectedRoute
                checks={[{
                  action: ACTIONS.MANAGE,
                  subject: SUBJECTS.PHISHING,
                }]}
                Component={errorWrapper(PhishingEmailTemplateEditor)()}
              />
            )}
          />
          <Route
            path="phishing/email_templates/:emailTemplateId/edit"
            element={(
              <ProtectedRoute
                checks={[{
                  action: ACTIONS.MANAGE,
                  subject: SUBJECTS.PHISHING,
                }]}
                Component={errorWrapper(PhishingEmailTemplateEditor)()}
              />
            )}
          />
          <Route
            path="phishing/landing_pages/:landingPageId/*"
            element={(
              <ProtectedRoute
                checks={[{
                  action: ACTIONS.ACCESS,
                  subject: SUBJECTS.PHISHING,
                }]}
                Component={errorWrapper(IndexPhishingLandingPage)()}
              />
            )}
          />
          <Route
            path="phishing/email_templates/:emailTemplateId/*"
            element={(
              <ProtectedRoute
                checks={[{
                  action: ACTIONS.ACCESS,
                  subject: SUBJECTS.PHISHING,
                }]}
                Component={errorWrapper(IndexPhishingEmailTemplate)()}
              />
            )}
          />
          <Route
            path="challenges"
            element={(
              <ProtectedRoute
                checks={[{
                  action: ACTIONS.ACCESS,
                  subject: SUBJECTS.CHALLENGES,
                }]}
                Component={errorWrapper(Challenges)()}
              />
            )}
          />
          <Route path="lessons" element={<LegacyLessonsRedirect />} />
          <Route path="lessons/*" element={<LegacyLessonsRedirect />} />
          {/* Not found */}
          <Route path="*" element={<NotFound />} />
        </Routes>
      </Suspense>
    </div>
  );
};

export default Index;
