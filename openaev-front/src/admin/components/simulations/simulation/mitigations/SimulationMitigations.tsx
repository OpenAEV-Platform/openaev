import { Button, Chip, Paper, Text } from '@filigran/design-system';
import { ExpandLess, ExpandMore } from '@mui/icons-material';
import { type FunctionComponent, useMemo, useState } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import MitigationCard, { criticalitySeverity, type MockMitigation } from '../../../atomic_testings/atomic_testing/MitigationCard';
import { buildMitigations } from '../../../atomic_testings/atomic_testing/mitigationMock';
import MitigationTargetsTable from '../../../atomic_testings/atomic_testing/MitigationTargetsTable';

// ---------------------------------------------------------------------------------------------
// PROTOTYPE - MOCK DATA ONLY. Nothing is sent to the backend or to XTM One.
// Simulation level, MITIGATION FIRST: the list is made of distinct mitigations (one per
// command + finding types + operating system), sorted by criticality, each with the number of
// assets and findings it closes. The assets are a drill-down (searchable, paginated), so many
// assets do not make the screen longer: they collapse into a handful of mitigations.
// Generated on click only; "Generate missing" asks once per group, never per asset.
// ---------------------------------------------------------------------------------------------

interface Group {
  id: string;
  action: string;
  command: string;
  environment?: string;
  platform?: string;
  assetPrefix: string;
  assetCount: number;
  findings: {
    type: string;
    value?: string;
  }[];
}

const GROUPS: Group[] = [
  {
    id: 'g1',
    action: 'LSASS credential dump',
    command: 'procdump.exe -accepteula -ma lsass.exe #{dump_path}',
    environment: 'Windows 10 22H2 · admin (SYSTEM)',
    platform: 'windows',
    assetPrefix: 'WIN-FIN',
    assetCount: 12,
    findings: [{ type: 'credentials' }],
  },
  {
    id: 'g2',
    action: 'LSASS credential dump',
    command: 'procdump.exe -accepteula -ma lsass.exe #{dump_path}',
    environment: 'Windows 11 23H2 · admin',
    platform: 'windows',
    assetPrefix: 'WIN-HR',
    assetCount: 6,
    findings: [{ type: 'credentials' }],
  },
  {
    id: 'g3',
    action: 'NetExec SMB - Share Listing',
    command: 'nxc smb #{target} -u #{user} -p ****** --shares',
    assetPrefix: '10.0.0.',
    assetCount: 5,
    // One share finding per share the command listed.
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
  },
  {
    id: 'g4',
    action: 'Nmap portscan',
    command: 'nmap -sV -p 445,3389 #{target}',
    assetPrefix: '10.20.0.',
    assetCount: 4,
    // One portscan finding per open port the scan reported.
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
  },
  {
    id: 'g5',
    action: 'Credential file read',
    command: 'cat /etc/app/credentials.conf',
    environment: 'Ubuntu 22.04 · standard (www-data)',
    platform: 'linux',
    assetPrefix: 'ubuntu-web',
    assetCount: 3,
    findings: [{ type: 'credentials' }],
  },
];

const CRITICALITY_RANK = {
  critical: 0,
  high: 1,
  medium: 2,
  low: 3,
};

const pad = (n: number) => String(n).padStart(2, '0');

const assetsOf = (group: Group) => Array.from({ length: group.assetCount }, (_, i) => ({
  name: group.assetPrefix.endsWith('.') ? `${group.assetPrefix}${i + 10}` : `${group.assetPrefix}-${pad(i + 1)}`,
  environment: group.environment ?? 'No agent (injector based)',
}));

const AssetsTable = ({ group }: { group: Group }) => {
  const rows = useMemo(() => assetsOf(group).map(asset => ({
    ...asset,
    findings: group.findings.map(finding => finding.type).join(', '),
  })), [group]);
  return <MitigationTargetsTable rows={rows} />;
};

interface Props {
  // Shown in the header when the tab is opened from a scenario (simulation picker).
  simulationLabel?: string;
}

