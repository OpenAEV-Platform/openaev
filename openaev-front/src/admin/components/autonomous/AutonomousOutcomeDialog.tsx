import { LaunchOutlined, VerifiedOutlined, WarningAmberOutlined } from '@mui/icons-material';
import { Alert, Box, Button, Chip, Dialog, DialogContent, DialogTitle, Divider, Link, Stack, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { Link as RouterLink } from 'react-router';

import { type AutonomousEvent } from '../../../actions/autonomous/autonomous-types';
import { useFormatter } from '../../../components/i18n';
import { EventMarkdown, sanitizeEventText } from './autonomousEventVisuals';

export type OutcomeKind = 'GAP' | 'PROOF';

// A finding linked to a proof, as serialized by the orchestrator into
// autonomous_event_data.findings. Kept permissive: the orchestrator authors
// prose + ids, so every field except a stable id is optional.
interface LinkedFinding {
  finding_id?: string;
  id?: string;
  type?: string;
  value?: string;
  label?: string;
  name?: string;
}

// A concrete platform action the operator can take to close a capability gap,
// serialized by the orchestrator into autonomous_event_data.next_steps /
// suggested_connectors. Rendered as an actionable checklist in the dialog.
interface NextStep {
  title?: string;
  label?: string;
  description?: string;
  short_description?: string;
  link?: string;
  url?: string;
  subscription_link?: string;
}

interface ParsedData {
  findings?: LinkedFinding[];
  next_steps?: (NextStep | string)[];
  suggested_connectors?: NextStep[];
  [key: string]: unknown;
}

// The orchestrator writes free JSON into autonomous_event_data; parse it
// defensively so a malformed blob degrades to "shown as raw text" instead of
// blanking the dialog.
const parseData = (raw?: string | null): {
  parsed?: ParsedData;
  rawFallback?: string;
} => {
  if (!raw) {
    return {};
  }
  try {
    const value = JSON.parse(raw);
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      return { parsed: value as ParsedData };
    }
    return { rawFallback: raw };
  } catch {
    return { rawFallback: raw };
  }
};

const findingId = (finding: LinkedFinding): string | undefined => finding.finding_id ?? finding.id;
const findingLabel = (finding: LinkedFinding): string =>
  finding.value ?? finding.label ?? finding.name ?? findingId(finding) ?? '';

const stepTitle = (step: NextStep | string): string =>
  (typeof step === 'string' ? step : (step.title ?? step.label ?? ''));
const stepDescription = (step: NextStep | string): string | undefined =>
  (typeof step === 'string' ? undefined : (step.description ?? step.short_description));
const stepLink = (step: NextStep | string): string | undefined =>
  (typeof step === 'string' ? undefined : (step.link ?? step.url ?? step.subscription_link));

interface Props {
  kind: OutcomeKind;
  event: AutonomousEvent | null;
  /** The run's simulation id, so linked findings deep-link to the Findings tab. */
  simulationId?: string | null;
  techniques: string[];
  cves: string[];
  onClose: () => void;
}

/**
 * Full-detail dialog for a capability gap or a proof-of-exploitation entry from
 * the autonomous cockpit. The cards on the Overview tab are clamped and
 * metadata-forward; this dialog is the drill-down: the untruncated narrative,
 * all technique / CVE tags, the raw structured metadata the orchestrator
 * attached, and - crucially - the actionable tail of each kind:
 *  - GAP: the concrete next steps in the platform to close the gap.
 *  - PROOF: the finding(s) the proof is backed by. A proof with no linked
 *    finding is surfaced as invalid, because there is no valid proof of
 *    exploitation without an associated finding.
 */
