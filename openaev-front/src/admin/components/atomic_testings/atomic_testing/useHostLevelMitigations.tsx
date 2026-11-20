import { Button, Text } from '@filigran/design-system';
import { CheckCircleOutlined, ExpandLess, ExpandMore, WarningAmberOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { useMemo, useRef, useState } from 'react';

import Empty from '../../../../components/Empty';
import { useFormatter } from '../../../../components/i18n';
import { tint } from '../../../../utils/tint';
import GenerateAiButton from '../../remediation_mitigation/GenerateAiButton';
import RemediationMitigationItemCard from '../../remediation_mitigation/RemediationMitigationItemCard';
import { REMEDIATION_CONTROL_TYPES, type RemediationMitigationCategory, type RemediationMitigationItem } from '../../remediation_mitigation/remediationMitigationMock';
import { type HostLevel, type HostLevelDetail } from '../../remediation_mitigation/RemediationMitigationPanel';
import { resolvePayload } from '../../remediation_mitigation/remediationMitigationStore';
import RemediationTypeSection from '../../remediation_mitigation/RemediationTypeSection';
import useRemediationMitigationLabels from '../../remediation_mitigation/useRemediationMitigationLabels';
import { type MitigationFinding, type MockMitigation } from './MitigationCard';
import MitigationContextPanel from './MitigationContextPanel';
import { buildMitigations, buildRemediations } from './mitigationMock';
import MitigationValidationPanel from './MitigationValidationPanel';
import { useMitigationValidation } from './useMitigationValidation';

// ---------------------------------------------------------------------------------------------
// PROTOTYPE - MOCK DATA ONLY.
// Nothing here talks to the backend or to XTM One. "Host-level" part of the Mitigation tab of ONE
// atomic testing: the targets that were NOT PREVENTED are grouped by the full list of inputs the
// mitigation is generated from (command + findings + operating system + privilege). One input that
// differs makes another group, because it can make another mitigation. Each group is an entry of the
// left list; clicking it shows the result of ITS request on the right, with the validation flow.
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
  // A group exists because its inputs produce a different mitigation, so its name is made of them:
  // the title is what the action found, the environment is where it ran.
  title: string;
  environment: string;
  os?: string;
  privilege?: string;
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

// Unauthenticated network attack against domain controllers: no agent, no privilege.
const ZEROLOGON_SET: MockSet = {
  action: 'Zerologon (CVE-2020-1472) vulnerability check',
  command: 'python3 zerologon_tester.py #{dc_name} #{dc_ip}',
  targets: [
    {
      name: 'DC01 (10.0.0.10)',
      prevented: false,
      os: 'Windows Server 2019',
      findings: [{ type: 'zerologon' }],
    },
    {
      name: 'DC02 (10.0.0.11)',
      prevented: false,
      os: 'Windows Server 2019',
      findings: [{ type: 'zerologon' }],
    },
  ],
};

// Canary-based check against a single Java web application: no agent, no privilege.
const LOG4SHELL_SET: MockSet = {
  action: 'Log4Shell (CVE-2021-44228) vulnerability check',
  command: 'curl -s -H "X-Api-Version: ...jndi:ldap://..." #{target_url}',
  targets: [
    {
      name: 'ubuntu-web-03 (10.20.3.10)',
      prevented: false,
      os: 'Linux',
      findings: [{
        type: 'log4shell',
        value: 'log4j-core 2.14.1',
      }],
    },
  ],
};

// In the product the targets come from the inject itself; the prototype picks a mock set from the
// injector and the title of the atomic testing.
const setFor = (injectType?: string, injectTitle?: string): MockSet => {
  const title = (injectTitle ?? '').toLowerCase();
  if (injectType === 'openaev_nmap') return NMAP_SET;
  if (injectType === 'openaev_netexec') return NETEXEC_SET;
  if (title.includes('zerologon')) return ZEROLOGON_SET;
  if (title.includes('log4j') || title.includes('log4shell') || title.includes('44228')) return LOG4SHELL_SET;
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

const findingId = (finding: MitigationFinding) => `${finding.type}=${finding.value ?? ''}`;

// The "do not ask twice" key is the full list of inputs the mitigation is generated from: command,
// findings (type AND value: another open port or share is another mitigation), operating system and
// privilege. If any of them differs, the mitigation can differ, so the group differs.
const groupKey = (command: string, target: MockTarget) => [
  command,
  distinctFindings(target.findings).map(findingId).sort().join(','),
  target.os ?? 'no-agent',
  target.privilege ?? 'no-privilege',
].join('|');

// "credentials", "portscan: 445/tcp, 3389/tcp", "share: ADMIN$, C$". Masked findings have no value.
const findingsTitle = (findings: MitigationFinding[]) => {
  const byType = new Map<string, string[]>();
  distinctFindings(findings).forEach((finding) => {
    const values = byType.get(finding.type) ?? [];
    if (finding.value) values.push(finding.value);
    byType.set(finding.type, values);
  });
  return [...byType]
    .map(([type, values]) => (values.length > 0 ? `${type}: ${values.sort().join(', ')}` : type))
    .join(' · ');
};

// Builds the "Host-level" part of the Mitigation tab: groups of assets, their entries and their detail.
// The layer follows what was found: exposed ports belong to the network, credentials to the directory.
const applyLayerOf = (group: Group): RemediationMitigationItem['apply_layer'] => {
  if (group.findings.some(finding => finding.type.includes('portscan'))) return 'network';
  if (group.findings.some(finding => finding.type.includes('credentials'))) return 'directory_domain';
  if (group.findings.some(finding => finding.type.includes('zerologon'))) return 'directory_domain';
  if (group.findings.some(finding => finding.type.includes('log4shell'))) return 'application';
  return 'host';
};

// Shows a generated host-level mitigation like a vendor-specific one: same card, same sections.
const toItem = (
  group: Group,
  mitigation: MockMitigation,
  index: number,
  category: RemediationMitigationCategory,
): RemediationMitigationItem => {
  const commands = mitigation.steps.flatMap(step => (step.command ? [`# ${step.text}`, step.command] : []));
  const remediation = category === 'remediation';
  return {
    id: `${group.key}#${index}`,
    category,
    control_type: mitigation.controlType ?? (remediation ? 'hardening' : 'prevention'),
    scope: 'generic',
    title: `${remediation ? 'Host-level remediation' : 'Host-level mitigation'}: ${group.title}`,
    summary: `${mitigation.summary} Criticality: ${mitigation.criticality}.`,
    platform: group.os?.toLowerCase().includes('linux') ? 'linux' : 'windows',
    author_rule: 'AI',
    review_state: 'needs_review',
    verify: mitigation.limitedContext,
    applies_to: group.os ? `${group.os} · ${group.privilege ?? ''}`.replace(/ · $/, '') : 'Targets reached by an injector (no agent)',
    apply_layer: applyLayerOf(group),
    actions: [{
      title: remediation ? 'Remove the weakness on the affected assets' : 'Apply on the affected assets',
      steps: mitigation.steps.map(step => step.text),
      prerequisites: [],
      change_risk: `${mitigation.risk}: ${mitigation.impact} Rollback: ${mitigation.rollback}`,
    }],
    content: commands.length > 0 ? commands.join('\n') : undefined,
    assumptions: mitigation.limitedContext ? ['No agent: the suggestion relies on the command and its findings only.'] : undefined,
  };
};

// Groups of assets of an atomic testing, for the Mitigation tab (host-level) and for the Remediation tab
// (a remediation has no vendor: the groups are its only list).
const useHostLevelMitigations = (
  injectType?: string,
  injectTitle?: string,
  category: RemediationMitigationCategory = 'mitigation',
): HostLevel => {
  const isRemediation = category === 'remediation';
  const labels = useRemediationMitigationLabels();
  const payload = resolvePayload(injectTitle);
  const { t, n } = useFormatter();
  const set = setFor(injectType, injectTitle);
  const theme = useTheme();
  // Groups whose asset list is open. Independent of the selection: collapsing keeps the group selected.
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [store, setStore] = useState<Record<string, MitigationState>>({});
  const { validation, validate, reset: resetValidation } = useMitigationValidation();
  // Demo only: the first attempt for the Windows Server group fails so the error state can be seen.
  const failedOnce = useRef(false);

  const { groups, preventedCount } = useMemo(() => {
    const byKey = new Map<string, Group>();
    set.targets.filter(target => !target.prevented).forEach((target) => {
      const key = groupKey(set.command, target);
      const group = byKey.get(key) ?? {
        key,
        title: findingsTitle(target.findings),
        environment: target.os ? [target.os, target.privilege].filter(Boolean).join(' · ') : t('Injector based (no agent)'),
        os: target.os,
        privilege: target.privilege,
        // Same key, same findings: every target of the group reports the same ones.
        findings: distinctFindings(target.findings),
        targets: [],
      };
      group.targets.push(target);
      byKey.set(key, group);
    });
    const all = [...byKey.values()];
    // A group is named after what makes it different from the others: an argument shared by every
    // group says nothing. With a single group, nothing differs: an agent-based group (it has an OS)
    // is named after its environment, same as any other agent-based group; an injector-based group has
    // no OS to show, so its only informative content is what it found.
    const vary = (read: (group: Group) => string | undefined) => new Set(all.map(read)).size > 1;
    const varying = {
      findings: vary(group => findingsTitle(group.findings)),
      os: vary(group => group.os),
      privilege: vary(group => group.privilege),
    };
    all.forEach((group) => {
      const parts = all.length === 1
        ? [group.os ? group.environment : findingsTitle(group.findings)]
        : [
            varying.findings ? findingsTitle(group.findings) : undefined,
            varying.os ? group.os ?? t('Injector based (no agent)') : undefined,
            varying.privilege ? group.privilege : undefined,
          ];
      group.title = parts.filter(Boolean).join(' · ');
    });
    return {
      groups: all.sort((a, b) => b.targets.length - a.targets.length),
      preventedCount: set.targets.filter(target => target.prevented).length,
    };
  }, [set, t]);

  const generate = (keys: string[]) => {
    resetValidation(keys);
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
            mitigations: (isRemediation ? buildRemediations : buildMitigations)({
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

  const notPreventedCount = groups.reduce((sum, g) => sum + g.targets.length, 0);
  const validatedCount = groups.reduce((sum, g) => {
    const v = validation[g.key];
    return v?.status === 'done' ? sum + v.total - v.failed.length : sum;
  }, 0);

  const renderEntry = (key: string, selected: boolean, onSelect: () => void) => {
    const g = groups.find(group => group.key === key);
    if (!g) return null;
    const isOpen = expanded.has(g.key);
    const assetsId = `mitigation-group-assets-${groups.indexOf(g)}`;
    const groupValidation = validation[g.key];
    const failedNames = groupValidation?.status === 'done' ? groupValidation.failed : [];
    // Green once every target of the group is validated as prevented.
    const isFixed = groupValidation?.status === 'done' && failedNames.length === 0;
    let startIcon;
    if (groupValidation?.status === 'done') {
      startIcon = isFixed
        ? <CheckCircleOutlined fontSize="small" />
        : <WarningAmberOutlined fontSize="small" />;
    }
    return (
      <div role="listitem">
        <Button
          priority={selected ? 'secondary' : 'tertiary'}
          aria-expanded={isOpen}
          aria-controls={assetsId}
          startIcon={startIcon}
          endIcon={isOpen ? <ExpandLess fontSize="small" /> : <ExpandMore fontSize="small" />}
          style={{
            width: '100%',
            height: 'auto',
            minHeight: 36,
            padding: '6px 12px',
            justifyContent: 'space-between',
            textAlign: 'left',
            ...(isFixed && {
              backgroundColor: tint(theme.palette.success.main, 15),
              borderColor: theme.palette.success.main,
              color: theme.palette.success.main,
            }),
          }}
          onClick={() => {
            onSelect();
            setExpanded((prev) => {
              const next = new Set(prev);
              if (next.has(g.key)) next.delete(g.key);
              else next.add(g.key);
              return next;
            });
          }}
        >
          <span style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'flex-start',
            gap: 2,
            minWidth: 0,
          }}
          >
            <span style={{ fontWeight: 600 }}>{g.title}</span>
            <span style={{
              fontSize: '0.75em',
              opacity: 0.8,
            }}
            >
              {`${n(g.targets.length)} ${t('targets')}`}
            </span>
          </span>
        </Button>
        {isOpen && (
          <ul
            id={assetsId}
            aria-label={`${t('Targets')} (${n(g.targets.length)})`}
            style={{
              listStyle: 'none',
              margin: '4px 0 4px 12px',
              padding: '0 0 0 12px',
              borderLeft: `1px solid ${theme.palette.divider}`,
              maxHeight: 240,
              overflowY: 'auto',
            }}
          >
            {g.targets.map(target => (
              <li key={target.name} style={{ padding: '2px 0' }}>
                <Text variant="content-caption" className="text-default-secondary">
                  {groupValidation?.status === 'done'
                    ? `${target.name} · ${failedNames.includes(target.name) ? t('not prevented') : t('prevented')}`
                    : target.name}
                </Text>
              </li>
            ))}
          </ul>
        )}
      </div>
    );
  };

  // Same shape as a vendor-specific pane: a title, a Generate button in the top right corner and the cards.
  const renderDetail = (key: string, controlFilter: string): HostLevelDetail | null => {
    const group = groups.find(g => g.key === key);
    if (!group) return null;
    const state = store[group.key];
    const findingTypes = [...new Set(group.findings.map(finding => finding.type))];
    const context = (
      <MitigationContextPanel
        action={set.action}
        command={set.command}
        findings={group.findings}
        showFindings={false}
        environment={group.os ? `${group.environment} · ${n(group.targets.length)} ${t('targets')}` : undefined}
        assets={group.targets.map(target => target.name)}
      />
    );
    if (state?.status === 'ready') {
      // A mitigation is a detection or a prevention: each sub-tab shows its own, a remediation shows all.
      const visibleItems = state.mitigations
        .map((mitigation, index) => toItem(group, mitigation, index, category))
        .filter(item => isRemediation || item.control_type === controlFilter);
      return {
        title: group.title,
        action: <GenerateAiButton onClick={() => generate([group.key])}>{t('Regenerate')}</GenerateAiButton>,
        body: (
          <>
            {isRemediation
              ? REMEDIATION_CONTROL_TYPES.map((controlType) => {
                  const ofType = visibleItems.filter(item => item.control_type === controlType);
                  return (
                    <RemediationTypeSection
                      key={controlType}
                      title={`${labels.controlType[controlType]} ${labels.category.remediation.toLowerCase()}`}
                      hasItems={ofType.length > 0}
                      // XTM One answered with nothing for this kind of remediation: say so.
                      emptyText={`${t('No suggestion available.')} ${t('XTM One has no suggestion for this group.')} ${t('The findings of this group give no weakness of this kind to remove.')}`}
                    >
                      {ofType.map(item => (
                        <RemediationMitigationItemCard key={item.id} item={item} payload={payload} extraDetails={context} findingTypes={findingTypes} />
                      ))}
                    </RemediationTypeSection>
                  );
                })
              : visibleItems.map(item => (
                  <RemediationMitigationItemCard key={item.id} item={item} payload={payload} extraDetails={context} findingTypes={findingTypes} />
                ))}
            {/* Remediation only: the user reads the result first, then applies it and validates. */}
            {isRemediation && visibleItems.length > 0 && (
              <MitigationValidationPanel
                total={group.targets.length}
                state={validation[group.key]}
                onApplied={() => validate(group.key, group.targets.map(target => target.name))}
              />
            )}
          </>
        ),
      };
    }
    if (state?.status === 'loading') {
      return {
        title: group.title,
        body: <Text variant="content-compact" className="text-default-secondary">{isRemediation ? t('Generating remediation…') : t('Generating mitigation…')}</Text>,
      };
    }
    const failed = state?.status === 'error';
    const generateLabel = isRemediation ? t('Generate remediation') : t('Generate mitigation');
    const failedMessage = isRemediation ? t('Remediation generation failed. Try again.') : t('Mitigation generation failed. Try again.');
    const emptyMessage = isRemediation ? t('No remediation generated for this group yet.') : t('No mitigation generated for this group yet.');
    return {
      title: group.title,
      action: (
        <GenerateAiButton onClick={() => generate([group.key])}>
          {failed ? t('Try again') : generateLabel}
        </GenerateAiButton>
      ),
      body: <Empty message={failed ? failedMessage : emptyMessage} />,
    };
  };

  const covers = isRemediation ? t('remediations cover') : t('mitigations cover');
  const coversOne = isRemediation ? t('remediation covers') : t('mitigation covers');
  // Shown in a tooltip when the user hovers the help icon of the Host-level section.
  const header = (
    <span>
      {groups.length === 0
        ? t('The action was prevented on every target, no mitigation needed.')
        : [
            `${n(groups.length)} ${groups.length === 1 ? coversOne : covers} ${n(notPreventedCount)} ${t('targets that were not prevented.')}`,
            preventedCount > 0 ? `${n(preventedCount)} ${t('targets were protected, no mitigation needed.')}` : '',
            validatedCount > 0 ? `${n(validatedCount)} ${t('validated as fixed.')}` : '',
          ].filter(Boolean).join(' ')}
    </span>
  );

  return {
    keys: groups.map(g => g.key),
    findingTypes: [...new Set(groups.flatMap(g => g.findings.map(finding => finding.type)))],
    header,
    renderEntry,
    renderDetail,
  };
};

export default useHostLevelMitigations;