const SimulationMitigations: FunctionComponent<Props> = ({ simulationLabel }) => {
  const { t, n } = useFormatter();
  const [generated, setGenerated] = useState<Set<string>>(new Set(['g1']));
  const [loading, setLoading] = useState<Set<string>>(new Set());
  const [open, setOpen] = useState<string | null>('g1');

  const groups = useMemo(() => GROUPS.map(group => ({
    group,
    mitigations: buildMitigations({
      actionName: group.action,
      command: group.command,
      findings: group.findings,
      hasAgent: !!group.environment,
      platform: group.platform,
    }),
  })).sort((a, b) => {
    const rank = (m: MockMitigation[]) => Math.min(...m.map(item => CRITICALITY_RANK[item.criticality]));
    return rank(a.mitigations) - rank(b.mitigations);
  }), []);

  const totalAssets = GROUPS.reduce((sum, group) => sum + group.assetCount, 0);
  const missing = groups.filter(({ group }) => !generated.has(group.id) && !loading.has(group.id));
  const missingAssets = missing.reduce((sum, { group }) => sum + group.assetCount, 0);

  const generate = (ids: string[]) => {
    setLoading(prev => new Set([...prev, ...ids]));
    setTimeout(() => {
      setGenerated(prev => new Set([...prev, ...ids]));
      setLoading(prev => new Set([...prev].filter(id => !ids.includes(id))));
    }, 1500);
  };

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 16,
    }}
    >
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        flexWrap: 'wrap',
      }}
      >
        <Chip label={t('Prototype - mock data')} severity="medium" />
        {simulationLabel && <Chip label={simulationLabel} severity="neutral" />}
        <Text variant="content-compact" className="text-default-secondary">
          {`${n(groups.length)} ${t('mitigations cover')} ${n(totalAssets)} ${t('executions that were not prevented.')}`}
        </Text>
        <div style={{
          marginLeft: 'auto',
          display: 'flex',
          alignItems: 'center',
          gap: 8,
        }}
        >
          {missing.length > 0 && (
            <Text variant="content-caption" className="text-default-secondary">
              {`${n(missing.length)} ${t('requests, one per distinct command, finding types and OS, for')} ${n(missingAssets)} ${t('executions')}`}
            </Text>
          )}
          <Button
            variant="ia"
            priority="secondary"
            disabled={missing.length === 0}
            onClick={() => generate(missing.map(({ group }) => group.id))}
          >
            {`${t('Generate missing')} (${missing.length})`}
          </Button>
        </div>
      </div>

      {groups.map(({ group, mitigations }) => {
        const isGenerated = generated.has(group.id);
        const isLoading = loading.has(group.id);
        const isOpen = open === group.id;
        const top = mitigations[0];
        return (
          <Paper
            key={group.id}
            padding={16}
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 12,
            }}
          >
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              flexWrap: 'wrap',
            }}
            >
              {isGenerated && !isOpen && <Chip label={`${t('Criticality')}: ${t(top.criticality)}`} severity={criticalitySeverity[top.criticality]} />}
              {!isGenerated && <Chip label={t('Not generated')} severity="neutral" />}
              <Text variant="content-base-bold">{group.action}</Text>
              <Chip label={`${n(group.assetCount)} ${t('assets')}`} severity="info" />
              <Text variant="content-caption" className="text-default-secondary">
                {group.environment ?? t('No agent (injector based)')}
              </Text>
              <div style={{
                marginLeft: 'auto',
                display: 'flex',
                gap: 8,
              }}
              >
                {!isGenerated && (
                  <Button variant="ia" priority="secondary" disabled={isLoading} onClick={() => generate([group.id])}>
                    {isLoading ? t('Generating mitigation…') : t('Generate mitigation')}
                  </Button>
                )}
                <Button priority="secondary" onClick={() => setOpen(isOpen ? null : group.id)} endIcon={isOpen ? <ExpandLess /> : <ExpandMore />}>
                  {t('Details')}
                </Button>
              </div>
            </div>
            {/* Shown while collapsed; once open, the card below carries the same information. */}
            {!(isOpen && isGenerated) && (
              <div style={{
                display: 'flex',
                flexWrap: 'wrap',
                gap: 6,
                alignItems: 'center',
              }}
              >
                <Text variant="content-caption" className="text-default-secondary">{t('Affected findings')}</Text>
                {top.addresses.map(finding => (
                  <Chip key={`${finding.type}-${finding.value ?? ''}`} label={finding.value ? `${finding.type}: ${finding.value}` : `${finding.type} (${t('value masked')})`} severity="neutral" />
                ))}
              </div>
            )}
            {isOpen && (
              <div style={{
                display: 'flex',
                flexDirection: 'column',
                gap: 16,
              }}
              >
                {isGenerated
                  ? mitigations.map(mitigation => (
                      <MitigationCard key={mitigation.summary} mitigation={mitigation} onRegenerate={() => generate([group.id])} />
                    ))
                  : <Text variant="content-compact" className="text-default-secondary">{t('No mitigation generated for this group yet.')}</Text>}
                <div>
                  <Text variant="content-base-bold" className="mb-2 block text-default-secondary">
                    {`${t('Affected assets')} (${n(group.assetCount)})`}
                  </Text>
                  <AssetsTable group={group} />
                </div>
              </div>
            )}
          </Paper>
        );
      })}
    </div>
  );
};

export default SimulationMitigations;
