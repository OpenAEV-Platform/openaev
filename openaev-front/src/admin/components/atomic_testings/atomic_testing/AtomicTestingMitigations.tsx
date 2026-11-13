import { Button, Chip, Paper, Tabs, TabsList, TabsTrigger, Text } from '@filigran/design-system';
import { BuildOutlined, ShieldOutlined } from '@mui/icons-material';
import { useMemo, useRef, useState } from 'react';

import { useFormatter } from '../../../../components/i18n';
import MitigationCard, { type MitigationFinding, type MockMitigation } from './MitigationCard';
import MitigationContextPanel from './MitigationContextPanel';
import { buildMitigations } from './mitigationMock';
import MitigationTargetsTable from './MitigationTargetsTable';

// ---------------------------------------------------------------------------------------------
// PROTOTYPE - MOCK DATA ONLY.
// Nothing here talks to the backend or to XTM One. Mitigation tab of ONE atomic testing, built to
// stay readable with many targets: the targets that were NOT PREVENTED are grouped by the reuse key
// (command + finding types + operating system), so the left list is made of a handful of groups,
// not of targets. Each group has one mitigation and a searchable, paginated target table.
// Prevented targets need no mitigation: they are only counted. Generated on click, once per group.
// ---------------------------------------------------------------------------------------------

interface MockTarget {
  name: string;
  prevented: boolean;
  // Operating system of the agent. Undefined for injector based executions (no agent).
  os?: string;
  privilege?: string;
  findings: MitigationFinding[];
}

interface MockSet {
  action: string;
  command: string;
  targets: MockTarget[];
}

interface Group {
  key: string;
  label: string;
  os?: string;
  findings: MitigationFinding[];
  targets: MockTarget[];
}

type MitigationState
  = | {
    status: 'ready';
    mitigations: MockMitigation[];
  }
  | { status: 'loading' }
  | { status: 'error' };

const pad = (n: number) => String(n).padStart(2, '0');
const range = <T, >(count: number, build: (index: number) => T): T[] => Array.from({ length: count }, (_, i) => build(i + 1));

const credentials: MitigationFinding[] = [{ type: 'credentials' }];

// LSASS dump: payload run by an agent. 6 of them prevented by the EDR.
const PAYLOAD_SET: MockSet = {
  action: 'LSASS credential dump',
  command: 'procdump.exe -accepteula -ma lsass.exe #{dump_path}',
  targets: [
    ...range(12, i => ({
      name: `WIN-FIN-${pad(i)}`,
      prevented: false,
      os: 'Windows 10 22H2',
      privilege: 'admin (SYSTEM)',
      findings: credentials,
    })),
    ...range(4, i => ({
      name: `WIN-SRV-${pad(i)}`,
      prevented: false,
      os: 'Windows Server 2022',
      privilege: 'admin (SYSTEM)',
      findings: credentials,
    })),
    ...range(3, i => ({
      name: `WIN-HR-${pad(i)}`,
      prevented: false,
      os: 'Windows 11 23H2',
      privilege: 'admin',
      findings: credentials,
    })),
    ...range(6, i => ({
      name: `WIN-DEV-${pad(i)}`,
      prevented: true,
      os: 'Windows 11 23H2',
      privilege: 'standard',
      findings: [],
    })),
  ],
};

// Injector based actions run from the platform against an asset: no agent, so no OS. The reuse key
// is then command + finding types only, and most of the time there is a single group.
const NMAP_SET: MockSet = {
  action: 'Nmap portscan',
  command: 'nmap -sV -p 445,3389 #{target}',
  targets: [
    ...range(14, i => ({
      name: `HOST-${pad(i)} (10.20.0.${i + 10})`,
      prevented: false,
      // Some hosts only expose SMB: same finding type, so the same group and the same mitigation.
      findings: i % 3 === 0
        ? [{
            type: 'portscan',
            value: '445/tcp',
          }]
        : [
            {
              type: 'portscan',
              value: '445/tcp',
            },
            {
              type: 'portscan',
              value: '3389/tcp',
            },
          ],
    })),
    ...range(5, i => ({
      name: `DMZ-${pad(i)} (10.30.0.${i + 10})`,
      prevented: true,
      findings: [],
    })),
  ],
};