const AutonomousOutcomeDialog: FunctionComponent<Props> = ({
  kind,
  event,
  simulationId,
  techniques,
  cves,
  onClose,
}) => {
  const theme = useTheme();
  const { t, nsdt } = useFormatter();

  if (!event) {
    return null;
  }

  const tone = kind === 'PROOF' ? theme.palette.success.main : theme.palette.warning.main;
  const { parsed, rawFallback } = parseData(event.autonomous_event_data);
  const findings = parsed?.findings ?? [];
  const nextSteps = [...(parsed?.next_steps ?? []), ...(parsed?.suggested_connectors ?? [])];
  const hasTags = techniques.length > 0 || cves.length > 0;

  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth PaperProps={{ elevation: 1 }}>
      <DialogTitle sx={{
        display: 'flex',
        alignItems: 'flex-start',
        gap: 1.5,
      }}
      >
        <Box sx={{
          display: 'inline-flex',
          marginTop: '2px',
          color: tone,
        }}
        >
          {kind === 'PROOF' ? <VerifiedOutlined /> : <WarningAmberOutlined />}
        </Box>
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="h6" sx={{ margin: 0 }}>
            {event.autonomous_event_title ?? (kind === 'PROOF' ? t('Proof') : t('Capability gap'))}
          </Typography>
          {event.autonomous_event_created_at && (
            <Typography variant="caption" color="text.secondary">
              {nsdt(event.autonomous_event_created_at)}
            </Typography>
          )}
        </Box>
      </DialogTitle>
      <DialogContent>
        <Stack sx={{ gap: 2 }}>
          {hasTags && (
            <Stack sx={{
              flexDirection: 'row',
              flexWrap: 'wrap',
              gap: 0.5,
            }}
            >
              {cves.map(cve => (
                <Chip
                  key={cve}
                  label={cve}
                  size="small"
                  sx={{
                    borderRadius: 0.5,
                    color: theme.palette.error.main,
                    backgroundColor: alpha(theme.palette.error.main, 0.12),
                  }}
                />
              ))}
              {techniques.map(technique => (
                <Chip
                  key={technique}
                  label={technique}
                  size="small"
                  sx={{
                    borderRadius: 0.5,
                    color: theme.palette.info.main,
                    backgroundColor: alpha(theme.palette.info.main, 0.12),
                  }}
                />
              ))}
            </Stack>
          )}

          {sanitizeEventText(event.autonomous_event_content) && (
            <EventMarkdown
              content={sanitizeEventText(event.autonomous_event_content)}
              color="text.primary"
              fontSize="0.875rem"
            />
          )}

          {/* PROOF: the finding(s) that back this proof. No finding => not a valid proof. */}
          {kind === 'PROOF' && (
            <>
              <Divider textAlign="left">
                <Typography variant="overline" color="text.secondary">
                  {t('Associated findings')}
                </Typography>
              </Divider>
              {findings.length === 0 ? (
                <Alert severity="warning" icon={<WarningAmberOutlined />}>
                  {t('This proof has no associated finding. A proof of exploitation is only valid when backed by at least one finding.')}
                </Alert>
              ) : (
                <Stack sx={{ gap: 0.75 }}>
                  {findings.map((finding, index) => {
                    const id = findingId(finding);
                    const label = findingLabel(finding) || t('Finding');
                    return (
                      <Stack
                        key={id ?? index}
                        sx={{
                          flexDirection: 'row',
                          alignItems: 'center',
                          gap: 1,
                          padding: 1,
                          borderRadius: 1,
                          border: `1px solid ${alpha(theme.palette.success.main, 0.35)}`,
                          backgroundColor: alpha(theme.palette.success.main, 0.06),
                        }}
                      >
                        {finding.type && (
                          <Chip
                            label={finding.type}
                            size="small"
                            sx={{
                              borderRadius: 0.5,
                              color: theme.palette.success.main,
                              backgroundColor: alpha(theme.palette.success.main, 0.12),
                            }}
                          />
                        )}
                        <Typography
                          variant="body2"
                          sx={{
                            flex: 1,
                            minWidth: 0,
                            wordBreak: 'break-word',
                          }}
                        >
                          {label}
                        </Typography>
                        {simulationId && (
                          <Link
                            component={RouterLink}
                            to={`/admin/simulations/${simulationId}/findings`}
                            sx={{
                              display: 'inline-flex',
                              alignItems: 'center',
                            }}
                          >
                            <LaunchOutlined fontSize="small" />
                          </Link>
                        )}
                      </Stack>
                    );
                  })}
                </Stack>
              )}
            </>
          )}

          {/* GAP: concrete platform next steps to close the shortfall. */}
          {kind === 'GAP' && nextSteps.length > 0 && (
            <>
              <Divider textAlign="left">
                <Typography variant="overline" color="text.secondary">
                  {t('Next steps in the platform')}
                </Typography>
              </Divider>
              <Stack sx={{ gap: 0.75 }}>
                {nextSteps.map((step, index) => {
                  const title = stepTitle(step) || t('Enable a capability');
                  const description = stepDescription(step);
                  const link = stepLink(step);
                  return (
                    <Stack
                      key={index}
                      sx={{
                        gap: 0.25,
                        padding: 1,
                        borderRadius: 1,
                        border: `1px solid ${alpha(tone, 0.35)}`,
                        backgroundColor: alpha(tone, 0.06),
                      }}
                    >
                      <Stack sx={{
                        flexDirection: 'row',
                        alignItems: 'center',
                        gap: 1,
                      }}
                      >
                        <Typography
                          variant="subtitle2"
                          sx={{
                            margin: 0,
                            flex: 1,
                          }}
                        >
                          {title}
                        </Typography>
                        {link && (
                          <Link
                            href={link}
                            target="_blank"
                            rel="noopener noreferrer"
                            sx={{
                              display: 'inline-flex',
                              alignItems: 'center',
                            }}
                          >
                            <LaunchOutlined fontSize="small" />
                          </Link>
                        )}
                      </Stack>
                      {description && (
                        <Typography variant="caption" color="text.secondary" sx={{ whiteSpace: 'pre-wrap' }}>
                          {description}
                        </Typography>
                      )}
                    </Stack>
                  );
                })}
              </Stack>
            </>
          )}

          {/* Any remaining structured metadata the orchestrator attached, shown raw so nothing is
              silently hidden while the schema is still evolving. */}
          {rawFallback && (
            <Box
              component="pre"
              sx={{
                margin: 0,
                padding: 1,
                borderRadius: 1,
                fontSize: 11,
                overflowX: 'auto',
                backgroundColor: alpha(theme.palette.text.primary, 0.05),
              }}
            >
              {rawFallback}
            </Box>
          )}
        </Stack>
      </DialogContent>
      <Box sx={{
        display: 'flex',
        justifyContent: 'flex-end',
        padding: 2,
        paddingTop: 0,
      }}
      >
        <Button onClick={onClose}>{t('Close')}</Button>
      </Box>
    </Dialog>
  );
};

export default AutonomousOutcomeDialog;
