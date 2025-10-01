import { DevicesOtherOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type CSSProperties, type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { fetchExecutors } from '../../../../../actions/Executor';
import { type ExecutorHelper } from '../../../../../actions/executors/executor-helper';
import type { LoggedHelper } from '../../../../../actions/helper';
import useBodyItemsStyles from '../../../../../components/common/queryable/style/style';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type AgentOutput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import EEChip from '../../../common/entreprise_edition/EEChip';
import AssetStatus from '../../AssetStatus';
import AgentDeploymentMode from '../AgentDeploymentMode';
import AgentPrivilege from '../AgentPrivilege';
import AgentLastSeen from './AgentLastSeen';

const useStyles = makeStyles()(() => ({
  itemHead: {
    paddingLeft: 10,
    textTransform: 'uppercase',
  },
  item: {
    paddingLeft: 10,
    height: 50,
  },
}));

const inlineStyles: Record<string, CSSProperties> = {
  agent_executed_by_user: { width: '30%' },
  agent_executor: {
    width: '15%',
    display: 'flex',
    alignItems: 'center',
    cursor: 'default',
  },
  agent_privilege: { width: '15%' },
  agent_deployment_mode: { width: '10%' },
  agent_active: { width: '10%' },
  agent_version: { width: '5%' },
  agent_last_seen: { width: '15%' },
};

interface Props { agents: AgentOutput[] }

const AgentList: FunctionComponent<Props> = ({ agents }) => {
  const { classes } = useStyles();
  const theme = useTheme();
  const bodyItemsStyles = useBodyItemsStyles();
  const dispatch = useAppDispatch();
  const { t } = useFormatter();
  // Fetching data
  const { settings, executorsMap } = useHelper((helper: ExecutorHelper & LoggedHelper) => ({
    settings: helper.getPlatformSettings(),
    executorsMap: helper.getExecutorsMap(),
  }));
  useDataLoader(() => {
    dispatch(fetchExecutors());
  });

  // Headers
  const headers = [
    {
      field: 'agent_executed_by_user',
      label: 'Agent Name',
      isSortable: false,
      value: (agent: AgentOutput) => agent.agent_executed_by_user,
    },
    {
      field: 'agent_executor',
      label: 'Executor',
      isSortable: false,
      value: (agent: AgentOutput) => {
        const executorId = agent.agent_executor?.executor_id;
        const executor = executorId ? executorsMap[executorId] : undefined;

        if (!executor) {
          return <>{t('Unknown')}</>;
        }

        const { executor_type, executor_name } = executor;
        const showEEChip = !settings.platform_license?.license_is_validated && (executor_type === 'openaev_tanium' || executor_type === 'openaev_crowdstrike');

        return (
          <>
            <img
              src={`/api/images/executors/icons/${executor_type}`}
              alt={executor_type}
              style={{
                width: 20,
                height: 20,
                borderRadius: 4,
                marginRight: theme.spacing(1),
              }}
            />
            {executor_name}
            {showEEChip && (
              <EEChip
                style={{ marginLeft: theme.spacing(1) }}
                clickable
                featureDetectedInfo={executor_name}
              />
            )}
          </>
        );
      },
    },
    {
      field: 'agent_privilege',
      label: 'Privilege',
      isSortable: false,
      value: (agent: AgentOutput) => {
        return (<AgentPrivilege variant="list" privilege={agent.agent_privilege ?? 'admin'} />);
      },
    },
    {
      field: 'agent_deployment_mode',
      label: 'Deployment',
      isSortable: false,
      value: (agent: AgentOutput) => {
        return (<AgentDeploymentMode variant="list" mode={agent.agent_deployment_mode ?? 'session'} />);
      },
    },
    {
      field: 'agent_active',
      label: 'Status',
      isSortable: false,
      value: (agent: AgentOutput) => {
        return (<AssetStatus variant="list" status={agent.agent_active ? 'Active' : 'Inactive'} />);
      },
    },
    {
      field: 'agent_version',
      label: 'Version',
      isSortable: false,
      value: (agent: AgentOutput) => agent.agent_version ?? '-',
    },
    {
      field: 'agent_last_seen',
      label: 'Last Seen',
      isSortable: false,
      value: (agent: AgentOutput) => agent.agent_last_seen
        ? <AgentLastSeen timestamp={agent.agent_last_seen} />
        : '-',
    },
  ];

  return (
    <List>
      <ListItem
        classes={{ root: classes.itemHead }}
        style={{ paddingTop: 0 }}
      >
        <ListItemIcon />
        <ListItemText
          primary={(
            <div>
              <div style={bodyItemsStyles.bodyItems}>
                {headers.map(header => (
                  <div
                    key={header.field}
                    style={{
                      ...bodyItemsStyles.bodyItem,
                      ...inlineStyles[header.field],
                    }}
                  >
                    {t(header.label)}
                  </div>
                ))}
              </div>
            </div>
          )}
        />
      </ListItem>
      <ListItem
        classes={{ root: classes.itemHead }}
        style={{ paddingTop: 0 }}
      >
      </ListItem>
      {agents.map((agent: AgentOutput) => {
        return (
          <ListItem
            key={agent.agent_id}
            classes={{ root: classes.item }}
            divider
          >
            <ListItemIcon>
              <DevicesOtherOutlined color="primary" />
            </ListItemIcon>
            <ListItemText
              primary={(
                <div style={bodyItemsStyles.bodyItems}>
                  {headers.map(header => (
                    <div
                      key={header.field}
                      style={{
                        ...bodyItemsStyles.bodyItem,
                        ...inlineStyles[header.field],
                      }}
                    >
                      {header.value(agent)}
                    </div>
                  ))}
                </div>
              )}
            />
          </ListItem>
        );
      })}
    </List>
  );
};

export default AgentList;