const NETEXEC_SET: MockSet = {
  action: 'NetExec SMB - Share Listing',
  command: 'nxc smb #{target} -u #{user} -p ****** --shares',
  targets: [
    ...range(9, i => ({
      name: `FILESRV-${pad(i)} (10.20.1.${i + 10})`,
      prevented: false,
      findings: [
        {
          type: 'share',
          value: 'C$',
        },
        {
          type: 'share',
          value: i % 2 === 0 ? 'ADMIN$' : 'Finance',
        },
      ],
    })),
    ...range(3, i => ({
      name: `DC-${pad(i)} (10.20.2.${i + 10})`,
      prevented: true,
      findings: [],
    })),
  ],
};

// In the product the targets come from the inject itself; the prototype picks a mock set from the
// injector of the atomic testing.
const setFor = (injectType?: string): MockSet => {
  if (injectType === 'openaev_nmap') return NMAP_SET;
  if (injectType === 'openaev_netexec') return NETEXEC_SET;
  return PAYLOAD_SET;
};

const distinctFindings = (findings: MitigationFinding[]) => {
  const seen = new Set<string>();
  return findings.filter((finding) => {
    const id = `${finding.type}|${finding.value ?? ''}`;
    if (seen.has(id)) return false;
    seen.add(id);
    return true;
  });
};

// The "do not ask twice" key: command + finding types + operating system.
const groupKey = (command: string, target: MockTarget) => {
  const types = [...new Set(target.findings.map(finding => finding.type))].sort().join(',');
  return [command, types, target.os ?? 'no-agent'].join('|');
};

interface Props { injectType?: string }

