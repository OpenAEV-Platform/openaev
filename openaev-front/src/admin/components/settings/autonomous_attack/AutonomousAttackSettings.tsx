import { Alert } from '@mui/material';
import { useEffect, useState } from 'react';

import { fetchAvailableAgents, fetchDefaultAgents, updateDefaultAgents } from '../../../../actions/autonomous/autonomous-actions';
import { type AdditionalAgent, type AutonomousDiscoveryMode, ORCHESTRATOR_AGENT_ID, SPECIALIST_DEFAULT_DISCOVERY_MODE } from '../../../../actions/autonomous/autonomous-types';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import { MESSAGING$ } from '../../../../utils/Environment';
import useAuth from '../../../../utils/hooks/useAuth';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
import { toHttpUrl } from '../../../../utils/url-helper';
import AutonomousAgentsSelector from '../../autonomous/AutonomousAgentsSelector';
import EnterpriseEditionButton from '../../common/entreprise_edition/EnterpriseEditionButton';
import { SETTINGS_LABEL } from '../../nav/config/settings.config';
import CustomizationMenu from '../CustomizationMenu';

// The license-independent built-in specialist the orchestrator consults; enabled by default but,
// like every other agent, it can be toggled off here to stop the orchestrator consulting it.
const BUILTIN_AGENT_SLUG = 'openaev-payload-creator';

const AutonomousAttackSettings = () => {
  const { t } = useFormatter();
  const { settings } = useAuth();
  const { isValidated: isEnterpriseEdition } = useEnterpriseEdition();

  const xtmOneUrl = toHttpUrl(settings.platform_xtm_one_url)?.replace(/\/+$/, '');
  const createAgentUrl = xtmOneUrl ? `${xtmOneUrl}/agents/new` : undefined;

  const [agents, setAgents] = useState<AdditionalAgent[]>([]);
  const [defaults, setDefaults] = useState<string[]>([]);
  const [modes, setModes] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!isEnterpriseEdition) {
      setLoading(false);
      return;
    }
    setLoading(true);
    Promise.all([fetchAvailableAgents(), fetchDefaultAgents()])
      .then(([available, tenantDefaults]) => {
        setAgents(available.data ?? []);
        setDefaults(tenantDefaults.data?.agent_ids ?? []);
        setModes(tenantDefaults.data?.agent_modes ?? {});
      })
      .catch(() => {
        setAgents([]);
        setDefaults([]);
        setModes({});
      })
      .finally(() => setLoading(false));
  }, [isEnterpriseEdition]);

  const persist = (nextDefaults: string[], nextModes: Record<string, string>) => {
    const previousDefaults = defaults;
    const previousModes = modes;
    setDefaults(nextDefaults);
    setModes(nextModes);
    setSaving(true);
    updateDefaultAgents(nextDefaults, nextModes)
      .then((res) => {
        setDefaults(res.data?.agent_ids ?? nextDefaults);
        setModes(res.data?.agent_modes ?? nextModes);
      })
      .catch(() => {
        setDefaults(previousDefaults);
        setModes(previousModes);
        MESSAGING$.notifyError(t('Failed to update default agents'));
      })
      .finally(() => setSaving(false));
  };

  const toggle = (agentId: string, enabled: boolean) => {
    const nextDefaults = enabled ? [...defaults, agentId] : defaults.filter(id => id !== agentId);
    // Seed the specialist default (EXPANSIVE) when enabling so the run always carries an explicit
    // per-agent mode; the orchestrator row is never toggled here.
    const nextModes = { ...modes };
    if (enabled && !nextModes[agentId]) {
      nextModes[agentId] = SPECIALIST_DEFAULT_DISCOVERY_MODE;
    }
    persist(nextDefaults, nextModes);
  };

  const changeMode = (agentId: string, mode: AutonomousDiscoveryMode) => {
    persist(defaults, { ...modes, [agentId]: mode });
  };

  return (
    <div style={{ display: 'flex' }}>
      <div style={{ flexGrow: 1 }}>
        <Breadcrumbs
          variant="list"
          elements={[{ label: t(SETTINGS_LABEL) }, { label: t('Customization') }, {
            label: t('Autonomous attack'),
            current: true,
          }]}
        />
        {!isEnterpriseEdition
          ? (
              <Alert severity="info" variant="outlined" action={<EnterpriseEditionButton />}>
                {t('Custom agents for the autonomous orchestrator are an Enterprise Edition feature.')}
              </Alert>
            )
          : (
              <AutonomousAgentsSelector
                agents={agents}
                enabledIds={defaults}
                onToggle={toggle}
                modes={modes}
                onModeChange={changeMode}
                orchestrator={{
                  id: ORCHESTRATOR_AGENT_ID,
                  name: t('Autonomous orchestrator'),
                  description: t('Plans and drives the entire attack path, and consults the agents below.'),
                }}
                builtinSlug={BUILTIN_AGENT_SLUG}
                loading={loading}
                disabled={saving}
                createAgentUrl={createAgentUrl}
                infoTooltip={t('Specialist agents the orchestrator consults by default on every new autonomous attack (payload creation, code generation, recon, exploitation support). Built-in agents are enabled by default; each run can still add or remove agents at launch. Each agent\'s discovery mode controls how much it may create from recon: enrich existing entities only, stay within scope, or expand the perimeter.')}
              />
            )}
      </div>
      <CustomizationMenu />
    </div>
  );
};

export default AutonomousAttackSettings;
