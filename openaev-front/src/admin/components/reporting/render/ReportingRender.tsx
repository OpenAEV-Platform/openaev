import { Box, Typography } from '@mui/material';
import { useEffect, useState } from 'react';
import { useParams } from 'react-router';

import { fetchReporting, REPORTING_URI } from '../../../../actions/reporting/reporting-actions';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { simpleCall } from '../../../../utils/Action';
import { type Reporting } from '../../../../utils/api-types';
import { useQueryParameter } from '../../../../utils/Environment';
import ReportingRenderPage from './ReportingRenderPage';
import ReportingRenderTheme from './ReportingRenderTheme';
import { retryWithBackoff } from './useReportingRenderData';

/**
 * Standalone (chrome-less) report render route:
 *
 *   /reporting/:reportingId/render?token=<generationToken>&format=pdf|html
 *
 * Consumed by (a) the headless Chromium PDF service, which waits for the
 * #reporting-render-ready marker before printing, and (b) the in-app detail
 * page as an embedded live preview. `token` authorizes cookie-less headless
 * captures and is forwarded to every data request; `format` is accepted for
 * forward-compatibility and does not change the rendering (the page is always
 * print-ready HTML - the PDF flavor is produced by printing it).
 */
const ReportingRender = () => {
  const { t } = useFormatter();
  const { reportingId } = useParams();
  const [token] = useQueryParameter(['token']);
  const [reporting, setReporting] = useState<Reporting | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    if (!reportingId) {
      setError(true);
      return undefined;
    }
    let cancelled = false;
    // With a generation token, bypass the standard action so the token reaches
    // the backend; the session-cookie preview path keeps the shared action.
    // Retried with backoff like every module query: the report definition
    // fetch must be just as resilient to transient failures as the data layer.
    const request = () => (token
      ? simpleCall(`${REPORTING_URI}/${reportingId}`, { params: { token } }, false)
      : fetchReporting(reportingId));
    retryWithBackoff(request, () => cancelled)
      .then((result) => {
        if (!cancelled) setReporting(result.data);
      })
      .catch(() => {
        if (!cancelled) setError(true);
      });
    return () => {
      cancelled = true;
    };
  }, [reportingId, token]);

  // The report itself could not load: still flip the readiness flag so the
  // headless capture terminates instead of waiting forever, and report the
  // failure through the section-error counter so the capture retries the
  // page instead of printing the error state.
  useEffect(() => {
    if (error) {
      window.OPENAEV_REPORT_SECTION_ERRORS = 1;
      window.OPENAEV_REPORT_READY = true;
    }
  }, [error]);

  if (error) {
    return (
      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '100vh',
        gap: 1,
      }}
      >
        <Typography variant="h1">{t('Report unavailable')}</Typography>
        <Typography sx={{ color: 'text.secondary' }}>
          {t('This report does not exist or you are not allowed to view it.')}
        </Typography>
        <div id="reporting-render-ready" style={{ display: 'none' }} />
      </Box>
    );
  }

  if (!reporting) {
    return <Loader />;
  }

  return (
    <ReportingRenderTheme branding={reporting.reporting_branding}>
      <ReportingRenderPage reporting={reporting} token={token} />
    </ReportingRenderTheme>
  );
};

export default ReportingRender;
