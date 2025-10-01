import { Grid, Paper, Tab, Tabs, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type SyntheticEvent, useContext, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { searchTargets } from '../../../../actions/injects/inject-action';
import Empty from '../../../../components/Empty';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { type InjectTarget, type SearchPaginationInput } from '../../../../utils/api-types';
import { InjectResultOverviewOutputContext, type InjectResultOverviewOutputContextType } from '../InjectResultOverviewOutputContext';
import PaginatedTargetTab from './PaginatedTargetTab';
import TargetResultsDetail from './target_result/TargetResultsDetail';

const useStyles = makeStyles()({
  chip: {
    fontSize: 12,
    height: 25,
    margin: '0 7px 7px 0',
    textTransform: 'uppercase',
    borderRadius: 4,
    width: 180,
  },
  paper: {
    height: '100%',
    minHeight: '100%',
    padding: 15,
    borderRadius: 4,
  },
  dividerL: {
    position: 'absolute',
    backgroundColor: 'rgba(105, 103, 103, 0.45)',
    width: '2px',
    bottom: '0',
    height: '99%',
    left: '-10px',
  },
  tabs: { marginLeft: 'auto' },
});

type TabConfig = {
  key: number;
  label: string;
  type: string;
  entityPrefix: string;
};

const AtomicTesting = () => {
  // Standard hooks
  const { classes } = useStyles();
  const theme = useTheme();
  const { t } = useFormatter();
  const [activeTab, setActiveTab] = useState<TabConfig>();

  // Fetching data
  const { injectResultOverviewOutput } = useContext<InjectResultOverviewOutputContextType>(InjectResultOverviewOutputContext);
  const [hasAssetsGroup, setHasAssetsGroup] = useState(false);
  const [hasAssetsGroupChecked, setHasAssetsGroupChecked] = useState(false);
  const [hasEndpoints, setHasEndpoints] = useState(false);
  const [hasEndpointsChecked, setHasEndpointsChecked] = useState(false);
  const [hasAgents, setHasAgents] = useState(false);
  const [hasAgentsChecked, setHasAgentsChecked] = useState(false);
  const [reloadContentCount, setReloadContentCount] = useState(0);
  const [hasTeams, setHasTeams] = useState(false);
  const [hasTeamsChecked, setHasTeamsChecked] = useState(false);
  const [hasPlayers, setHasPlayers] = useState(false);
  const [hasPlayersChecked, setHasPlayersChecked] = useState(false);
  const [selectedTarget, setSelectedTarget] = useState<InjectTarget>();

  // Initial tab open
  const [searchParams, setSearchParams] = useSearchParams();
  const targetType = searchParams.get('target');

  const navigateToTab = (tab: TabConfig | undefined) => {
    setActiveTab(tab);
    setReloadContentCount(reloadContentCount + 1);
  };

  const tabConfig: TabConfig[] = useMemo(() => {
    let index: number = 0;
    const tabs: TabConfig[] = [];

    if (hasAssetsGroup) {
      tabs.push({
        key: index++,
        label: t('Asset groups'),
        type: 'ASSETS_GROUPS',
        entityPrefix: 'asset_group_target',
      });
    }
    if (hasTeams) {
      tabs.push({
        key: index++,
        label: t('Teams'),
        type: 'TEAMS',
        entityPrefix: 'team_target',
      });
    }
    if (hasEndpoints) {
      tabs.push({
        key: index++,
        label: t('Endpoints'),
        type: 'ASSETS',
        entityPrefix: 'endpoint_target',
      });
    }
    if (hasPlayers) {
      tabs.push({
        key: index++,
        label: t('Players'),
        type: 'PLAYERS',
        entityPrefix: 'player_target',
      });
    }
    if (hasAgents) {
      tabs.push({
        key: index++,
        label: t('Agents'),
        type: 'AGENT',
        entityPrefix: 'agent_target',
      });
    }

    // tabs visibility may have changed so we reevaluate this structure;
    // figure out which tab to display; if the previously displayed tab
    // is still available, keep it up
    // otherwise default to the first occurring tab
    if (tabs.length === 0) {
      navigateToTab(undefined);
    }

    if (targetType != null && tabs.map(conf => conf.type).includes(targetType)) {
      navigateToTab(tabs.find(tc => targetType === tc.type));
      searchParams.delete('target');
      setSearchParams(searchParams, { replace: true });
    } else if (activeTab && tabs.map(conf => conf.type).includes(activeTab.type)) {
      navigateToTab(tabs.find(tc => activeTab.type === tc.type));
    } else {
      navigateToTab(tabs[0]);
    }

    return tabs;
  }, [hasAssetsGroup, hasTeams, hasEndpoints, hasAgents, hasPlayers]);

  const activeTabKey: number = useMemo(() => {
    return activeTab?.key || 0;
  }, [activeTab]);

  const injectId = injectResultOverviewOutput?.inject_id || '';

  useEffect(() => {
    if (!injectResultOverviewOutput) return;

    const searchPaginationInput1Result: SearchPaginationInput = {
      filterGroup: {
        mode: 'and',
        filters: [],
      },
      size: 1,
      page: 0,
    };

    searchTargets(injectId, 'ASSETS_GROUPS', searchPaginationInput1Result)
      .then((response) => {
        if (response.data.content.length > 0) {
          setHasAssetsGroup(true);
        } else { setHasAssetsGroup(false); }
      })
      .finally(() => {
        setHasAssetsGroupChecked(true);
      });

    searchTargets(injectId, 'ASSETS', searchPaginationInput1Result)
      .then((response) => {
        if (response.data.content.length > 0) {
          setHasEndpoints(true);
        } else { setHasEndpoints(false); }
      })
      .finally(() => {
        setHasEndpointsChecked(true);
      });

    searchTargets(injectId, 'TEAMS', searchPaginationInput1Result)
      .then((response) => {
        if (response.data.content.length > 0) {
          setHasTeams(true);
        } else { setHasTeams(false); }
      })
      .finally(() => {
        setHasTeamsChecked(true);
      });

    searchTargets(injectId, 'PLAYERS', searchPaginationInput1Result)
      .then((response) => {
        if (response.data.content.length > 0) {
          setHasPlayers(true);
        } else { setHasPlayers(false); }
      })
      .finally(() => {
        setHasPlayersChecked(true);
      });

    searchTargets(injectId, 'AGENT', searchPaginationInput1Result)
      .then((response) => {
        if (response.data.content.length > 0) {
          setHasAgents(true);
        } else { setHasAgents(false); }
      })
      .finally(() => {
        setHasAgentsChecked(true);
      });

    setReloadContentCount(reloadContentCount + 1);
  }, [injectResultOverviewOutput]);

  // Handles
  const handleNewTargetClick = (target: InjectTarget) => {
    setSelectedTarget(target);
  };

  const handleTabChange = (_event: SyntheticEvent, newValue: number) => {
    const location = tabConfig.find(tc => newValue == tc.key);
    navigateToTab(location);
  };

  const drawTabs = () => {
    const tab = tabConfig.find(value => value.type == activeTab?.type);
    if (!tab) {
      return (<div />);
    }
    const isAllTargets = tab.type === 'ALL_TARGETS';
    return (
      <>
        {!isAllTargets && injectResultOverviewOutput && (
          <PaginatedTargetTab
            key={activeTabKey}
            handleSelectTarget={handleNewTargetClick}
            entityPrefix={tab.entityPrefix}
            inject_id={injectResultOverviewOutput.inject_id}
            target_type={tab.type}
            reloadContentCount={reloadContentCount}
          />
        )}
      </>
    );
  };

  if (!injectResultOverviewOutput) {
    return <Loader variant="inElement" />;
  }

  return (
    <Grid container spacing={3} style={{ marginBottom: theme.spacing(3) }}>
      <Grid size={6}>
        <Typography variant="h4" gutterBottom style={{ float: 'left' }} sx={{ mb: theme.spacing(1) }}>
          {t('Targets')}
        </Typography>
        <div className="clearfix" />
        <Paper classes={{ root: classes.paper }} variant="outlined">
          {hasAssetsGroupChecked && hasTeamsChecked && hasEndpointsChecked && hasAgentsChecked && hasPlayersChecked && (
            <>
              <Tabs
                value={activeTabKey}
                onChange={handleTabChange}
                indicatorColor="primary"
                textColor="primary"
                className={classes.tabs}
              >
                {tabConfig
                  .map(tab => (
                    <Tab key={`tab-${tab.key}`} label={tab.label} />
                  ))}
              </Tabs>
              {drawTabs()}
            </>
          )}
        </Paper>
      </Grid>
      <Grid size={6}>
        <Typography variant="h4" gutterBottom sx={{ mb: theme.spacing(1) }}>
          {t('Results by target')}
        </Typography>
        {selectedTarget && !!injectResultOverviewOutput.inject_type && (
          <TargetResultsDetail inject={injectResultOverviewOutput} target={selectedTarget} />
        )}
        {!selectedTarget && (
          <Paper classes={{ root: classes.paper }} variant="outlined">
            <Empty message={t('No target data available.')} />
          </Paper>
        )}
      </Grid>
    </Grid>
  );
};

export default AtomicTesting;
