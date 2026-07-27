import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode, useEffect, useState } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type Reporting, type ReportingModule } from '../../../../utils/api-types';
import AttackPathsModule from './modules/AttackPathsModule';
import CoverModule from './modules/CoverModule';
import CustomMarkdownModule from './modules/CustomMarkdownModule';
import ExecutiveSummaryModule from './modules/ExecutiveSummaryModule';
import FailedExpectationsModule from './modules/FailedExpectationsModule';
import FindingsModule from './modules/FindingsModule';
import MitreCoverageModule from './modules/MitreCoverageModule';
import { ModuleSection } from './modules/ModuleSection';
import ResultsBreakdownModule from './modules/ResultsBreakdownModule';
import ScoreTrendsModule from './modules/ScoreTrendsModule';
import SecurityDomainsModule from './modules/SecurityDomainsModule';
import SubjectDetailsModule from './modules/SubjectDetailsModule';
import { moduleTitle, TIME_RANGE_LABELS } from './reportingRenderLabels';
import resolveReportingLogo from './reportingRenderLogo';
import useReportingRenderData from './useReportingRenderData';

/**
 * Print-ready A4 layout of a report: branded cover, table of contents, one
 * section per configured module, and a running footer.
 *
 * Print notes (validated against Chromium, the engine of the headless PDF
 * service):
 * - The document owns ALL page geometry: both the printer margins and the
 *   `@page` margins are ZERO, so full-bleed elements (cover, running footer,
 *   separator rules) genuinely reach the paper edges. Horizontal gutters come
 *   from the body padding; the per-page vertical rhythm comes from the
 *   repeating table spacers below.
 * - The body flows inside a <table>: Chromium repeats <thead>/<tfoot> on every
 *   printed page AND reserves their layout space, which is the only reliable
 *   way to keep flowing content from colliding with the fixed running footer
 *   once the @page margins are gone.
 * - The running footer uses `position: fixed; bottom: 0`, which Chromium
 *   repeats on EVERY printed page. CSS counters (`counter(page)`) only work in
 *   @page margin boxes, which Chromium does not support for arbitrary content,
 *   so the footer intentionally has no page numbers.
 * - Charts render at fixed pixel sizes with animations disabled (PrintChart)
 *   so the capture is deterministic.
 */

declare global {
  interface Window { OPENAEV_REPORT_READY?: boolean }
}

interface Props {
  reporting: Reporting;
  token: string | null;
}

