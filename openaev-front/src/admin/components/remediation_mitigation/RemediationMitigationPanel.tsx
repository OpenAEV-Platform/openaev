// fds:keep-mui Alert: the library has no banner/alert component yet
import { Button, Chip, IconButton, Paper, Tabs, TabsList, TabsTrigger, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { ExpandLess, ExpandMore, HelpOutlined } from '@mui/icons-material';
import { Alert } from '@mui/material';
import { type ReactNode, useMemo, useState } from 'react';

import Empty from '../../../components/Empty';
import { useFormatter } from '../../../components/i18n';
import { tint } from '../../../utils/tint';
import GenerateAiButton from './GenerateAiButton';
import GenerateDialog, { type GenerateDefaults } from './GenerateDialog';
import RemediationMitigationItemCard from './RemediationMitigationItemCard';
import {
  type CollectorType,
  type ControlType,
  MITIGATION_CONTROL_TYPES,
  REMEDIATION_CONTROL_TYPES,
  type RemediationMitigationCategory,
} from './remediationMitigationMock';
import {
  generateItem,
  getItems,
  resetMockData,
  resolvePayload,
  unavailableKey,
  useMockStore,
  VENDORS,
  type Verdict,
} from './remediationMitigationStore';
import RemediationTypeSection from './RemediationTypeSection';
import useRemediationMitigationLabels from './useRemediationMitigationLabels';
import VendorLogo from './VendorLogo';
import { type RequestContext } from './xtmOneRequest';

const ALL = 'all';
const GENERIC_ENTRY = 'generic';
const VENDOR_PREFIX = 'vendor:';
const HOST_PREFIX = 'host:';

// What the attack path step observed. Drives the smart hints.
export interface PanelContext {
  findingType?: string;
  verdicts?: {
    prevention?: Verdict;
    detection?: Verdict;
    vulnerability?: Verdict;
  };
}

// Host-level mitigations: groups of assets, one entry per distinct set of request inputs. The owner of
// the data (the atomic testing) renders the entries and the detail of each group.
// What the pane of a host-level group shows: same as a vendor-specific one.
export interface HostLevelDetail {
  title: string;
  action?: ReactNode;
  body: ReactNode;
}

export interface HostLevel {
  keys: string[];
  // Types of the findings the action produced, over every group: shown on the vendor-specific cards too.
  findingTypes?: string[];
  header?: ReactNode;
  renderEntry: (key: string, selected: boolean, onSelect: () => void) => ReactNode;
  renderDetail: (key: string, controlFilter: string) => HostLevelDetail | null;
}

interface Props {
  category: RemediationMitigationCategory;
  // Name of the payload (or inject) the tab is for. Resolved to a sample payload.
  payloadName?: string | null;
  // Review / edit / delete (Threat Arsenal and attack path). Atomic testing is read-mostly.
  editable?: boolean;
  context?: PanelContext;
  // Mitigation only: when there are assets (atomic testing), the host-level entries are their groups.
  hostLevel?: HostLevel;
  // Lets a prevention item jump to the Remediation tab of the same payload.
  onSwitchToRemediation?: () => void;
}

const listStyle = {
  display: 'flex',
  flexDirection: 'column' as const,
  gap: 4,
  minWidth: 280,
  maxWidth: 320,
  flexShrink: 0,
};

// The content of the "Remediation" and "Mitigation" tabs: same layout, driven by the category.
// PROTOTYPE: sample data in memory, every "AI" action is simulated.
const RemediationMitigationPanel = ({
  category,
  payloadName,
  editable = false,
  context,
  hostLevel,
  onSwitchToRemediation,
}: Props) => {
  const { t } = useFormatter();
  const labels = useRemediationMitigationLabels();
  const store = useMockStore();
  const payload = useMemo(() => resolvePayload(payloadName), [payloadName]);
  const isMitigation = category === 'mitigation';
  // A mitigation is either a detection or a prevention, and the vendors that can generate it depend on that.
  const [controlFilter, setControlFilter] = useState<string>(isMitigation ? 'detection' : ALL);
  const [selected, setSelected] = useState<string | null>(null);
  const [collapsed, setCollapsed] = useState<{
    host: boolean;
    vendor: boolean;
  }>({
    host: false,
    vendor: false,
  });
  const [dialog, setDialog] = useState<{
    open: boolean;
    defaults?: GenerateDefaults;
  }>({ open: false });

  const controlTypes: ControlType[] = isMitigation ? MITIGATION_CONTROL_TYPES : REMEDIATION_CONTROL_TYPES;
  const items = getItems(store, payload.id, category);

  // Only the SIEM / EDR products OpenAEV supports (a collector exists) that can do the selected control type
  // (CrowdStrike appears under both). Unsupported products are not shown.
  const vendors = VENDORS.filter(vendor => vendor.controls.includes(controlFilter as ControlType));
  const supportedVendors = vendors.filter(vendor => vendor.collector);
  const hostEntries = hostLevel ? hostLevel.keys.map(key => `${HOST_PREFIX}${key}`) : [GENERIC_ENTRY];
  // A remediation has no vendor: its groups of assets are its only list.
  const entries = [...hostEntries, ...(isMitigation ? supportedVendors.map(vendor => `${VENDOR_PREFIX}${vendor.collector}`) : [])];
  const current = selected && entries.includes(selected) ? selected : entries[0];

  // A CVE finding already has its fix in the platform: no new content is generated for it.
  const isCve = !isMitigation && context?.findingType === 'cve';

  const requestContext: RequestContext | undefined = context
    ? {
        findingType: context.findingType,
        verdict: (['prevention', 'detection', 'vulnerability'] as const)
          .flatMap(name => (context.verdicts?.[name]
            ? [{
                name,
                value: context.verdicts[name] as Verdict,
              }]
            : []))[0],
      }
    : undefined;

  // Vendor-specific mitigation: no dialog, the open control type tab and vendor tab are the request.
  const [generatingKey, setGeneratingKey] = useState<string | null>(null);
  const generateForTab = (controlType: 'detection' | 'prevention', collector: CollectorType) => {
    const key = `${controlType}|${collector}`;
    setGeneratingKey(key);
    generateItem({
      payload,
      category,
      controlType,
      collector,
      findingType: context?.findingType,
    }).finally(() => setGeneratingKey(null));
  };

  const openDialog = (defaults?: GenerateDefaults) => setDialog({
    open: true,
    defaults,
  });

  const hints: {
    key: string;
    text: string;
    defaults?: GenerateDefaults;
  }[] = [];
  if (isMitigation && context?.verdicts?.detection === 'not_met') {
    hints.push({
      key: 'detection',
      text: t('Detection = Not met'),
      defaults: { controlType: 'detection' },
    });
  }
  if (isMitigation && context?.verdicts?.prevention === 'not_met') {
    hints.push({
      key: 'prevention',
      text: t('Prevention = Not met'),
      defaults: {
        controlType: 'prevention',
        collector: 'crowdstrike',
      },
    });
  }
  if (!isMitigation && context?.verdicts?.vulnerability === 'met' && !isCve) {
    hints.push({
      key: 'vulnerability',
      text: t('Vulnerability = Met'),
    });
  }
  const hintLabel = (key: string) => {
    if (key === 'detection') return t('Generate detection mitigation');
    if (key === 'prevention') return t('Generate prevention mitigation');
    return t('Generate remediation');
  };

  const card = (item: (typeof items)[number]) => (
    <RemediationMitigationItemCard
      key={item.id}
      item={item}
      payload={payload}
      findingTypes={context?.findingType ? [context.findingType] : hostLevel?.findingTypes}
      payloadId={payload.id}
      editable={editable}
      onSwitchToRemediation={onSwitchToRemediation}
    />
  );

  const emptyMessage = isMitigation
    ? t('No mitigation available for this action yet.')
    : t('No remediation available for this action yet.');

  const entryButton = (key: string, label: string, count: number) => (
    <Button
      key={key}
      priority={key === current ? 'secondary' : 'tertiary'}
      style={{
        width: '100%',
        justifyContent: 'space-between',
        textAlign: 'left',
      }}
      onClick={() => setSelected(key)}
    >
      <span>{label}</span>
      <span style={{ opacity: 0.7 }}>{count}</span>
    </Button>
  );

  // A section of the left list: a bar bigger than the entries it folds, entries are indented under it.
  const sectionHeader = (section: 'host' | 'vendor', label: string) => (
    <Button
      priority="tertiary"
      aria-expanded={!collapsed[section]}
      endIcon={collapsed[section] ? <ExpandMore fontSize="small" /> : <ExpandLess fontSize="small" />}
      style={{
        width: '100%',
        justifyContent: 'space-between',
        fontSize: 17,
        fontWeight: 700,
        textTransform: 'uppercase',
        letterSpacing: '0.04em',
        padding: '10px 12px',
        marginTop: section === 'vendor' ? 12 : 0,
        backgroundColor: tint('currentColor', 10),
        borderRadius: 4,
      }}
      onClick={() => setCollapsed(prev => ({
        ...prev,
        [section]: !prev[section],
      }))}
    >
      {label}
    </Button>
  );

  const sectionBody = (children: ReactNode) => (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 4,
      marginLeft: 8,
      paddingLeft: 8,
      borderLeft: `2px solid ${tint('currentColor', 20)}`,
    }}
    >
      {children}
    </div>
  );

  const hostHelp = hostLevel?.header
    ? (
        <Tooltip>
          <TooltipTrigger asChild>
            <IconButton
              aria-label={t('Host-level summary')}
              priority="tertiary"
              icon={<HelpOutlined fontSize="small" />}
            />
          </TooltipTrigger>
          <TooltipContent>{hostLevel.header}</TooltipContent>
        </Tooltip>
      )
    : null;

  // The pane of the selected entry: a Paper with its title and its Generate button in the top right corner.
  const pane = (title: ReactNode, action: ReactNode, body: ReactNode) => (
    <div style={{
      display: 'grid',
      gridTemplateColumns: 'minmax(0, 1fr)',
    }}
    >
      <Paper
        padding={16}
        title={title}
        action={hostHelp
          ? (
              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
              }}
              >
                {hostHelp}
                {action}
              </div>
            )
          : action}
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 12,
        }}
      >
        {body}
      </Paper>
    </div>
  );

  const hostEntriesNode = hostLevel
    ? hostLevel.keys.map(key => (
        <div key={key}>
          {hostLevel.renderEntry(key, current === `${HOST_PREFIX}${key}`, () => setSelected(`${HOST_PREFIX}${key}`))}
        </div>
      ))
    : entryButton(
        GENERIC_ENTRY,
        t('Generic'),
        items.filter(item => item.scope === 'generic' && item.control_type === controlFilter).length,
      );

  const renderMitigationBody = () => {
    if (current.startsWith(HOST_PREFIX) && hostLevel) {
      const detail = hostLevel.renderDetail(current.slice(HOST_PREFIX.length), controlFilter);
      return detail
        ? pane(
            detail.title,
            detail.action,
            detail.body,
          )
        : null;
    }
    const collector = current.startsWith(VENDOR_PREFIX) ? current.slice(VENDOR_PREFIX.length) as CollectorType : undefined;
    const vendor = VENDORS.find(entry => entry.collector === collector);
    const visible = items.filter(item => item.control_type === controlFilter
      && (collector ? item.collector_type === collector : item.scope === 'generic'));
    // XTM One answered that it has no suggestion for this request: say so, with its reason.
    const reason = store.unavailable[unavailableKey(payload.id, 'mitigation', controlFilter as ControlType, collector ?? 'generic')];
    const noSuggestion = reason
      ? `${labels.controlType[controlFilter as ControlType]} ${labels.category.mitigation.toLowerCase()}: ${t('XTM One has no suggestion for this request.')} ${reason}`
      : emptyMessage;
    return pane(
      vendor
        ? (
            <span style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 8,
            }}
            >
              <VendorLogo vendorId={vendor.id} label={vendor.label} size={22} />
              {`${vendor.label} (${vendor.kind})`}
            </span>
          )
        : t('Generic'),
      <GenerateAiButton
        disabled={!!generatingKey}
        onClick={() => (collector
          ? generateForTab(controlFilter as 'detection' | 'prevention', collector)
          : openDialog({
              controlType: controlFilter as 'detection' | 'prevention',
              collector: 'generic',
            }))}
      >
        {generatingKey === `${controlFilter}|${collector}` ? t('Generating…') : t('Generate mitigation')}
      </GenerateAiButton>,
      visible.length === 0 ? <Empty message={noSuggestion} /> : visible.map(card),
    );
  };

  // Remediation without assets (Threat Arsenal, attack path): one pane, Generate in its top right corner.
  const renderRemediationBody = () => {
    const reason = store.unavailable[unavailableKey(payload.id, 'remediation')];
    return pane(
      t('Remediation'),
      <GenerateAiButton disabled={isCve} onClick={() => openDialog()}>
        {t('Generate remediation')}
      </GenerateAiButton>,
      // One section per control type; a type without any suggestion says so.
      REMEDIATION_CONTROL_TYPES.map((controlType) => {
        const ofType = items.filter(item => item.control_type === controlType);
        const title = `${labels.controlType[controlType]} ${labels.category.remediation.toLowerCase()}`;
        return (
          <RemediationTypeSection
            key={controlType}
            title={title}
            hasItems={ofType.length > 0}
            emptyText={`${t('No suggestion available.')}${reason ? ` ${t('XTM One has no suggestion for this request.')} ${reason}` : ''}`}
          >
            {ofType.map(card)}
          </RemediationTypeSection>
        );
      }),
    );
  };

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 12,
    }}
    >
      {isCve && (
        <Alert severity="info" variant="outlined">
          {t('Vulnerability record remediation: the fix for this CVE already exists in the vulnerability record of the platform, so nothing new is generated here. (Sample text.)')}
        </Alert>
      )}

      {hints.map(hint => (
        <Alert
          key={hint.key}
          severity="warning"
          variant="outlined"
          action={<Button size="sm" variant="ia" onClick={() => openDialog(hint.defaults)}>{hintLabel(hint.key)}</Button>}
        >
          {`${hint.text}. ${t('Suggested next step')}: ${hintLabel(hint.key)}.`}
        </Alert>
      ))}

      {isMitigation && (
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
        }}
        >
          <div style={{
            flex: 1,
            minWidth: 0,
          }}
          >
            <Tabs value={controlFilter} onValueChange={setControlFilter} panels="external">
              <TabsList aria-label={t('Control type')}>
                {!isMitigation && <TabsTrigger value={ALL}>{t('All')}</TabsTrigger>}
                {controlTypes.map(controlType => (
                  <TabsTrigger key={controlType} value={controlType}>{labels.controlType[controlType]}</TabsTrigger>
                ))}
              </TabsList>
            </Tabs>
          </div>
        </div>
      )}

      {isMitigation || hostLevel
        ? (
            <div style={{
              display: 'flex',
              gap: 16,
              alignItems: 'flex-start',
            }}
            >
              <div role="list" aria-label={t('Mitigation sources')} style={listStyle}>
                {isMitigation
                  ? (
                      <>
                        {sectionHeader('host', t('Host-level'))}
                        {!collapsed.host && sectionBody(hostEntriesNode)}
                      </>
                    )
                  : (
                      // A remediation has no vendor: no section title, only the groups of assets.
                      <>
                        {hostEntriesNode}
                      </>
                    )}
                {isMitigation && (
                  <>
                    {sectionHeader('vendor', t('Vendor-specific'))}
                    {!collapsed.vendor && sectionBody(
                      // Same rail as the security platforms of the main Remediation tab: vertical library
                      // tabs, logo + name. No tab is active while a host-level entry is selected.
                      <Tabs
                        orientation="vertical"
                        value={current.startsWith(VENDOR_PREFIX) ? current : ''}
                        onValueChange={setSelected}
                        panels="external"
                        style={{ display: 'grid' }}
                      >
                        <TabsList aria-label={t('Vendor-specific')}>
                          {supportedVendors.map(vendor => (
                            <TabsTrigger
                              key={vendor.id}
                              value={`${VENDOR_PREFIX}${vendor.collector}`}
                              icon={<VendorLogo vendorId={vendor.id} label={vendor.label} />}
                            >
                              {`${vendor.label} (${vendor.kind})`}
                            </TabsTrigger>
                          ))}
                        </TabsList>
                      </Tabs>,
                    )}
                  </>
                )}
              </div>
              <div style={{
                flex: 1,
                minWidth: 0,
              }}
              >
                {renderMitigationBody()}
              </div>
            </div>
          )
        : (
            <div style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 12,
            }}
            >
              {renderRemediationBody()}
            </div>
          )}

      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
      }}
      >
        <Chip label={t('SAMPLE DATA - prototype, nothing is stored or sent')} severity="medium" />
        <Button size="sm" priority="tertiary" onClick={resetMockData}>{t('Reset mock data')}</Button>
      </div>

      <GenerateDialog
        open={dialog.open}
        onClose={() => setDialog({ open: false })}
        payload={payload}
        category={category}
        context={requestContext}
        defaults={dialog.defaults}
      />
    </div>
  );
};

export default RemediationMitigationPanel;
