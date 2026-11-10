import { Button, Chip, Paper, Tabs, TabsList, TabsTrigger, Text } from '@filigran/design-system';
import { BuildOutlined, InfoOutlined, ShieldOutlined } from '@mui/icons-material';
import { useRef, useState } from 'react';

import { useFormatter } from '../../../../components/i18n';
import MitigationCard, { type MockMitigation } from './MitigationCard';
import { MOCK_MITIGATIONS } from './mitigationMock';

// ---------------------------------------------------------------------------------------------
// PROTOTYPE - MOCK DATA ONLY.
// Nothing here talks to the backend or to XTM One. It exists to show the proposed UX of a
// "Mitigations" tab on the atomic testing page: one entry per agent (execution), generated on
// click for NOT PREVENTED results only, and reused when action + environment are identical.
// ---------------------------------------------------------------------------------------------

type MockResult = 'NOT_PREVENTED' | 'PREVENTED' | 'DETECTED';

interface MockAgent {
  id: string;
  name: string;
  result: MockResult;
  // Execution environment sent to XTM One. Undefined for network based executions (no agent).
  environment?: {
    os: string;
    arch: string;
    privilege: string;
    executor: string;
  };
}

type MitigationState
  = | {
    status: 'ready';
    mitigation: MockMitigation;
    generatedBy: string;
  }
  | { status: 'loading' }
  | { status: 'error' };

const MOCK_AGENTS: MockAgent[] = [
  {
    id: 'a1',
    name: 'WIN-DC01',
    result: 'NOT_PREVENTED',
    environment: {
      os: 'Windows 10 22H2',
      arch: 'x86_64',
      privilege: 'admin (SYSTEM)',
      executor: 'Tanium',
    },
  },
  {
    id: 'a2',
    name: 'WIN-FIN-07',
    result: 'NOT_PREVENTED',
    // Same environment as WIN-DC01 -> the stored mitigation is reused, no second XTM One call.
    environment: {
      os: 'Windows 10 22H2',
      arch: 'x86_64',
      privilege: 'admin (SYSTEM)',
      executor: 'Tanium',
    },
  },
  {
    id: 'a3',
    name: 'ubuntu-web-03',
    result: 'NOT_PREVENTED',
    environment: {
      os: 'Ubuntu 22.04',
      arch: 'x86_64',
      privilege: 'standard (www-data)',
      executor: 'OpenAEV agent',
    },
  },
  {
    id: 'a4',
    name: 'WIN-HR-12',
    result: 'PREVENTED',
    environment: {
      os: 'Windows 11 23H2',
      arch: 'x86_64',
      privilege: 'standard',
      executor: 'CrowdStrike',
    },
  },
  {
    id: 'a5',
    name: '10.20.0.14 (network scan)',
    result: 'NOT_PREVENTED',
    // Network based execution: no agent, so no environment. We still suggest, with a notice.
  },
];

// The "do not ask twice" key: everything that shapes the suggestion.
const environmentKey = (agent: MockAgent) => {
  if (!agent.environment) return 'network';
  const {
    os,
    arch,
    privilege,
    executor,
  } = agent.environment;
  return [os, arch, privilege, executor].join('|');
};

const mitigationFor = (agent: MockAgent): MockMitigation => {
  if (!agent.environment) return MOCK_MITIGATIONS.network;
  return agent.environment.os.startsWith('Windows') ? MOCK_MITIGATIONS.windows : MOCK_MITIGATIONS.linux;
};

