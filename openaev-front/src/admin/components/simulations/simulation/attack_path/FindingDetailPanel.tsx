import { Close, InfoOutlined } from '@mui/icons-material';
import { Alert, Box, Button, Chip, IconButton, Paper, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';

import FindingIcon from '../../../../../components/FindingIcon';
import { useFormatter } from '../../../../../components/i18n';
import LogicNodeTooltip from '../../../chaining/logic/chaining_flow/NodeTooltip';
import graphTooltipSlotProps from '../../../chaining/logic/logic-graph/graphTooltipSlotProps';
import expectationIconByType from '../../../common/ExpectationIconByType';
import InjectFormSection from '../../../common/injects/form/InjectFormSection';

export interface ProducingAction {
  ref: string;
  contract: string;
  statusColor: string;
  statusLabel: string;
  subtitle: string;
}

export type ExpectationVerdict = 'success' | 'failed' | 'unknown';

export interface FindingExpectations {
  prevention?: ExpectationVerdict;
  detection?: ExpectationVerdict;
  vulnerability?: ExpectationVerdict;
}

interface Props {
  // Masked, display-ready finding value (secrets already hidden by the caller).
  value: string;
  type: string;
  endpointLabel: string;
  endpointSub?: string;
  // Prevention / detection / vulnerability verdicts for this finding (backend-provided).
  expectations?: FindingExpectations;
  actions: ProducingAction[];
  activeRef: string | null;
  onSelect: (ref: string) => void;
  onClose: () => void;
}

const EXPECTATION_ORDER: (keyof FindingExpectations)[] = ['prevention', 'detection', 'vulnerability'];

// Right-side panel describing one finding picked in the attack-path graph: its prevention/detection/
// vulnerability verdicts, what it is, on which endpoint it was found, and which action(s) produced it.
// Selecting a producing action opens the Result & Terminal panel next to it, so the finding, its path
// (highlighted on the map) and the raw execution result can all be inspected at once. All values are
// rendered as inert text (secrets are masked upstream); nothing here is injected as HTML.
const FindingDetailPanel = ({ value, type, endpointLabel, endpointSub, expectations, actions, activeRef, onSelect, onClose }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();

  // Colour a verdict per its expectation type: a successful prevention is green, a successful
  // detection is orange, a failed verdict is red, and an unknown one is muted.
  const verdictColor = (key: keyof FindingExpectations, verdict?: ExpectationVerdict): string => {
    if (verdict === 'success') {
      return key === 'detection' ? theme.palette.warning.main : theme.palette.success.main;
    }
    if (verdict === 'failed') {
      return theme.palette.error.main;
    }
    return theme.palette.text.disabled;
  };

  // Plain-language explanation of each expectation type, so a newcomer understands what the three
  // badges actually mean rather than facing an unlabelled icon row.
  const expectationHelp: Record<keyof FindingExpectations, string> = {
    prevention: t('Prevention checks whether a security control blocked or stopped this technique on the endpoint.'),
    detection: t('Detection checks whether a security control raised an alert on this technique on the endpoint.'),
    vulnerability: t('Vulnerability checks whether the endpoint was actually found vulnerable to this technique.'),
  };
  const verdictHelp = (verdict: ExpectationVerdict): string => {
    if (verdict === 'success') {
      return t('Result: the expectation was met (control effective / target vulnerable).');
    }
    if (verdict === 'failed') {
      return t('Result: the expectation was not met (control ineffective / target not vulnerable).');
    }
    return t('Result: not evaluated - no matching control result was reported for this finding.');
  };
  const cap = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);

  return (
    <Paper
      variant="outlined"
      style={{
        flex: 1,
        minWidth: 0,
        overflow: 'auto',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      {/* Header in the app Drawer language: verdict badges, the finding value as the title, its type
          as a chip with the shared FindingIcon, and a close control over the standard divider. The
          close sits in the first header row (badges when present, title otherwise), vertically
          centered on that row like every other attack-path panel. */}
      <Box sx={{
        padding: theme.spacing(2, 2.5, 1.5),
        borderBottom: `1px solid ${theme.palette.divider}`,
        flexShrink: 0,
      }}
      >
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
        }}
        >
          <Box sx={{
            flex: 1,
            minWidth: 0,
          }}
          >
            {expectations
              ? (
                  <Box sx={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 1,
                  }}
                  >
                    {EXPECTATION_ORDER.map((key) => {
                      const verdict = expectations[key] ?? 'unknown';
                      const label = `${t(cap(key))}: ${t(cap(verdict))}`;
                      const color = verdictColor(key, verdict);
                      return (
                        <Tooltip
                          key={key}
                          placement="top"
                          arrow
                          disableInteractive
                          slotProps={graphTooltipSlotProps}
                          title={(
                            <LogicNodeTooltip
                              eyebrow={t(cap(key))}
                              title={t(cap(verdict))}
                              description={`${expectationHelp[key]} ${verdictHelp(verdict)}`}
                              accentColor={color}
                            />
                          )}
                        >
                          <Box
                            component="span"
                            role="img"
                            aria-label={label}
                            sx={{
                              display: 'inline-flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              width: 28,
                              height: 28,
                              borderRadius: 1,
                              color,
                              background: alpha(color, 0.1),
                              boxShadow: `inset 0 0 12px ${alpha(color, 0.13)}`,
                            }}
                          >
                            {expectationIconByType(key, { color })}
                          </Box>
                        </Tooltip>
                      );
                    })}
                  </Box>
                )
              : (
                  <Typography
                    variant="h5"
                    sx={{
                      wordBreak: 'break-all',
                      margin: 0,
                    }}
                    title={value}
                  >
                    {value}
                  </Typography>
                )}
          </Box>
          <IconButton size="small" aria-label={t('Close')} onClick={onClose} sx={{ flexShrink: 0 }}>
            <Close fontSize="small" />
          </IconButton>
        </Box>
        {expectations && (
          <Typography
            variant="h5"
            sx={{
              wordBreak: 'break-all',
              margin: 0,
              mt: 1,
            }}
            title={value}
          >
            {value}
          </Typography>
        )}
        <Chip
          size="small"
          variant="outlined"
          icon={(
            <Box
              component="span"
              sx={{
                'display': 'inline-flex',
                'alignItems': 'center',
                '& .MuiSvgIcon-root': { fontSize: 14 },
              }}
            >
              <FindingIcon findingType={type} />
            </Box>
          )}
          label={type}
          sx={{ mt: 0.75 }}
        />
      </Box>

      <Box sx={{
        padding: theme.spacing(2, 2.5),
        display: 'flex',
        flexDirection: 'column',
        gap: 3,
      }}
      >
        <InjectFormSection title={t('Discovered on')}>
          <Box>
            <Typography variant="body2" noWrap title={endpointLabel}>{endpointLabel}</Typography>
            {endpointSub && (
              <Box sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 0.5,
              }}
              >
                <Typography variant="caption" color="text.secondary" noWrap sx={{ minWidth: 0 }}>{endpointSub}</Typography>
                {/* The IP/platform line is opaque on its own ("Unknown" reads as an error): an info
                    affordance spells out what it is and what an undetermined platform means. */}
                <Tooltip
                  placement="top"
                  arrow
                  disableInteractive
                  slotProps={graphTooltipSlotProps}
                  title={(
                    <LogicNodeTooltip
                      eyebrow={t('Endpoint')}
                      title={t('Where the finding was discovered')}
                      description={t('The host this finding was found on, shown as its IP address and platform (operating system family). "Unknown" means the platform could not be determined from the data collected during the run.')}
                    />
                  )}
                >
                  <InfoOutlined sx={{
                    fontSize: 13,
                    color: 'text.disabled',
                    flexShrink: 0,
                    cursor: 'help',
                  }}
                  />
                </Tooltip>
              </Box>
            )}
          </Box>
        </InjectFormSection>

        <InjectFormSection title={`${t('Discovered by')} (${actions.length})`}>
          <Box sx={{
            display: 'flex',
            flexDirection: 'column',
          }}
          >
            {actions.length === 0 && (
              <Alert severity="info">{t('No producing action found for this finding')}</Alert>
            )}
            {actions.map(a => (
              <Box
                key={a.ref}
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 1,
                  py: 0.75,
                  px: 0.5,
                  borderRadius: 1,
                  borderBottom: `1px solid ${theme.palette.divider}`,
                  borderLeft: a.ref === activeRef ? `2px solid ${theme.palette.primary.main}` : '2px solid transparent',
                  backgroundColor: a.ref === activeRef ? theme.palette.action.selected : undefined,
                  transition: theme.transitions.create(['background-color', 'border-color']),
                }}
              >
                <Box sx={{
                  minWidth: 0,
                  flex: 1,
                }}
                >
                  <Typography variant="body2" noWrap title={a.contract}>{a.contract}</Typography>
                  <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>
                    {a.subtitle}
                  </Typography>
                </Box>
                {/* Verdict pill in the shared StatusPill visual language, from the caller-resolved colour. */}
                <Box
                  component="span"
                  sx={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    paddingInline: 1,
                    paddingBlock: 0.25,
                    borderRadius: 1,
                    backgroundColor: alpha(a.statusColor, 0.08),
                    color: a.statusColor,
                    fontSize: 11,
                    fontWeight: 700,
                    letterSpacing: '0.04em',
                    textTransform: 'uppercase',
                    whiteSpace: 'nowrap',
                    flexShrink: 0,
                  }}
                >
                  {a.statusLabel}
                </Box>
                <Button
                  size="small"
                  variant="outlined"
                  color="primary"
                  onClick={() => onSelect(a.ref)}
                  sx={{ flexShrink: 0 }}
                >
                  {t('View')}
                </Button>
              </Box>
            ))}
          </Box>
        </InjectFormSection>
      </Box>
    </Paper>
  );
};

export default FindingDetailPanel;
