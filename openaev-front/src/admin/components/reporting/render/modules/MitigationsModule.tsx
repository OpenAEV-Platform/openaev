import { Box, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import { type ReportingModule } from '../../../../../utils/api-types';
import { type MockMitigation } from '../../../atomic_testings/atomic_testing/MitigationCard';
import { buildMitigation, MOCK_MITIGATIONS } from '../../../atomic_testings/atomic_testing/mitigationMock';
import { ModuleEmpty, PrintChip } from './ModuleSection';

// ---------------------------------------------------------------------------------------------
// PROTOTYPE - MOCK DATA ONLY. Opt-in report section (not part of the default composition).
// Reads only mitigations that were already generated in the interface: it never generates one,
// so opening or scheduling a report never triggers an AI call, and a subject with no mitigation
// gives an empty section. Each mitigation appears ONCE, with the actions and the findings it
// relates to and the executions it closes (identical inputs share one stored mitigation).
// `module_config.mock_state = "empty"` shows the empty state (prototype switch).
// ---------------------------------------------------------------------------------------------

interface RelatedFinding {
  type: string;
  value: string;
  action: string;
  execution: string;
}

interface MitigationGroup {
  id: string;
  mitigation: MockMitigation;
  actions: string[];
  findings: RelatedFinding[];
  executions: string[];
}

interface Props { module: ReportingModule }

const GROUPS: MitigationGroup[] = [
  {
    id: 'g1',
    mitigation: MOCK_MITIGATIONS.windows,
    actions: ['LSASS credential dump'],
    findings: [
      {
        type: 'credentials',
        value: 'Administrator (NTLM hash, masked)',
        action: 'LSASS credential dump',
        execution: 'WIN-DC01',
      },
      {
        type: 'credentials',
        value: 'svc-finance (NTLM hash, masked)',
        action: 'LSASS credential dump',
        execution: 'WIN-FIN-07',
      },
    ],
    executions: ['WIN-DC01', 'WIN-FIN-07'],
  },
  {
    id: 'g2',
    mitigation: buildMitigation({
      actionName: 'NetExec SMB - Share Listing',
      findingTypes: ['share'],
      hasAgent: false,
    }),
    actions: ['NetExec SMB - Share Listing'],
    findings: [
      {
        type: 'share',
        value: '\\\\winterfell\\C$',
        action: 'NetExec SMB - Share Listing',
        execution: 'winterfell (10.0.0.7)',
      },
      {
        type: 'share',
        value: '\\\\winterfell\\ADMIN$',
        action: 'NetExec SMB - Share Listing',
        execution: 'winterfell (10.0.0.7)',
      },
    ],
    executions: ['winterfell (10.0.0.7)'],
  },
  {
    id: 'g3',
    mitigation: buildMitigation({
      actionName: 'Nmap portscan',
      findingTypes: ['portscan'],
      hasAgent: false,
    }),
    actions: ['Nmap portscan'],
    findings: [
      {
        type: 'portscan',
        value: '20.224.192.102:445',
        action: 'Nmap portscan',
        execution: 'castelback (20.224.192.102)',
      },
      {
        type: 'portscan',
        value: '20.224.192.102:3389',
        action: 'Nmap portscan',
        execution: 'castelback (20.224.192.102)',
      },
    ],
    executions: ['castelback (20.224.192.102)'],
  },
];

// Not prevented executions that nobody generated a mitigation for yet.
const MISSING: {
  action: string;
  execution: string;
}[] = [
  {
    action: 'Credential file read',
    execution: 'ubuntu-web-03',
  },
];

// Criticality comes from XTM One and is displayed as is; it drives the order of the section.
const CRITICALITY_RANK: Record<MockMitigation['criticality'], number> = {
  critical: 0,
  high: 1,
  medium: 2,
  low: 3,
};

const MitigationsModule: FunctionComponent<Props> = ({ module }) => {
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
    : [...GROUPS].sort((a, b) => {
        // Most critical first, then the mitigation that closes the most executions.
        const byRisk = CRITICALITY_RANK[a.mitigation.criticality] - CRITICALITY_RANK[b.mitigation.criticality];
        return byRisk !== 0 ? byRisk : b.executions.length - a.executions.length;
      });
  const closedExecutions = groups.reduce((sum, group) => sum + group.executions.length, 0);

  if (groups.length === 0) {
    return <ModuleEmpty message={t('No mitigation was generated for this report subject yet.')} />;
  }

  const label = (text: string) => (
    <Typography sx={{
      fontSize: 10,
      letterSpacing: '0.08em',
      textTransform: 'uppercase',
      color: 'text.secondary',
      marginTop: 1.5,
      marginBottom: 0.5,
    }}
    >
      {text}
    </Typography>
  );

  return (
    <Box>
      <Typography sx={{
        fontSize: 12,
        color: 'text.secondary',
        marginBottom: 2,
      }}
      >
        {`${n(groups.length)} ${groups.length === 1 ? t('mitigation covers') : t('mitigations cover')} ${n(closedExecutions)} ${closedExecutions === 1 ? t('execution that was not prevented.') : t('executions that were not prevented.')}`}
        {MISSING.length > 0 && ` ${n(MISSING.length)} ${MISSING.length === 1 ? t('execution has no mitigation yet.') : t('executions have no mitigation yet.')}`}
      </Typography>

      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
      }}
      >
        {groups.map(group => (
          <Box
            key={group.id}
            sx={{
              border: `1px solid ${theme.palette.divider}`,
              borderRadius: 1,
              padding: 2,
              breakInside: 'avoid',
            }}
          >
            <Box sx={{
              display: 'flex',
              gap: 1,
              flexWrap: 'wrap',
              marginBottom: 1,
            }}
            >
              <PrintChip label={`${t('Criticality')}: ${t(group.mitigation.criticality)}`} color={riskColor(group.mitigation.criticality)} />
              <PrintChip label={`${t('Risk of applying')}: ${t(group.mitigation.risk)}`} color={riskColor(group.mitigation.risk)} />
              <PrintChip label={`${n(group.executions.length)} ${group.executions.length === 1 ? t('execution') : t('executions')}`} color={theme.palette.primary.main} />
              {group.mitigation.limitedContext && (
                <PrintChip label={t('Limited context')} color={theme.palette.warning.main} />
              )}
            </Box>
            <Typography sx={{
              fontSize: 12,
              marginBottom: 1,
            }}
            >
              {group.mitigation.summary}
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
              {group.mitigation.steps.map(step => (
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
              {`${t('Impact')}: ${group.mitigation.impact} ${t('Rollback')}: ${group.mitigation.rollback}`}
            </Typography>

            {label(t('Related actions'))}
            <Box sx={{
              display: 'flex',
              gap: 1,
              flexWrap: 'wrap',
            }}
            >
              {group.actions.map(action => (
                <PrintChip key={action} label={action} color={theme.palette.primary.main} />
              ))}
            </Box>

            {label(t('Related findings'))}
            <Box sx={{
              display: 'flex',
              flexDirection: 'column',
              gap: 0.5,
            }}
            >
              {group.findings.map(finding => (
                <Box
                  key={`${finding.execution}-${finding.value}`}
                  sx={{
                    display: 'flex',
                    gap: 1,
                    alignItems: 'center',
                    flexWrap: 'wrap',
                  }}
                >
                  <PrintChip label={finding.type} color={theme.palette.error.main} />
                  <Typography sx={{
                    fontSize: 11,
                    overflowWrap: 'anywhere',
                  }}
                  >
                    {finding.value}
                  </Typography>
                  <Typography sx={{
                    fontSize: 10,
                    color: 'text.disabled',
                  }}
                  >
                    {`${finding.action} - ${finding.execution}`}
                  </Typography>
                </Box>
              ))}
            </Box>
          </Box>
        ))}
      </Box>

      {MISSING.length > 0 && (
        <Box sx={{ marginTop: 2 }}>
          <Typography sx={{
            fontSize: 12,
            fontWeight: 600,
            marginBottom: 0.5,
          }}
          >
            {t('Not prevented, no mitigation generated yet')}
          </Typography>
          {MISSING.map(row => (
            <Typography
              key={row.execution}
              sx={{
                fontSize: 11,
                color: 'text.secondary',
              }}
            >
              {`${row.action} - ${row.execution}`}
            </Typography>
          ))}
        </Box>
      )}

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

export default MitigationsModule;