const AtomicTestingMitigations = ({ injectType }: Props) => {
  const { t, n } = useFormatter();
  const set = setFor(injectType);
  const [selected, setSelected] = useState<string | null>(null);
  const [store, setStore] = useState<Record<string, MitigationState>>({});
  // Demo only: the first attempt for the Windows Server group fails so the error state can be seen.
  const failedOnce = useRef(false);

  const { groups, preventedCount } = useMemo(() => {
    const byKey = new Map<string, Group>();
    set.targets.filter(target => !target.prevented).forEach((target) => {
      const key = groupKey(set.command, target);
      const group = byKey.get(key) ?? {
        key,
        label: target.os ?? t('Injector based (no agent)'),
        os: target.os,
        findings: [],
        targets: [],
      };
      group.targets.push(target);
      group.findings = distinctFindings([...group.findings, ...target.findings]);
      byKey.set(key, group);
    });
    return {
      groups: [...byKey.values()].sort((a, b) => b.targets.length - a.targets.length),
      preventedCount: set.targets.filter(target => target.prevented).length,
    };
  }, [set, t]);

  const group = groups.find(g => g.key === selected) ?? groups[0];
  const state = group ? store[group.key] : undefined;

  const generate = (keys: string[]) => {
    setStore((prev) => {
      const next = { ...prev };
      keys.forEach((key) => {
        next[key] = { status: 'loading' };
      });
      return next;
    });
    setTimeout(() => {
      setStore((prev) => {
        const next = { ...prev };
        keys.forEach((key) => {
          const target = groups.find(g => g.key === key);
          if (!target) return;
          if (target.os?.startsWith('Windows Server') && !failedOnce.current) {
            failedOnce.current = true;
            next[key] = { status: 'error' };
            return;
          }
          next[key] = {
            status: 'ready',
            mitigations: buildMitigations({
              actionName: set.command,
              command: set.command,
              findings: target.findings,
              hasAgent: !!target.os,
              platform: target.os,
            }),
          };
        });
        return next;
      });
    }, 1500);
  };

  const missing = groups.filter(g => !store[g.key]);
  const missingTargets = missing.reduce((sum, g) => sum + g.targets.length, 0);
  const notPreventedCount = groups.reduce((sum, g) => sum + g.targets.length, 0);

  const rows = useMemo(() => (group
    ? group.targets.map(target => ({
        name: target.name,
        environment: target.os ? `${target.os} · ${target.privilege ?? ''}` : t('No agent (injector based)'),
        findings: target.findings.map(finding => (finding.value ? `${finding.type}: ${finding.value}` : finding.type)).join(', '),
      }))
    : []), [group, t]);

  const centered = {
    display: 'flex',
    flexDirection: 'column' as const,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
    textAlign: 'center' as const,
    minHeight: 160,
  };

  if (groups.length === 0) {
    return (
      <Paper padding={32} style={centered}>
        <ShieldOutlined sx={{ fontSize: 44 }} />
        <Text variant="content-compact" className="text-default-secondary">
          {t('The action was prevented on every target, no mitigation needed.')}
        </Text>
      </Paper>
    );
  }

  const renderBody = () => {
    if (state?.status === 'ready') {
      return state.mitigations.map(mitigation => (
        <MitigationCard key={mitigation.summary} mitigation={mitigation} onRegenerate={() => generate([group.key])} />
      ));
    }
    if (state?.status === 'loading') {
      return (
        <Paper padding={32} style={centered}>
          <Text variant="content-compact" className="text-default-secondary">{t('Generating mitigation…')}</Text>
        </Paper>
      );
    }
    return (
      <Paper padding={32} style={centered}>
        <BuildOutlined sx={{ fontSize: 44 }} />
        <Text variant="content-compact" className="text-default-secondary">
          {state?.status === 'error'
            ? t('Mitigation generation failed. Try again.')
            : t('No mitigation generated for this group yet.')}
        </Text>
        <Button variant="ia" priority="secondary" onClick={() => generate([group.key])}>
          {state?.status === 'error' ? t('Try again') : t('Generate mitigation')}
        </Button>
      </Paper>
    );
  };

  return (
    <div style={{
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
        <Chip label={t('Prototype - mock data')} severity="medium" />
        <Text variant="content-caption" className="text-default-secondary">
          {`${n(groups.length)} ${groups.length === 1 ? t('mitigation covers') : t('mitigations cover')} ${n(notPreventedCount)} ${t('targets that were not prevented.')}`}
          {preventedCount > 0 && ` ${n(preventedCount)} ${t('targets were protected, no mitigation needed.')}`}
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
              {`${n(missing.length)} ${t('requests, one per distinct command, finding types and OS, for')} ${n(missingTargets)} ${t('targets')}`}
            </Text>
          )}
          <Button variant="ia" priority="secondary" disabled={missing.length === 0} onClick={() => generate(missing.map(g => g.key))}>
            {`${t('Generate missing')} (${missing.length})`}
          </Button>
        </div>
      </div>

      <div style={{
        display: 'flex',
        gap: 16,
        alignItems: 'stretch',
        minHeight: 340,
      }}
      >
        <Tabs
          orientation="vertical"
          value={group.key}
          onValueChange={setSelected}
          panels="external"
          style={{
            display: 'grid',
            minWidth: 240,
            maxWidth: 280,
            flexShrink: 0,
          }}
        >
          <TabsList aria-label={t('Mitigation groups')}>
            {groups.map(g => (
              <TabsTrigger key={g.key} value={g.key}>
                {`${g.label} · ${n(g.targets.length)}`}
              </TabsTrigger>
            ))}
          </TabsList>
        </Tabs>

        <div style={{
          flex: 1,
          minWidth: 0,
          display: 'flex',
          flexDirection: 'column',
          gap: 12,
        }}
        >
          <MitigationContextPanel
            action={set.action}
            command={set.command}
            findings={group.findings}
            environment={group.os ? `${group.os} · ${n(group.targets.length)} ${t('targets')}` : undefined}
          />
          {renderBody()}
          <div>
            <Text variant="content-base-bold" className="mb-2 block text-default-secondary">
              {`${t('Targets')} (${n(group.targets.length)})`}
            </Text>
            <MitigationTargetsTable key={group.key} rows={rows} />
          </div>
        </div>
      </div>
    </div>
  );
};

export default AtomicTestingMitigations;
