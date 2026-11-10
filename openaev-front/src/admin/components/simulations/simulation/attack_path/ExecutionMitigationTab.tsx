import { Button, Chip, Paper, Text } from '@filigran/design-system';
import { BuildOutlined, ShieldOutlined } from '@mui/icons-material';
import { type ReactNode, useReducer } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import MitigationCard, { type MockMitigation } from '../../../atomic_testings/atomic_testing/MitigationCard';
import { buildMitigation } from '../../../atomic_testings/atomic_testing/mitigationMock';

// ---------------------------------------------------------------------------------------------
// PROTOTYPE - MOCK DATA ONLY. Nothing is sent to the backend or to XTM One.
// Mitigation tab of the attack path execution panel: one execution = one agent (or none for an
// injector based execution). The suggestion is driven by the ACTION (payload or injector) and by
// the TYPES of finding it produced; the agent environment only refines it when there is one.
// Generated on click, only when the action was NOT prevented, and reused when the whole input
// (action + finding types + environment) is the same: module level store below.
// ---------------------------------------------------------------------------------------------

type MitigationState
  = | {
    status: 'ready';
    mitigation: MockMitigation;
    generatedBy: string;
  }
  | { status: 'loading' };

const store = new Map<string, MitigationState>();

interface Props {
  executionLabel: string;
  actionName: string;
  // Types only (credentials, share, portscan...): finding values are masked and never sent.
  findingTypes: string[];
  attackPatterns: string[];
  targetPlatform?: string;
  agentName?: string;
  agentPrivilege?: string;
  // True when a security platform prevented the action: no mitigation is needed then.
  prevented: boolean;
}

const ExecutionMitigationTab = ({
  executionLabel,
  actionName,
  findingTypes,
  attackPatterns,
  targetPlatform,
  agentName,
  agentPrivilege,
  prevented,
}: Props) => {
  const { t } = useFormatter();
  const [, rerender] = useReducer((n: number) => n + 1, 0);

  const hasAgent = !!agentName;
  const types = [...new Set(findingTypes)].sort();
  // Every input that shapes the suggestion is part of the key: same key, no new request.
  const key = [actionName, types.join(','), targetPlatform ?? '', hasAgent ? `agent:${agentPrivilege ?? ''}` : 'no-agent'].join('|');
  const state = store.get(key);

  const generate = () => {
    store.set(key, { status: 'loading' });
    rerender();
    setTimeout(() => {
      store.set(key, {
        status: 'ready',
        mitigation: buildMitigation({
          actionName,
          findingTypes: types,
          hasAgent,
        }),
        generatedBy: executionLabel,
      });
      rerender();
    }, 1800);
  };

  const centered = {
    display: 'flex',
    flexDirection: 'column' as const,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
    textAlign: 'center' as const,
    minHeight: 160,
  };

  if (prevented) {
    return (
      <Paper padding={24} style={centered}>
        <ShieldOutlined sx={{ fontSize: 40 }} />
        <Text variant="content-compact" className="text-default-secondary">
          {t('This action was stopped, no mitigation needed.')}
        </Text>
      </Paper>
    );
  }

  const contextRow = (label: string, value: ReactNode) => (
    <div>
      <Text variant="content-caption" className="block text-default-secondary">{label}</Text>
      <div style={{
        display: 'flex',
        flexWrap: 'wrap',
        gap: 4,
      }}
      >
        {value}
      </div>
    </div>
  );

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
      }}
      >
        <Chip label={t('Prototype - mock data')} severity="medium" />
        <Chip label={t('Not prevented')} severity="high" />
      </div>

      <Paper
        padding={16}
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 12,
        }}
      >
        <Text variant="content-base-bold" className="text-default-secondary">{t('Context sent to XTM One')}</Text>
        {contextRow(t('Action'), <Text variant="content-compact">{actionName}</Text>)}
        {contextRow(
          t('Finding types'),
          types.length > 0
            ? types.map(type => <Chip key={type} label={type} severity="neutral" />)
            : <Text variant="content-compact">{t('None')}</Text>,
        )}
        {attackPatterns.length > 0 && contextRow(
          t('Techniques'),
          attackPatterns.map(ap => <Chip key={ap} label={ap} severity="neutral" />),
        )}
        {contextRow(
          t('Environment'),
          <Text variant="content-compact">
            {hasAgent
              ? `${agentName} · ${agentPrivilege ?? '-'}`
              : t('No agent (injector based execution): the suggestion relies on the action and its findings.')}
          </Text>,
        )}
      </Paper>

      {state?.status === 'ready' && (
        <MitigationCard
          mitigation={state.mitigation}
          reusedFrom={state.generatedBy !== executionLabel ? state.generatedBy : undefined}
          onRegenerate={generate}
        />
      )}
      {state?.status === 'loading' && (
        <Paper padding={24} style={centered}>
          <Text variant="content-compact" className="text-default-secondary">{t('Generating mitigation…')}</Text>
        </Paper>
      )}
      {!state && (
        <Paper padding={24} style={centered}>
          <BuildOutlined sx={{ fontSize: 40 }} />
          <Text variant="content-compact" className="text-default-secondary">
            {t('No mitigation generated for this action and findings yet.')}
          </Text>
          <Button variant="ia" priority="secondary" onClick={generate}>{t('Generate mitigation')}</Button>
        </Paper>
      )}
    </div>
  );
};

export default ExecutionMitigationTab;
