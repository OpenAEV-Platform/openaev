import { Box, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import { type ReportingModule } from '../../../../../utils/api-types';
import { type MitigationFinding, type MockMitigation } from '../../../atomic_testings/atomic_testing/MitigationCard';
import { buildRemediations } from '../../../atomic_testings/atomic_testing/mitigationMock';
import { ModuleEmpty, PrintChip } from './ModuleSection';

// ---------------------------------------------------------------------------------------------
// PROTOTYPE - MOCK DATA ONLY. Opt-in report section, next to Mitigations (not part of the default
// composition). A remediation removes the weakness behind an attack, a mitigation only stops or detects
// it: the two are separate sections. Reads only remediations that were already generated in the interface,
// never generates one. Each action lists the hosts the remediation must be applied on.
// `module_config.mock_state = "empty"` shows the empty state (prototype switch).
// ---------------------------------------------------------------------------------------------

interface RemediationGroup {
  action: string;
  findings: MitigationFinding[];
  hasAgent: boolean;
  platform?: string;
  // Hosts the action was not prevented on: where the remediation must be applied.
  hosts: string[];
}

interface Props { module: ReportingModule }

const GROUPS: RemediationGroup[] = [
  {
    action: 'LSASS credential dump',
    findings: [{ type: 'credentials' }],
    hasAgent: true,
    platform: 'Windows',
    hosts: ['WIN-DC01', 'WIN-FIN-07'],
  },
  {
    action: 'NetExec SMB - Share Listing',
    findings: [
      {
        type: 'share',
        value: 'C$',
      },
      {
        type: 'share',
        value: 'ADMIN$',
      },
    ],
    hasAgent: false,
    hosts: ['winterfell (10.0.0.7)'],
  },
  {
    action: 'Nmap portscan',
    findings: [
      {
        type: 'portscan',
        value: '445/tcp',
      },
      {
        type: 'portscan',
        value: '3389/tcp',
      },
    ],
    hasAgent: false,
    hosts: ['castelback (20.224.192.102)'],
  },
];

const CRITICALITY_RANK: Record<MockMitigation['criticality'], number> = {
  critical: 0,
  high: 1,
  medium: 2,
  low: 3,
};

const RemediationsModule: FunctionComponent<Props> = ({ module }) => {
  const theme = useTheme();
  const { t, n } = useFormatter();
  const includeCommands = module.module_config?.include_commands !== false;
  const isEmpty = module.module_config?.mock_state === 'empty';

  const riskColor = (risk: MockMitigation['risk'] | MockMitigation['criticality']) => {
    if (risk === 'critical' || risk === 'high') return theme.palette.error.main;
    if (risk === 'medium') return theme.palette.warning.main;
    return theme.palette.success.main;
  };

  const groups = isEmpty
    ? []
    : GROUPS.map(group => ({
        ...group,
        remediations: buildRemediations({
          actionName: group.action,
          findings: group.findings,
          hasAgent: group.hasAgent,
          platform: group.platform,
        }).sort((a, b) => CRITICALITY_RANK[a.criticality] - CRITICALITY_RANK[b.criticality]),
      }));

  if (groups.length === 0) {
    return <ModuleEmpty message={t('No remediation was generated for this report subject yet.')} />;
  }

  const hostCount = new Set(groups.flatMap(group => group.hosts)).size;

  return (
    <Box>
      <Typography sx={{
        fontSize: 12,
        color: 'text.secondary',
        marginBottom: 2,
      }}
      >
        {`${n(groups.length)} ${groups.length === 1 ? t('action has remediations to apply on') : t('actions have remediations to apply on')} ${n(hostCount)} ${hostCount === 1 ? t('host.') : t('hosts.')}`}
      </Typography>

      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
      }}
      >
        {groups.map(group => (
          <Box
            key={group.action}
            sx={{
              border: `1px solid ${theme.palette.divider}`,
              borderRadius: 1,
              padding: 2,
              breakInside: 'avoid',
            }}
          >
            <Typography sx={{
              fontSize: 13,
              fontWeight: 600,
              marginBottom: 1,
            }}
            >
              {group.action}
            </Typography>

            <Typography sx={{
              fontSize: 10,
              letterSpacing: '0.08em',
              textTransform: 'uppercase',
              color: 'text.secondary',
              marginBottom: 0.5,
            }}
            >
              {t('Discovered findings')}
            </Typography>
            <Box sx={{
              display: 'flex',
              gap: 1,
              flexWrap: 'wrap',
              marginBottom: 1.5,
            }}
            >
              {[...new Set(group.findings.map(finding => finding.type))].map(type => (
                <PrintChip key={type} label={type} color={theme.palette.error.main} />
              ))}
            </Box>

            <Typography sx={{
              fontSize: 10,
              letterSpacing: '0.08em',
              textTransform: 'uppercase',
              color: 'text.secondary',
              marginBottom: 0.5,
            }}
            >
              {`${t('Affected hosts')} (${n(group.hosts.length)})`}
            </Typography>
            <Box sx={{
              display: 'flex',
              gap: 1,
              flexWrap: 'wrap',
              marginBottom: 1.5,
            }}
            >
              {group.hosts.map(host => (
                <PrintChip key={host} label={host} color={theme.palette.primary.main} />
              ))}
            </Box>

            {group.remediations.map(remediation => (
              <Box key={remediation.summary} sx={{ marginTop: 1.5 }}>
                <Box sx={{
                  display: 'flex',
                  gap: 1,
                  flexWrap: 'wrap',
                  marginBottom: 1,
                }}
                >
                  {remediation.controlType && (
                    <PrintChip label={remediation.controlType === 'patch' ? t('Patch') : t('Hardening')} color={theme.palette.primary.main} />
                  )}
                  <PrintChip label={`${t('Criticality')}: ${t(remediation.criticality)}`} color={riskColor(remediation.criticality)} />
                  <PrintChip label={`${t('Risk of applying')}: ${t(remediation.risk)}`} color={riskColor(remediation.risk)} />
                </Box>
                <Typography sx={{
                  fontSize: 12,
                  marginBottom: 1,
                }}
                >
                  {remediation.summary}
                </Typography>
                <Box
                  component="ol"
                  sx={{
                    margin: 0,
                    paddingLeft: 2.5,
                    fontSize: 12,
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 0.75,
                  }}
                >
                  {remediation.steps.map(step => (
                    <li key={step.text}>
                      {step.text}
                      {includeCommands && step.command && (
                        <Box sx={{
                          fontFamily: '"IBM Plex Mono", monospace',
                          fontSize: 10.5,
                          marginTop: 0.5,
                          padding: 0.75,
                          borderRadius: 0.5,
                          backgroundColor: theme.palette.action.hover,
                          overflowWrap: 'anywhere',
                        }}
                        >
                          {step.command}
                        </Box>
                      )}
                    </li>
                  ))}
                </Box>
                <Typography sx={{
                  fontSize: 11,
                  color: 'text.secondary',
                  marginTop: 1,
                }}
                >
                  {`${t('Impact')}: ${remediation.impact} ${t('Rollback')}: ${remediation.rollback}`}
                </Typography>
              </Box>
            ))}
          </Box>
        ))}
      </Box>

      <Typography sx={{
        fontSize: 10,
        color: 'text.disabled',
        marginTop: 2,
      }}
      >
        {t('AI-generated starting point. Nothing was changed in the environment.')}
      </Typography>
    </Box>
  );
};

export default RemediationsModule;