const ReportingRenderPage: FunctionComponent<Props> = ({ reporting, token }) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const data = useReportingRenderData(reporting, token);
  const [ready, setReady] = useState(false);

  const logoUrl = resolveReportingLogo(theme, reporting.reporting_branding);
  const timeRangeLabel = t(TIME_RANGE_LABELS[reporting.reporting_time_range]);

  // Readiness contract for the headless PDF service: once every module query
  // settled (success OR error - an error block is a rendered state) let the
  // browser paint twice, then flip the flag and mount the marker node.
  useEffect(() => {
    if (!data.allSettled || ready) return undefined;
    let cancelled = false;
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        if (cancelled) return;
        window.OPENAEV_REPORT_READY = true;
        setReady(true);
      });
    });
    return () => {
      cancelled = true;
    };
  }, [data.allSettled, ready]);

  const coverModule = reporting.reporting_modules.find(module => module.module_type === 'COVER');
  const bodyModules = reporting.reporting_modules.filter(module => module.module_type !== 'COVER');

  const renderModuleContent = (module: ReportingModule): ReactNode => {
    switch (module.module_type) {
      case 'EXECUTIVE_SUMMARY':
        return <ExecutiveSummaryModule posture={data.posture} injectCount={data.injectCount} />;
      case 'SUBJECT_DETAILS':
        return <SubjectDetailsModule reporting={reporting} subject={data.subject} />;
      case 'MITRE_COVERAGE':
        return <MitreCoverageModule mitre={data.mitre} />;
      case 'RESULTS_BREAKDOWN':
        return <ResultsBreakdownModule posture={data.posture} />;
      case 'SECURITY_DOMAINS':
        return <SecurityDomainsModule domains={data.securityDomains} />;
      case 'SCORE_TRENDS':
        return <ScoreTrendsModule trends={data.trends} />;
      case 'FAILED_EXPECTATIONS':
        return <FailedExpectationsModule failedExpectations={data.failedExpectations} />;
      case 'FINDINGS':
        return <FindingsModule findings={data.findings} />;
      case 'ATTACK_PATHS':
        return <AttackPathsModule attackPaths={data.attackPaths} />;
      case 'CUSTOM_MARKDOWN':
        return <CustomMarkdownModule module={module} />;
      default:
        return null;
    }
  };

  return (
    <Box
      className="reporting-render-root"
      sx={{
        backgroundColor: 'background.default',
        color: 'text.primary',
        minHeight: '100vh',
      }}
    >
      {/* Paged-media rules: kept as a raw style tag because @page and @media
          print cannot be expressed through the sx prop. */}
      <style>
        {`
          /* ZERO margins everywhere (printer AND @page): the document owns every
             band of the paper, so full-bleed elements really touch the edges and
             dark themes can never be framed by unpainted white paper. */
          @page {
            size: A4 portrait;
            margin: 0;
            background: ${theme.palette.background.default};
          }
          .reporting-render-root { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
          .reporting-render-root * { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
          .reporting-module { break-inside: avoid; page-break-inside: avoid; }
          .reporting-cover { break-after: page; page-break-after: always; }
          .reporting-toc { break-after: page; page-break-after: always; }
          .reporting-print-footer { display: none; }
          .reporting-paged { width: 100%; border-collapse: collapse; }
          /* Direct children only: a bare ".reporting-paged td" would also hit
             the module data tables nested in the body cell and outrank their
             emotion classes, collapsing every ReportCell padding to zero. */
          .reporting-paged > thead > tr > td,
          .reporting-paged > tbody > tr > td,
          .reporting-paged > tfoot > tr > td { padding: 0; }
          /* The paged spacers only exist for print; they are inert on screen. */
          .reporting-page-top-spacer, .reporting-page-bottom-spacer { display: none; }
          /* HTML flavor has no page breaks: give the cover the breathing room the
             break-after provides in print, and smooth-scroll the TOC anchors. */
          @media screen {
            html { scroll-behavior: smooth; }
            .reporting-cover-wrap { max-width: 210mm; margin: 0 auto; padding: 40px 40px 0; }
            .reporting-cover { margin-bottom: 24px; }
            .reporting-toc-entry:hover .reporting-toc-title { color: ${theme.palette.primary.main}; }
          }
          @media print {
            html, body { background: ${theme.palette.background.default} !important; margin: 0 !important; }
            /* Full-bleed cover: square corners, fills the whole first page.
               (296mm instead of 297mm: an exact page height plus rounding can
               spill a blank page; the sliver left over is painted by @page.) */
            .reporting-cover-wrap { max-width: none !important; margin: 0 !important; padding: 0 !important; }
            .reporting-cover {
              border-radius: 0 !important;
              box-sizing: border-box;
              min-height: 296mm;
              padding: 14mm 15mm 18mm !important;
            }
            /* Horizontal gutters are ours now; vertical rhythm comes from the
               repeating table spacers, not from padding. */
            .reporting-sheet { max-width: none !important; padding: 0 15mm !important; }
            /* Chromium repeats thead/tfoot on every printed page AND reserves
               their space: top gap + footer band, with zero overlap risk. */
            .reporting-page-top-spacer { display: block; height: 15mm; }
            .reporting-page-bottom-spacer { display: block; height: 18mm; }
            /* position: fixed repeats on every printed page in Chromium; with
               zero page margins it reaches the true paper edges, so the footer
               rule spans 100% of the sheet. */
            .reporting-print-footer { display: flex !important; position: fixed; bottom: 0; left: 0; right: 0; }
          }
        `}
      </style>

      {coverModule && (
        <Box className="reporting-cover-wrap">
          <CoverModule
            reporting={reporting}
            subject={data.subject}
            logoUrl={logoUrl}
          />
        </Box>
      )}

      {/* The body flows inside a table so its thead/tfoot spacers repeat on
          every printed page (see print notes above). */}
      <table className="reporting-paged">
        <thead>
          <tr>
            <td><div className="reporting-page-top-spacer" /></td>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>
              <Box
                className="reporting-sheet"
                sx={{
                  maxWidth: '210mm',
                  margin: '0 auto',
                  padding: '32px 40px 48px',
                }}
              >
                {bodyModules.length > 1 && (
                  <Box
                    className="reporting-toc reporting-module"
                    sx={{ marginBottom: 6 }}
                  >
                    <Typography sx={{
                      fontFamily: '"Geologica", sans-serif',
                      fontSize: 20,
                      fontWeight: 600,
                      marginBottom: 0.5,
                    }}
                    >
                      {t('Table of contents')}
                    </Typography>
                    <Typography sx={{
                      fontSize: 11,
                      color: 'text.secondary',
                      letterSpacing: '0.06em',
                      textTransform: 'uppercase',
                      marginBottom: 2.5,
                    }}
                    >
                      {timeRangeLabel}
                    </Typography>
                    <Box sx={{
                      display: 'flex',
                      flexDirection: 'column',
                    }}
                    >
                      {/* Anchor entries: clickable in the HTML flavor (smooth
                          scroll) AND in the PDF (Chromium converts internal
                          anchors into in-document links). */}
                      {bodyModules.map((module, index) => (
                        <Box
                          // Modules have no id; order is the identity.
                          // eslint-disable-next-line react/no-array-index-key
                          key={index}
                          component="a"
                          href={`#reporting-section-${index + 1}`}
                          className="reporting-toc-entry"
                          sx={{
                            display: 'flex',
                            alignItems: 'baseline',
                            gap: 1.5,
                            paddingY: 1.25,
                            textDecoration: 'none',
                            color: 'inherit',
                            borderBottom: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
                          }}
                        >
                          <Typography sx={{
                            fontFamily: '"Geologica", sans-serif',
                            fontSize: 12,
                            fontWeight: 600,
                            color: 'primary.main',
                            width: 24,
                            flexShrink: 0,
                          }}
                          >
                            {String(index + 1).padStart(2, '0')}
                          </Typography>
                          <Typography
                            className="reporting-toc-title"
                            sx={{
                              fontSize: 13,
                              fontWeight: 500,
                            }}
                          >
                            {moduleTitle(module, t)}
                          </Typography>
                          {/* Dotted leader filling the gap toward the margin. */}
                          <Box sx={{
                            flex: 1,
                            borderBottom: `1px dotted ${alpha(theme.palette.text.primary, 0.25)}`,
                            transform: 'translateY(-3px)',
                          }}
                          />
                        </Box>
                      ))}
                    </Box>
                  </Box>
                )}

                {bodyModules.map((module, index) => (
                  <ModuleSection
                    // Modules have no id; order is the identity.
                    // eslint-disable-next-line react/no-array-index-key
                    key={index}
                    id={`reporting-section-${index + 1}`}
                    title={moduleTitle(module, t)}
                    subtitle={timeRangeLabel}
                  >
                    {renderModuleContent(module)}
                  </ModuleSection>
                ))}
              </Box>
            </td>
          </tr>
        </tbody>
        <tfoot>
          <tr>
            <td><div className="reporting-page-bottom-spacer" /></td>
          </tr>
        </tfoot>
      </table>

      {/* Running footer, repeated on every printed page (see print notes). */}
      <Box
        className="reporting-print-footer"
        sx={{
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 2,
          padding: '4px 15mm',
          backgroundColor: 'background.default',
          borderTop: `1px solid ${alpha(theme.palette.text.primary, 0.15)}`,
        }}
      >
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
        }}
        >
          {logoUrl && (
            <Box
              component="img"
              src={logoUrl}
              alt=""
              sx={{
                maxHeight: 14,
                maxWidth: 90,
                objectFit: 'contain',
              }}
            />
          )}
        </Box>
        <Typography sx={{
          fontSize: 9,
          color: 'text.secondary',
        }}
        >
          {reporting.reporting_name}
        </Typography>
      </Box>

      {/* Machine-readable readiness marker (Playwright waits for it). */}
      {ready && <div id="reporting-render-ready" style={{ display: 'none' }} />}
    </Box>
  );
};

export default ReportingRenderPage;
