import { Box, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import { type ReportingModule } from '../../../../../utils/api-types';
import { type AssetCriticality, humanizeEnum } from '../../../assets/asset-categories';
import { type MitigationFinding, type MockMitigation } from '../../../atomic_testings/atomic_testing/MitigationCard';
import { buildMitigations, buildRemediations, MOCK_MITIGATIONS } from '../../../atomic_testings/atomic_testing/mitigationMock';
import { combineCriticality, criticalityRank, hostCriticality } from '../../../remediation_mitigation/itemCriticality';
import { ModuleEmpty, PrintChip } from './ModuleSection';

// ---------------------------------------------------------------------------------------------
// PROTOTYPE - MOCK DATA ONLY. The Mitigations and Remediations sections of a report, inject by inject: the name of the
// inject, its result, then what is recommended for it (the remediation, then the mitigation), then the next inject.
// A remediation is made once for the action and never changes; a mitigation is tied to the result of the inject. What
// does not exist yet is generated when the report is generated, without waiting for the user to ask for it, so the
// report is always complete. The criticality is the weakness combined with the most critical host of the result, on the
// scale of the assets.
// With only one of the two modules in the report, only its part is shown; with both, one section shows them together.
// `module_config.mock_state = "empty"` shows the empty state (prototype switch).
// ---------------------------------------------------------------------------------------------

interface InjectResult {
  name: string;
  // Hosts where the inject was not prevented (the executions of its result).
  hosts: string[];
  findings: MitigationFinding[];
  hasAgent: boolean;
  platform?: string;
  // The mitigation of the result.
  mitigation: MockMitigation;
  // XTM One's answer when it has no patch for the action.
  noPatchReason?: string;
}

interface Props {
  module: ReportingModule;
  showMitigation: boolean;
  showRemediation: boolean;
}

const INJECTS: InjectResult[] = [
  {
    name: 'LSASS credential dump',
    hosts: ['WIN-DC01', 'WIN-FIN-07'],
    findings: [{ type: 'credentials' }],
    hasAgent: true,
    platform: 'Windows',
    mitigation: MOCK_MITIGATIONS.windows,
    noPatchReason: 'LSASS credential dump abuses a normal feature of the system, not a vulnerability, so there is no patch or fix to apply.',
  },
  {
    name: 'NetExec SMB - Share Listing',
    hosts: ['winterfell (10.0.0.7)'],
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
    mitigation: buildMitigations({
      actionName: 'NetExec SMB - Share Listing',
      findings: [{ type: 'share' }],
      hasAgent: false,
    })[0],
  },
  {
    name: 'Nmap portscan',
    hosts: ['castelback (20.224.192.102)'],
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
    mitigation: buildMitigations({
      actionName: 'Nmap portscan',
      findings: [{ type: 'portscan' }],
      hasAgent: false,
    })[0],
  },
  {
    name: 'Credential file read',
    hosts: ['ubuntu-web-03'],
    findings: [{ type: 'credentials' }],
    hasAgent: false,
    mitigation: buildMitigations({
      actionName: 'Credential file read',
      findings: [{ type: 'credentials' }],
      hasAgent: false,
    })[0],
  },
  {
    name: 'Log4Shell (CVE-2021-44228) vulnerability check',
    hosts: ['ubuntu-web-03'],
    findings: [{
      type: 'cve',
      value: 'CVE-2021-44228',
    }],
    hasAgent: false,
    mitigation: buildMitigations({
      actionName: 'Log4Shell (CVE-2021-44228) vulnerability check',
      findings: [{
        type: 'cve',
        value: 'CVE-2021-44228',
      }],
      hasAgent: false,
    })[0],
  },
];

const InjectGuidanceModule: FunctionComponent<Props> = ({ module, showMitigation, showRemediation }) => {
  const theme = useTheme();
  const { t, n } = useFormatter();
  const includeCommands = module.module_config?.include_commands !== false;
  const isEmpty = module.module_config?.mock_state === 'empty';

  // Same reading as the criticality chips of the product: red for Very high / High, orange Medium, green Low.
  const levelColor = (level: AssetCriticality) => {
    if (level === 'VERY_HIGH' || level === 'HIGH') return theme.palette.error.main;
    if (level === 'MEDIUM') return theme.palette.warning.main;
    if (level === 'LOW') return theme.palette.success.main;
    return theme.palette.text.disabled;
  };
  const riskColor = (risk: 'low' | 'medium' | 'high') => {
    if (risk === 'high') return theme.palette.error.main;
    if (risk === 'medium') return theme.palette.warning.main;
    return theme.palette.success.main;
  };

  const injects = isEmpty
    ? []
    : INJECTS.map(inject => ({
        ...inject,
        // Only the patches: hardening reduces the attack surface but leaves the weakness, it is a mitigation.
        remediations: buildRemediations({
          actionName: inject.name,
          findings: inject.findings,
          hasAgent: inject.hasAgent,
          platform: inject.platform,
        })
          .filter(remediation => remediation.controlType === 'patch')
          .map(remediation => ({
            ...remediation,
            combined: combineCriticality(remediation.criticality, inject.hosts),
          }))
          .sort((a, b) => criticalityRank(a.combined.level) - criticalityRank(b.combined.level)),
        mitigationCriticality: combineCriticality(inject.mitigation.criticality, inject.hosts),
      }));

  if (injects.length === 0) {
    return <ModuleEmpty message={t('No mitigation or remediation was generated for this report subject yet.')} />;
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

  const steps = (items: MockMitigation['steps']) => (
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
      {items.map(step => (
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
  );

  const guidance = (item: MockMitigation, kind: string, criticality: AssetCriticality) => (
    <Box sx={{ marginTop: 1 }}>
      <Box sx={{
        display: 'flex',
        gap: 1,
        flexWrap: 'wrap',
        marginBottom: 1,
      }}
      >
        <PrintChip label={kind} color={theme.palette.primary.main} />
        <PrintChip label={`${t('Criticality')}: ${t(humanizeEnum(criticality))}`} color={levelColor(criticality)} />
        <PrintChip label={`${t('Risk of applying')}: ${t(item.risk)}`} color={riskColor(item.risk)} />
      </Box>
      {item.title && (
        <Typography sx={{
          fontSize: 13,
          fontWeight: 600,
          marginBottom: 0.5,
        }}
        >
          {item.title}
        </Typography>
      )}
      <Typography sx={{
        fontSize: 12,
        marginBottom: 1,
      }}
      >
        {item.summary}
      </Typography>
      {steps(item.steps)}
      <Typography sx={{
        fontSize: 11,
        color: 'text.secondary',
        marginTop: 1,
      }}
      >
        {`${t('Impact')}: ${item.impact} ${t('Rollback')}: ${item.rollback}`}
      </Typography>
    </Box>
  );

  return (
    <Box>
      <Typography sx={{
        fontSize: 12,
        color: 'text.secondary',
        marginBottom: 2,
      }}
      >
        {`${n(injects.length)} ${injects.length === 1 ? t('inject was not prevented.') : t('injects were not prevented.')}`}
      </Typography>

      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
      }}
      >
        {injects.map(inject => (
          <Box
            key={inject.name}
            sx={{
              border: `1px solid ${theme.palette.divider}`,
              borderRadius: 1,
              padding: 2,
              breakInside: 'avoid',
            }}
          >
            <Typography sx={{
              fontSize: 14,
              fontWeight: 600,
              marginBottom: 1,
            }}
            >
              {inject.name}
            </Typography>

            {label(t('Result'))}
            <Box sx={{
              display: 'flex',
              gap: 1,
              flexWrap: 'wrap',
              alignItems: 'center',
            }}
            >
              <PrintChip label={t('Not prevented')} color={theme.palette.error.main} />
              {inject.hosts.map(host => (
                <PrintChip key={host} label={`${host} · ${t(humanizeEnum(hostCriticality(host)))}`} color={levelColor(hostCriticality(host))} />
              ))}
            </Box>

            {label(t('Discovered findings'))}
            <Box sx={{
              display: 'flex',
              gap: 1,
              flexWrap: 'wrap',
              alignItems: 'center',
            }}
            >
              {inject.findings.map(finding => (
                <Box
                  key={`${finding.type}-${finding.value ?? ''}`}
                  sx={{
                    display: 'flex',
                    gap: 0.75,
                    alignItems: 'center',
                  }}
                >
                  <PrintChip label={finding.type} color={theme.palette.error.main} />
                  {finding.value && <Typography sx={{ fontSize: 11 }}>{finding.value}</Typography>}
                </Box>
              ))}
            </Box>

            {showRemediation && (
              <>
                {label(t('Remediation'))}
                {inject.remediations.length === 0 && (
                  <Typography sx={{ fontSize: 12 }}>
                    {`${t('XTM One found no patch or fix for this action.')} ${inject.noPatchReason ?? `${inject.name} ${t('abuses a normal feature of the system, not a vulnerability, so there is no patch or fix to apply.')}`}${showMitigation ? ` ${t('See the Mitigation below to reduce the risk.')}` : ''}`}
                  </Typography>
                )}
                {inject.remediations.map(remediation => (
                  <Box key={remediation.summary}>
                    {guidance(remediation, t('Patch'), remediation.combined.level)}
                  </Box>
                ))}
              </>
            )}

            {showMitigation && (
              <>
                {label(t('Mitigation'))}
                {guidance(inject.mitigation, t(inject.mitigation.controlType === 'detection' ? 'Detection' : 'Prevention'), inject.mitigationCriticality.level)}
              </>
            )}
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

export default InjectGuidanceModule;
