import * as PropTypes from 'prop-types';
import { lazy, Suspense } from 'react';
import { Route, Routes } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { errorWrapper } from '../components/Error';
import Loader from '../components/Loader';
import Reset from './components/login/Reset';

const Login = lazy(() => import('./components/login/Login'));
const Comcheck = lazy(() => import('./components/comcheck/Comcheck'));
const Channel = lazy(() => import('./components/channels/Channel'));
const Challenges = lazy(() => import('./components/challenges/ChallengesPlayer'));
const ExerciseViewLessons = lazy(() => import('./components/lessons/ExerciseViewLessons'));
const ScenarioViewLessons = lazy(() => import('./components/lessons/ScenarioViewLessons'));
const UrlAccess = lazy(() => import('./components/url_access/UrlAccess'));
const PhishingPage = lazy(() => import('./components/phishing/PhishingPage'));
const ErrorHandler = lazy(() => import('./components/error_handler/./ErrorHandler'));

const useStyles = makeStyles()(() => ({
  root: {
    minWidth: 1280,
    height: '100%',
    overflowY: 'auto',
  },
  content: {
    height: '100%',
    flexGrow: 1,
    // fds-migration/TOKEN-MAPPING.md § ISO OpenCTI — see private/Index.tsx for the full
    // rationale: this must stay transparent so <body>'s two-stop FDS gradient shows
    // through (this wraps the login screen too, which reads platform_theme directly).
    padding: 0,
    minWidth: 0,
  },
}));

const Index = () => {
  const { classes } = useStyles();

  return (
    <div className={classes.root}>
      <main className={classes.content}>
        <Suspense fallback={<Loader />}>
          <Routes>
            <Route path="comcheck/:statusId" element={errorWrapper(Comcheck)()} />
            <Route path="reset" element={errorWrapper(Reset)()} />
            <Route path="channels/:exerciseId/:channelId" element={errorWrapper(Channel)()} />
            <Route path="challenges/:exerciseId" element={errorWrapper(Challenges)()} />
            <Route path="lessons/simulation/:exerciseId" element={errorWrapper(ExerciseViewLessons)()} />
            <Route path="lessons/scenario/:scenarioId" element={errorWrapper(ScenarioViewLessons)()} />
            <Route path="url/access" element={errorWrapper(UrlAccess)()} />
            {/* Benign, tenant-less phishing landing route (tenant resolved server-side from token) */}
            <Route path="auth/:token" element={errorWrapper(PhishingPage)()} />
            {/* Legacy tenant-scoped route kept alive for links already delivered before the redesign */}
            <Route path="phishing/:tenantId/:token" element={errorWrapper(PhishingPage)()} />
            <Route path="handle-error" element={errorWrapper(ErrorHandler)()} />
            <Route path="*" element={<Login />} />
          </Routes>
        </Suspense>
      </main>
    </div>
  );
};

Index.propTypes = { classes: PropTypes.object };

export default Index;