const AtomicTestingMitigations = () => {
  const { t } = useFormatter();
  const [activeTab, setActiveTab] = useState<number>(0);
  // Stored mitigations keyed by action + environment: this is the "do not ask twice" rule.
  const [store, setStore] = useState<Record<string, MitigationState>>({});
  // Demo only: the first attempt for the Linux host fails so the error state can be seen.
  const failedOnce = useRef(false);

  const agent = MOCK_AGENTS[activeTab];
  const key = environmentKey(agent);
  const state = store[key];

  const generate = (target: MockAgent) => {
    const k = environmentKey(target);
    setStore(prev => ({
      ...prev,
      [k]: { status: 'loading' },
    }));
    setTimeout(() => {
      const shouldFail = target.environment?.os.startsWith('Ubuntu') && !failedOnce.current;
      if (shouldFail) {
        failedOnce.current = true;
        setStore(prev => ({
          ...prev,
          [k]: { status: 'error' },
        }));
        return;
      }
      setStore(prev => ({
        ...prev,
        [k]: {
          status: 'ready',
          mitigation: mitigationFor(target),
          generatedBy: target.name,
        },
      }));
    }, 1800);
  };

  const resultChip = (result: MockResult) => {
    if (result === 'NOT_PREVENTED') return <Chip label={t('Not prevented')} severity="high" />;
    if (result === 'DETECTED') return <Chip label={t('Detected')} severity="info" />;
    return <Chip label={t('Prevented')} severity="low" />;
  };

  const renderEnvironment = () => (
    <Paper
      padding={16}
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 8,
      }}
    >
      <Text variant="content-base-bold" className="text-default-secondary">
        {t('Context sent to XTM One')}
      </Text>
      {agent.environment
        ? (
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))',
              gap: 12,
            }}
            >
              {([
                [t('Operating system'), agent.environment.os],
                [t('Architecture'), agent.environment.arch],
                [t('Privilege'), agent.environment.privilege],
                [t('Executor'), agent.environment.executor],
              ] as [string, string][]).map(([label, value]) => (
                <div key={label}>
                  <Text variant="content-caption" className="block text-default-secondary">{label}</Text>
                  <Text variant="content-compact" className="block">{value}</Text>
                </div>
              ))}
            </div>
          )
        : (
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
            }}
            >
              <InfoOutlined fontSize="small" />
              <Text variant="content-compact">
                {t('Network based execution: no agent, so the environment is unknown. The suggestion will be more generic.')}
              </Text>
            </div>
          )}
    </Paper>
  );

  const renderMitigation = (mitigation: MockMitigation, generatedBy: string) => (
    <MitigationCard
      mitigation={mitigation}
      reusedFrom={generatedBy !== agent.name ? generatedBy : undefined}
      onRegenerate={() => generate(agent)}
    />
  );

  const centered = {
    display: 'flex',
    flexDirection: 'column' as const,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
    textAlign: 'center' as const,
    flex: 1,
    minHeight: 180,
  };

  const renderBody = () => {
    if (agent.result !== 'NOT_PREVENTED') {
      return (
        <Paper padding={32} style={centered}>
          <ShieldOutlined sx={{ fontSize: 44 }} />
          <Text variant="content-compact" className="text-default-secondary">
            {t('This action was stopped on this agent, no mitigation needed.')}
          </Text>
        </Paper>
      );
    }
    if (state?.status === 'ready') {
      return (
        <>
          {renderEnvironment()}
          {renderMitigation(state.mitigation, state.generatedBy)}
        </>
      );
    }
    if (state?.status === 'loading') {
      return (
        <>
          {renderEnvironment()}
          <Paper padding={32} style={centered}>
            <Text variant="content-compact" className="text-default-secondary">
              {t('Generating mitigation…')}
            </Text>
          </Paper>
        </>
      );
    }
    return (
      <>
        {renderEnvironment()}
        <Paper padding={32} style={centered}>
          <BuildOutlined sx={{ fontSize: 44 }} />
          <Text variant="content-compact" className="text-default-secondary">
            {state?.status === 'error'
              ? t('Mitigation generation failed. Try again.')
              : t('No mitigation generated for this environment yet.')}
          </Text>
          <Button variant="ia" priority="secondary" onClick={() => generate(agent)}>
            {state?.status === 'error' ? t('Try again') : t('Generate mitigation')}
          </Button>
        </Paper>
      </>
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
      }}
      >
        <Chip label={t('Prototype - mock data')} severity="medium" />
        <Text variant="content-caption" className="text-default-secondary">
          {t('Nothing is sent to XTM One. Generate on WIN-DC01, then open WIN-FIN-07: same environment, no second request.')}
        </Text>
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
          value={String(activeTab)}
          onValueChange={value => setActiveTab(Number(value))}
          panels="external"
          style={{
            display: 'grid',
            minWidth: 220,
            flexShrink: 0,
          }}
        >
          <TabsList aria-label={t('Agents')}>
            {MOCK_AGENTS.map((a, index) => (
              <TabsTrigger key={a.id} value={String(index)}>
                {a.name}
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
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            minHeight: 36,
          }}
          >
            <ShieldOutlined fontSize="small" />
            <Text variant="content-base-bold">{t('Mitigation')}</Text>
            {resultChip(agent.result)}
          </div>
          {renderBody()}
        </div>
      </div>
    </div>
  );
};

export default AtomicTestingMitigations;
