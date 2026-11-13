import { Button, Chip, Paper, Text } from '@filigran/design-system';
import { BuildOutlined, ShieldOutlined } from '@mui/icons-material';
import { useReducer } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import MitigationCard, { type MitigationFinding, type MockMitigation } from '../../../atomic_testings/atomic_testing/MitigationCard';
import MitigationContextPanel from '../../../atomic_testings/atomic_testing/MitigationContextPanel';
import { buildMitigations } from '../../../atomic_testings/atomic_testing/mitigationMock';

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
    mitigations: MockMitigation[];
    generatedBy: string;
  }
  | { status: 'loading' };

const store = new Map<string, MitigationState>();

interface Props {
  executionLabel: string;
  actionName: string;
  // Command template (sensitive arguments masked).
  command?: string;
  // Findings of the execution. The value is sent only for non-sensitive types (share, port...).
  findings: MitigationFinding[];
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
  command,
  findings,
  attackPatterns,
  targetPlatform,
  agentName,
  agentPrivilege,
  prevented,
}: Props) => {
  const { t } = useFormatter();
  const [, rerender] = useReducer((n: number) => n + 1, 0);

  const hasAgent = !!agentName;
  const types = [...new Set(findings.map(finding => finding.type))].sort();
  // The reuse key: command + finding types + operating system (platform, privilege). Same key, no new request.
  const key = [command ?? actionName, types.join(','), targetPlatform ?? '', hasAgent ? `agent:${agentPrivilege ?? ''}` : 'no-agent'].join('|');
  const state = store.get(key);

  const generate = () => {
    store.set(key, { status: 'loading' });
    rerender();
    setTimeout(() => {
      store.set(key, {
        status: 'ready',
        mitigations: buildMitigations({
          actionName,
          command,
          findings,
          hasAgent,
          platform: targetPlatform,
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
      </div>

      <MitigationContextPanel
        action={actionName}
        command={command}
        findings={findings}
        techniques={attackPatterns}
        environment={hasAgent ? [targetPlatform, agentName, agentPrivilege].filter(Boolean).join(' · ') : undefined}
      />

      {state?.status === 'ready' && (
        <>
          {state.mitigations.map(mitigation => (
            <MitigationCard
              key={mitigation.summary}
              mitigation={mitigation}
              reusedFrom={state.generatedBy !== executionLabel ? state.generatedBy : undefined}
              onRegenerate={generate}
            />
          ))}
        </>
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
