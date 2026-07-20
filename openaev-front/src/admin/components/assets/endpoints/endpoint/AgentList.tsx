import { DevicesOtherOutlined } from '@mui/icons-material';
import { Box, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode } from 'react';

import { fetchExecutors } from '../../../../../actions/executors/executor-action';
import { type ExecutorHelper } from '../../../../../actions/executors/executor-helper';
import type { LoggedHelper } from '../../../../../actions/helper';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type AgentOutput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { buildTenantApiPath } from '../../../../../utils/url-helper';
import EEChip from '../../../common/entreprise_edition/EEChip';
import AssetStatus from '../../AssetStatus';
import AgentDeploymentMode from '../AgentDeploymentMode';
import AgentPrivilege from '../AgentPrivilege';
import AgentLastSeen from './AgentLastSeen';

// A tiny labeled meta cell inside an agent card (overline label + value).
const MetaItem = ({ label, children }: {
  label: string;
  children: ReactNode;
}) => (
  <div style={{ minWidth: 0 }}>
    <Typography sx={{
      fontFamily: '"Geologica", sans-serif',
      fontWeight: 600,
      fontSize: 10,
      letterSpacing: '0.1em',
      textTransform: 'uppercase',
      color: 'text.secondary',
      marginBottom: 0.25,
    }}
    >
      {label}
    </Typography>
    <Box sx={{
      display: 'flex',
      alignItems: 'center',
      minHeight: 24,
      fontSize: 13,
    }}
    >
      {children}
    </Box>
  </div>
);

interface Props { agents: AgentOutput[] }

/**
 * Agents installed on an asset, rendered as compact cards: an asset usually
 * carries one or two agents, so a full seven-column table was overkill. Each
 * card shows the executor identity, liveliness, and the deployment metadata
 * at a glance.
 */
const AgentList: FunctionComponent<Props> = ({ agents }) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { settings, executorsMap } = useHelper((helper: ExecutorHelper & LoggedHelper) => ({
    settings: helper.getPlatformSettings(),
    executorsMap: helper.getExecutorsMap(),
  }));
  useDataLoader(() => {
    dispatch(fetchExecutors());
  });

  return (
    <Box sx={{
      display: 'grid',
      gridTemplateColumns: 'repeat(auto-fill, minmax(380px, 1fr))',
      gap: 2,
    }}
    >
      {agents.map((agent) => {
        const executorId = agent.agent_executor?.executor_id;
        const executor = executorId ? executorsMap[executorId] : undefined;
        const showEEChip = !settings.platform_license?.license_is_validated
          && (executor?.executor_type === 'openaev_tanium'
            || executor?.executor_type === 'openaev_crowdstrike_executor'
            || executor?.executor_type === 'openaev_sentinelone_executor'
            || executor?.executor_type === 'openaev_paloaltocortex_executor');
        return (
          <Box
            key={agent.agent_id}
            data-testid="asset-agent-card"
            sx={{
              border: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
              borderRadius: 1,
              padding: 1.5,
              display: 'flex',
              flexDirection: 'column',
              gap: 1.5,
              background: `linear-gradient(135deg, ${alpha(theme.palette.primary.main, 0.04)}, transparent 55%)`,
            }}
          >
            {/* Identity row: executor icon, agent name + executor, status. */}
            <Box sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1.5,
            }}
            >
              <Box sx={{
                width: 38,
                height: 38,
                borderRadius: 1,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0,
                backgroundColor: alpha(theme.palette.text.primary, 0.04),
                border: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
              }}
              >
                {executor
                  ? (
                      <img
                        src={buildTenantApiPath(`/api/images/executors/icons/${executor.executor_type}`)}
                        alt={executor.executor_type}
                        style={{
                          width: 22,
                          height: 22,
                          borderRadius: 4,
                        }}
                      />
                    )
                  : <DevicesOtherOutlined color="primary" sx={{ fontSize: 20 }} />}
              </Box>
              <Box sx={{
                minWidth: 0,
                flex: 1,
              }}
              >
                <Tooltip title={agent.agent_executed_by_user ?? ''}>
                  <Typography sx={{
                    fontSize: 13.5,
                    fontWeight: 600,
                    lineHeight: 1.35,
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                  }}
                  >
                    {agent.agent_executed_by_user}
                  </Typography>
                </Tooltip>
                <Box sx={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 0.5,
                  minWidth: 0,
                }}
                >
                  <Typography sx={{
                    fontSize: 12,
                    color: 'text.secondary',
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                  }}
                  >
                    {executor?.executor_name ?? t('Unknown')}
                  </Typography>
                  {showEEChip && (
                    <EEChip clickable featureDetectedInfo={executor?.executor_name} />
                  )}
                </Box>
              </Box>
              <AssetStatus variant="list" status={agent.agent_active ? 'Active' : 'Inactive'} />
            </Box>
            {/* Meta row: privilege / deployment / version / last seen. */}
            <Box sx={{
              display: 'grid',
              gridTemplateColumns: 'repeat(4, auto)',
              justifyContent: 'space-between',
              columnGap: 2,
              rowGap: 1,
              borderTop: `1px solid ${alpha(theme.palette.text.primary, 0.05)}`,
              paddingTop: 1.5,
            }}
            >
              <MetaItem label={t('Privilege')}>
                <AgentPrivilege variant="list" privilege={agent.agent_privilege ?? 'admin'} />
              </MetaItem>
              <MetaItem label={t('Deployment')}>
                <AgentDeploymentMode variant="list" mode={agent.agent_deployment_mode ?? 'session'} />
              </MetaItem>
              <MetaItem label={t('Version')}>
                {agent.agent_version ?? '-'}
              </MetaItem>
              <MetaItem label={t('Last Seen')}>
                {agent.agent_last_seen
                  ? <AgentLastSeen timestamp={agent.agent_last_seen} />
                  : '-'}
              </MetaItem>
            </Box>
          </Box>
        );
      })}
    </Box>
  );
};

export default AgentList;
