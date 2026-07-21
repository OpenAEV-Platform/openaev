import {
  AttachFileOutlined,
  BugReportOutlined,
  CleaningServicesOutlined,
  CodeOutlined,
  DomainOutlined,
  GroupsOutlined,
  InfoOutlined,
  MemoryOutlined,
  PersonOutlined,
  TerminalOutlined,
  TuneOutlined,
  VerifiedOutlined,
} from '@mui/icons-material';
import {
  Box,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, useMemo } from 'react';

import { type AttackPatternHelper } from '../../../actions/attack_patterns/attackpattern-helper';
import type { DomainHelper } from '../../../actions/domains/domain-helper';
import type { DocumentHelper } from '../../../actions/helper';
import CodeBlock from '../../../components/common/overview/CodeBlock';
import Field from '../../../components/common/overview/Field';
import KeyValueChip from '../../../components/common/overview/KeyValueChip';
import Section from '../../../components/common/overview/Section';
import { useFormatter } from '../../../components/i18n';
import ItemSecurityPlatformType from '../../../components/ItemSecurityPlatformType';
import ItemTags from '../../../components/ItemTags';
import PlatformIcon from '../../../components/PlatformIcon';
import { useHelper } from '../../../store';
import {
  type AttackPattern,
  type Command,
  type DnsResolution,
  type Domain,
  type Executable,
  type FileDrop,
  type Payload as PayloadType,
  type PayloadArgument,
  type PayloadPrerequisite,
  type ThreatArsenalAction,
} from '../../../utils/api-types';
import { TO_CLASSIFY } from '../../../utils/domains/domainUtils';
import expectationIconByType, { expectationTypeColor } from '../common/ExpectationIconByType';
import { isTechnicalExpectation } from '../common/injects/expectations/ExpectationUtils';
import InjectIcon from '../common/injects/InjectIcon';
import DocumentType from '../components/documents/DocumentType';
import PayloadStatusComponent from '../payloads/PayloadStatusComponent';
import { getStatusColor, getStatusLabel } from './threatArsenalStatusUtils';

// Human labels for the predefined expectation types carried by a payload/contract.
const EXPECTATION_TYPE_LABELS: Record<string, string> = {
  PREVENTION: 'Prevention',
  DETECTION: 'Detection',
  VULNERABILITY: 'Vulnerability',
  MANUAL: 'Manual',
  ARTICLE: 'Article',
  CHALLENGE: 'Challenge',
};

type ExpectationType = 'ARTICLE' | 'CHALLENGE' | 'MANUAL' | 'PREVENTION' | 'DETECTION' | 'VULNERABILITY';

interface Props {
  action: ThreatArsenalAction;
  payload: PayloadType | null;
  // Predefined expectation types declared by the contract (payload- or
  // injector-based), passed separately so contracts without a payload still
  // render an Expectations section.
  expectations?: ExpectationType[];
  // Security platform types expected to fulfil each technical expectation
  // (empty/absent = any security platform).
  expectedSecurityPlatforms?: Record<string, string[]>;
  loading: boolean;
}

const ThreatArsenalActionOverview: FunctionComponent<Props> = ({
  action,
  payload,
  expectations,
  expectedSecurityPlatforms,
  loading,
}) => {
  const { t, tPick, nsdt } = useFormatter();
  const theme = useTheme();

  const { attackPatternsMap, documentsMap, allDomains } = useHelper(
    (helper: AttackPatternHelper & DocumentHelper & DomainHelper) => ({
      attackPatternsMap: helper.getAttackPatternsMap(),
      documentsMap: helper.getDocumentsMap(),
      allDomains: helper.getDomains(),
    }),
  );

  const attackPatternIds = action.action_attack_patterns_ids ?? [];
  const attackPatterns = useMemo(
    () => attackPatternIds.map(id => attackPatternsMap[id]).filter(Boolean) as AttackPattern[],
    [attackPatternIds, attackPatternsMap],
  );

  const domains: Domain[] = useMemo(() => {
    const ids = action.action_domains_ids ?? [];
    return (allDomains as Domain[]).filter(
      (d: Domain) => ids.includes(d.domain_id) && d.domain_name !== TO_CLASSIFY,
    );
  }, [action.action_domains_ids, allDomains]);

  const primaryDomain = domains[0];
  const accent = primaryDomain?.domain_color ?? theme.palette.primary.main;
  const status = action.action_payload?.payload_status ?? payload?.payload_status;
  const statusColor = status ? getStatusColor(theme, status) : undefined;
  const statusLabel = getStatusLabel(status);
  const name = tPick(action.action_labels);
  const description = payload?.payload_description ?? '';
  const platforms = payload?.payload_platforms ?? action.action_platforms ?? [];

  const getAttackCommand = (p: PayloadType | null): string => {
    if (!p) return '';
    switch (p.payload_type) {
      case 'Command':
        return (p as Command).command_content || '';
      case 'DnsResolution':
        return (p as DnsResolution).dns_resolution_hostname || '';
      case 'FileDrop':
        return (p as FileDrop).file_drop_file || '';
      case 'Executable':
        return (p as Executable).executable_file || '';
      default:
        return '';
    }
  };

  const getArgumentContent = (argument: PayloadArgument): string => {
    if (argument?.type === 'document' && documentsMap?.[argument.default_value]) {
      return documentsMap[argument.default_value].document_name;
    }
    return argument.default_value;
  };

  const attackCommand = getAttackCommand(payload);
  const commandExecutor = payload?.payload_type === 'Command'
    ? (payload as Command).command_executor
    : undefined;

  // Predefined expectations declared by the payload/contract, with the security
  // platform types expected to fulfil each technical one (empty = any platform).
  const expectationTypes = expectations ?? payload?.payload_expectations ?? [];
  const expectedPlatforms: Record<string, string[]>
    = expectedSecurityPlatforms ?? payload?.payload_expected_security_platforms ?? {};

  // The author is the payload's author (a person, team or organization) when
  // set. It can legitimately be absent - such actions are shown with a dash, not
  // a made-up default - and can be filtered via the sidebar "No author" facet.
  const authorName = action.action_author_name;
  const authorType = action.action_author_type;
  const AuthorIcon = (() => {
    switch (authorType) {
      case 'team':
        return GroupsOutlined;
      case 'organization':
        return DomainOutlined;
      case 'user':
        return PersonOutlined;
      default:
        return VerifiedOutlined;
    }
  })();

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 2,
    }}
    >
      <Box
        sx={{
          position: 'relative',
          borderRadius: 1,
          border: `1px solid ${theme.palette.divider}`,
          overflow: 'hidden',
          background: `linear-gradient(135deg, ${alpha(accent, 0.18)} 0%, ${alpha(accent, 0.04)} 60%, transparent 100%)`,
        }}
      >
        <Box sx={{
          display: 'flex',
          gap: 2,
          padding: 2,
          alignItems: 'flex-start',
        }}
        >
          <Box
            sx={{
              flexShrink: 0,
              width: 56,
              height: 56,
              borderRadius: 1,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              backgroundColor: theme.palette.background.paper,
              border: `1px solid ${alpha(accent, 0.4)}`,
              boxShadow: `0 4px 12px -4px ${alpha(accent, 0.4)}`,
            }}
          >
            <InjectIcon
              type={
                action.action_payload != null
                  ? action.action_payload.payload_collector_type ?? action.action_payload.payload_type
                  : action.action_injector_type
              }
              isPayload={action.action_payload != null}
              variant="list"
            />
          </Box>

          <Box sx={{
            flex: 1,
            minWidth: 0,
          }}
          >
            <Box sx={{
              display: 'flex',
              alignItems: 'flex-start',
              gap: 1.5,
              flexWrap: 'wrap',
            }}
            >
              <Typography
                variant="h6"
                sx={{
                  fontWeight: 600,
                  fontSize: 18,
                  lineHeight: 1.3,
                  flex: 1,
                  margin: 0,
                  minWidth: 0,
                  wordBreak: 'break-word',
                }}
              >
                {name || '-'}
              </Typography>
              {statusLabel && statusColor && (
                <Box
                  sx={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 0.5,
                    paddingInline: 1,
                    paddingBlock: 0.25,
                    borderRadius: 1,
                    backgroundColor: alpha(statusColor, 0.18),
                    color: statusColor,
                    border: `1px solid ${alpha(statusColor, 0.45)}`,
                    fontSize: 10.5,
                    fontWeight: 700,
                    letterSpacing: '0.04em',
                    textTransform: 'uppercase',
                    flexShrink: 0,
                  }}
                >
                  <Box
                    aria-hidden
                    sx={{
                      width: 6,
                      height: 6,
                      borderRadius: '50%',
                      backgroundColor: statusColor,
                      boxShadow: `0 0 6px ${alpha(statusColor, 0.8)}`,
                    }}
                  />
                  {t(statusLabel)}
                </Box>
              )}
            </Box>

            {description && (
              <Typography
                variant="body2"
                sx={{
                  color: 'text.secondary',
                  marginTop: 1,
                  lineHeight: 1.55,
                }}
              >
                {description}
              </Typography>
            )}

            <Box sx={{
              display: 'flex',
              alignItems: 'center',
              flexWrap: 'wrap',
              gap: 1,
              marginTop: 1.5,
            }}
            >
              {domains.map((domain) => {
                const domainColor = domain.domain_color ?? theme.palette.primary.main;
                return (
                  <Box
                    key={domain.domain_id}
                    sx={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      paddingInline: 1,
                      paddingBlock: 0.25,
                      borderRadius: 1,
                      fontSize: 10.5,
                      fontWeight: 600,
                      letterSpacing: '0.04em',
                      textTransform: 'uppercase',
                      borderColor: alpha(domainColor, 0.5),
                      border: '1px solid',
                      color: domainColor,
                      backgroundColor: alpha(domainColor, 0.08),
                    }}
                  >
                    {domain.domain_name}
                  </Box>
                );
              })}
              {action.injector_contract_updated_at && (
                <Typography variant="caption" sx={{ color: 'text.disabled' }}>
                  ·
                  {' '}
                  {t('Updated')}
                  {' '}
                  {nsdt(action.injector_contract_updated_at)}
                </Typography>
              )}
            </Box>
          </Box>
        </Box>
      </Box>

      <Section title={t('Overview')} icon={<InfoOutlined fontSize="small" />}>
        <Box sx={{
          display: 'grid',
          gridTemplateColumns: {
            xs: '1fr',
            md: 'repeat(2, minmax(0, 1fr))',
          },
          gap: 2,
        }}
        >
          <Field label="Platforms">
            {platforms.length > 0
              ? (
                  <Box sx={{
                    display: 'flex',
                    gap: 1,
                    flexWrap: 'wrap',
                    alignItems: 'center',
                  }}
                  >
                    {platforms.map(platform => (
                      <Box
                        key={platform}
                        sx={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: 0.5,
                          paddingBlock: 0.25,
                          paddingInline: 0.75,
                          borderRadius: 1,
                          border: `1px solid ${theme.palette.divider}`,
                          backgroundColor: alpha(theme.palette.background.paper, 0.4),
                        }}
                      >
                        <PlatformIcon platform={platform} width={14} />
                        <Typography variant="caption" sx={{ fontWeight: 500 }}>{t(platform)}</Typography>
                      </Box>
                    ))}
                  </Box>
                )
              : (
                  <Typography variant="body2" sx={{ color: 'text.disabled' }}>—</Typography>
                )}
          </Field>

          <Field label="Type">
            {(() => {
              if (payload?.payload_type) {
                return <KeyValueChip label={t('Type')} value={t(payload.payload_type)} />;
              }
              if (!action.action_injector_type) {
                return <Typography variant="body2" sx={{ color: 'text.disabled' }}>—</Typography>;
              }
              return (
                <Box sx={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 1,
                }}
                >
                  <InjectIcon
                    variant="list"
                    type={action.action_injector_type}
                    isPayload={false}
                  />
                  <Typography variant="body2">{action.action_injector_type}</Typography>
                </Box>
              );
            })()}
          </Field>

          {payload?.payload_execution_arch && (
            <Field label="Architecture">
              <Box sx={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 0.75,
              }}
              >
                <MemoryOutlined fontSize="small" sx={{ color: 'text.secondary' }} />
                <Typography variant="body2">{t(payload.payload_execution_arch)}</Typography>
              </Box>
            </Field>
          )}

          {payload?.payload_external_id && (
            <Field label="External Id">
              <Typography variant="body2" sx={{ fontFamily: 'Consolas, monaco, monospace' }}>
                {payload.payload_external_id}
              </Typography>
            </Field>
          )}

          <Field label="Tags">
            {(action.action_tags_ids?.length ?? 0) > 0
              ? <ItemTags variant="reduced-view" tags={action.action_tags_ids} />
              : <Typography variant="body2" sx={{ color: 'text.disabled' }}>—</Typography>}
          </Field>

          <Field label="Author">
            {authorName
              ? (
                  <Box sx={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 0.75,
                  }}
                  >
                    <AuthorIcon fontSize="small" sx={{ color: 'text.secondary' }} />
                    <Typography variant="body2">{authorName}</Typography>
                  </Box>
                )
              : <Typography variant="body2" sx={{ color: 'text.disabled' }}>—</Typography>}
          </Field>
        </Box>
      </Section>

      {attackPatterns.length > 0 && (
        <Section title={t('Attack patterns')} icon={<BugReportOutlined fontSize="small" />}>
          <Box sx={{
            display: 'flex',
            flexWrap: 'wrap',
            gap: 1,
          }}
          >
            {attackPatterns.map(ap => (
              <Tooltip
                key={ap.attack_pattern_id}
                title={`[${ap.attack_pattern_external_id}] ${ap.attack_pattern_name}`}
              >
                <Box
                  sx={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    paddingBlock: 0.5,
                    paddingInline: 1,
                    borderRadius: 1,
                    border: `1px solid ${alpha(theme.palette.primary.main, 0.4)}`,
                    backgroundColor: alpha(theme.palette.primary.main, 0.08),
                    color: theme.palette.primary.main,
                    fontSize: 11.5,
                    fontWeight: 500,
                    fontFamily: 'Consolas, monaco, monospace',
                    maxWidth: 280,
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                  }}
                >
                  [
                  {ap.attack_pattern_external_id}
                  ]
                  {' '}
                  {ap.attack_pattern_name}
                </Box>
              </Tooltip>
            ))}
          </Box>
        </Section>
      )}

      {expectationTypes.length > 0 && (
        <Section title={t('Expectations')} icon={<VerifiedOutlined fontSize="small" />}>
          <Box sx={{
            display: 'flex',
            flexDirection: 'column',
            gap: 1,
          }}
          >
            {expectationTypes.map((type) => {
              // Distinct name: `platforms` at component level holds the endpoint platforms.
              const expectedPlatformTypes = expectedPlatforms[type] ?? [];
              const technical = isTechnicalExpectation(type);
              return (
                <Box
                  key={type}
                  sx={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: 1.5,
                    padding: 1.25,
                    borderRadius: 1,
                    border: `1px solid ${theme.palette.divider}`,
                    backgroundColor: alpha(theme.palette.background.paper, 0.4),
                  }}
                >
                  <Box sx={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 1,
                    minWidth: 0,
                  }}
                  >
                    <Box
                      aria-hidden
                      sx={{
                        width: 28,
                        height: 28,
                        flexShrink: 0,
                        borderRadius: 1,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        color: expectationTypeColor(type),
                        backgroundColor: alpha(expectationTypeColor(type), 0.12),
                      }}
                    >
                      {expectationIconByType(type)}
                    </Box>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {t(EXPECTATION_TYPE_LABELS[type] ?? type)}
                    </Typography>
                  </Box>
                  <Box sx={{
                    display: 'flex',
                    flexWrap: 'wrap',
                    gap: 0.5,
                    justifyContent: 'flex-end',
                  }}
                  >
                    {(() => {
                      if (!technical) {
                        return (
                          <Typography variant="caption" sx={{ color: 'text.disabled' }}>
                            {t('Human validation')}
                          </Typography>
                        );
                      }
                      if (expectedPlatformTypes.length === 0) {
                        return (
                          <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                            {t('Any security platform')}
                          </Typography>
                        );
                      }
                      return expectedPlatformTypes.map(platform => (
                        <ItemSecurityPlatformType key={platform} type={platform} />
                      ));
                    })()}
                  </Box>
                </Box>
              );
            })}
          </Box>
        </Section>
      )}

      {loading && action.action_payload && (
        <Section title={t('Execution')} icon={<TerminalOutlined fontSize="small" />}>
          <Skeleton variant="rectangular" height={120} animation="wave" sx={{ borderRadius: 1 }} />
        </Section>
      )}

      {payload && (
        <Section
          title={t('Execution')}
          icon={<TerminalOutlined fontSize="small" />}
          action={commandExecutor
            ? <KeyValueChip label={t('Executor')} value={commandExecutor} />
            : null}
        >
          {payload.payload_type === 'Command' && (
            <CodeBlock content={attackCommand} language={t('Attack command')} />
          )}
          {payload.payload_type === 'DnsResolution' && (
            <Field label="Hostname">
              <Typography variant="body2" sx={{ fontFamily: 'Consolas, monaco, monospace' }}>
                {(payload as DnsResolution).dns_resolution_hostname || '—'}
              </Typography>
            </Field>
          )}
          {documentsMap && payload.payload_type === 'FileDrop' && (
            <Field label="File to drop">
              <Box sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 1,
              }}
              >
                <AttachFileOutlined fontSize="small" sx={{ color: 'text.secondary' }} />
                <Typography variant="body2" sx={{ fontWeight: 500 }}>
                  {documentsMap[(payload as FileDrop).file_drop_file]?.document_name ?? '—'}
                </Typography>
                <DocumentType
                  type={documentsMap[(payload as FileDrop).file_drop_file]?.document_type}
                  variant="list"
                />
              </Box>
            </Field>
          )}
          {documentsMap && payload.payload_type === 'Executable' && (
            <Field label="Executable file">
              <Box sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 1,
              }}
              >
                <AttachFileOutlined fontSize="small" sx={{ color: 'text.secondary' }} />
                <Typography variant="body2" sx={{ fontWeight: 500 }}>
                  {documentsMap[(payload as Executable).executable_file]?.document_name ?? '—'}
                </Typography>
                <DocumentType
                  type={documentsMap[(payload as Executable).executable_file]?.document_type}
                  variant="list"
                />
              </Box>
            </Field>
          )}
        </Section>
      )}

      {payload && (payload.payload_arguments?.length ?? 0) > 0 && (
        <Section title={t('Arguments')} icon={<TuneOutlined fontSize="small" />}>
          <Table
            size="small"
            sx={{
              '& .MuiTableCell-root': {
                fontSize: 12,
                borderColor: theme.palette.divider,
              },
            }}
          >
            <TableHead>
              <TableRow>
                <TableCell sx={{
                  fontWeight: 700,
                  textTransform: 'uppercase',
                  fontSize: 10.5,
                  color: 'text.secondary',
                  letterSpacing: '0.06em',
                }}
                >
                  {t('Type')}
                </TableCell>
                <TableCell sx={{
                  fontWeight: 700,
                  textTransform: 'uppercase',
                  fontSize: 10.5,
                  color: 'text.secondary',
                  letterSpacing: '0.06em',
                }}
                >
                  {t('Key')}
                </TableCell>
                <TableCell sx={{
                  fontWeight: 700,
                  textTransform: 'uppercase',
                  fontSize: 10.5,
                  color: 'text.secondary',
                  letterSpacing: '0.06em',
                }}
                >
                  {t('Default value')}
                </TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {payload.payload_arguments?.map((arg: PayloadArgument) => (
                <TableRow key={arg.key}>
                  <TableCell>
                    <KeyValueChip label="" value={arg.type} />
                  </TableCell>
                  <TableCell sx={{
                    fontFamily: 'Consolas, monaco, monospace',
                    fontWeight: 500,
                  }}
                  >
                    {arg.key}
                  </TableCell>
                  <TableCell>
                    <Box
                      component="code"
                      sx={{
                        display: 'inline-block',
                        backgroundColor: theme.palette.background.accent,
                        paddingInline: 0.75,
                        paddingBlock: 0.25,
                        borderRadius: 0.5,
                        fontSize: 11.5,
                      }}
                    >
                      {getArgumentContent(arg)}
                    </Box>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Section>
      )}

      {payload && (payload.payload_prerequisites?.length ?? 0) > 0 && (
        <Section title={t('Prerequisites')} icon={<CodeOutlined fontSize="small" />}>
          <Box sx={{
            display: 'flex',
            flexDirection: 'column',
            gap: 1.5,
          }}
          >
            {payload.payload_prerequisites?.map((prereq: PayloadPrerequisite, idx) => (
              <Box
                key={`${prereq.executor}-${idx}`}
                sx={{
                  border: `1px solid ${theme.palette.divider}`,
                  borderRadius: 1,
                  padding: 1.5,
                  backgroundColor: alpha(theme.palette.background.paper, 0.4),
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 1,
                }}
              >
                {prereq.executor && (
                  <KeyValueChip label={t('Executor')} value={prereq.executor} />
                )}
                {prereq.get_command && (
                  <Field label="Get command">
                    <CodeBlock content={prereq.get_command} />
                  </Field>
                )}
                {prereq.check_command && (
                  <Field label="Check command">
                    <CodeBlock content={prereq.check_command} />
                  </Field>
                )}
              </Box>
            ))}
          </Box>
        </Section>
      )}

      {payload && (payload.payload_cleanup_command || payload.payload_cleanup_executor) && (
        <Section
          title={t('Cleanup')}
          icon={<CleaningServicesOutlined fontSize="small" />}
          collapsible
          defaultCollapsed
          action={payload.payload_cleanup_executor
            ? <KeyValueChip label={t('Executor')} value={payload.payload_cleanup_executor} />
            : null}
        >
          {payload.payload_cleanup_command
            ? <CodeBlock content={payload.payload_cleanup_command} language={t('Cleanup command')} />
            : <Typography variant="body2" sx={{ color: 'text.disabled' }}>—</Typography>}
        </Section>
      )}

      {!payload && !loading && !action.action_payload && (
        <Section title={t('Payload status')} icon={<InfoOutlined fontSize="small" />}>
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1,
          }}
          >
            <PayloadStatusComponent status={undefined} />
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
              {t('This action does not have a payload attached.')}
            </Typography>
          </Box>
        </Section>
      )}
    </Box>
  );
};

export default ThreatArsenalActionOverview;
