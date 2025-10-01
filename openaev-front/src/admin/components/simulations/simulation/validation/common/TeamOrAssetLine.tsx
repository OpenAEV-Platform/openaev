import { CastForEducationOutlined, DnsOutlined, LanOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { fetchSimulationAssetGroups } from '../../../../../../actions/asset_groups/assetgroup-action';
import { type AssetGroupsHelper } from '../../../../../../actions/asset_groups/assetgroup-helper';
import { type EndpointHelper } from '../../../../../../actions/assets/asset-helper';
import { fetchSimulationEndpoints } from '../../../../../../actions/assets/endpoint-actions';
import { fetchExerciseChallenges } from '../../../../../../actions/challenge-action';
import { fetchExerciseArticles } from '../../../../../../actions/channels/article-action';
import { type ArticlesHelper } from '../../../../../../actions/channels/article-helper';
import { type ChannelsHelper } from '../../../../../../actions/channels/channel-helper';
import { type Contract } from '../../../../../../actions/contract/contract';
import { fetchExerciseTeams } from '../../../../../../actions/Exercise';
import { type ChallengeHelper } from '../../../../../../actions/helper';
import { type TeamsHelper } from '../../../../../../actions/teams/team-helper';
import { useHelper } from '../../../../../../store';
import { type AssetGroup, type Endpoint, type Inject, type Team } from '../../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../../utils/hooks';
import useDataLoader from '../../../../../../utils/hooks/useDataLoader';
import { type InjectExpectationsStore } from '../../../../common/injects/expectations/Expectation';
import ChallengeExpectation from '../expectations/ChallengeExpectation';
import ChannelExpectation from '../expectations/ChannelExpectation';
import ManualExpectations from '../expectations/ManualExpectations';
import TechnicalExpectationAsset from '../expectations/TechnicalExpectationAsset';
import TechnicalExpectationAssetGroup from '../expectations/TechnicalExpectationAssetGroup';

const useStyles = makeStyles()(() => ({
  item: { height: 40 },
  bodyItem: {
    height: '100%',
    float: 'left',
    fontSize: 13,
  },
}));

interface Props {
  exerciseId: string;
  inject: Inject;
  injectContract: Contract;
  expectationsByInject: InjectExpectationsStore[];
  id: string;
  expectations: InjectExpectationsStore[];
}

const TeamOrAssetLine: FunctionComponent<Props> = ({
  exerciseId,
  inject,
  injectContract,
  expectationsByInject,
  id,
  expectations,
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const dispatch = useAppDispatch();

  // Fetching data
  const {
    teamsMap,
    assetsMap,
    assetGroupsMap,
    challengesMap,
    articlesMap,
    channelsMap,
  } = useHelper((helper: ArticlesHelper & AssetGroupsHelper & EndpointHelper & ChallengeHelper & ChannelsHelper & TeamsHelper) => {
    return {
      articlesMap: helper.getArticlesMap(),
      assetsMap: helper.getEndpointsMap(),
      assetGroupsMap: helper.getAssetGroupMaps(),
      challengesMap: helper.getChallengesMap(),
      channelsMap: helper.getChannelsMap(),
      teamsMap: helper.getTeamsMap(),
    };
  });
  useDataLoader(() => {
    dispatch(fetchExerciseTeams(exerciseId));
    dispatch(fetchExerciseArticles(exerciseId));
    dispatch(fetchExerciseChallenges(exerciseId));
    dispatch(fetchSimulationEndpoints(exerciseId));
    dispatch(fetchSimulationAssetGroups(exerciseId));
  });

  const team: Team = teamsMap[id];
  const asset: Endpoint = assetsMap[id];
  const assetGroup: AssetGroup = assetGroupsMap[id];

  const groupByExpectationName = (es: InjectExpectationsStore[]) => {
    return es.reduce((group, expectation) => {
      const { inject_expectation_name } = expectation;
      if (inject_expectation_name) {
        const values = group.get(inject_expectation_name) ?? [];
        values.push(expectation);
        group.set(inject_expectation_name, values);
      }
      return group;
    }, new Map());
  };

  return (
    <div key={id}>
      <ListItem
        divider
        sx={{ pl: 4 }}
        classes={{ root: classes.item }}
      >
        <ListItemIcon>
          {!!team && <CastForEducationOutlined fontSize="small" />}
          {!!asset && <DnsOutlined fontSize="small" />}
          {!!assetGroup && <LanOutlined fontSize="small" />}
        </ListItemIcon>
        <ListItemText
          primary={(
            <div className={classes.bodyItem} style={{ width: '20%' }}>
              {team?.team_name || asset?.asset_name || assetGroup?.asset_group_name}
            </div>
          )}
        />
      </ListItem>
      <List component="div" disablePadding>
        {Array.from(groupByExpectationName(expectations)).map(([expectationName, es]) => {
          if (es === 'ARTICLE') {
            const expectation = es[0];
            const article = articlesMap[expectation.inject_expectation_article] || {};
            const channel = channelsMap[article.article_channel] || {};
            return (
              <ChannelExpectation key={expectationName} channel={channel} article={article} expectation={expectation} />
            );
          }
          if (es === 'CHALLENGE') {
            const expectation = es[0];
            const challenge = challengesMap[expectation.inject_expectation_challenge] || {};
            return (
              <ChallengeExpectation key={expectationName} challenge={challenge} expectation={expectation} />
            );
          }
          if (es === 'PREVENTION' || es === 'DETECTION') {
            const expectation = es[0];
            if (asset) {
              return (
                <TechnicalExpectationAsset
                  key={expectationName}
                  expectation={expectation}
                  injectContract={injectContract}
                />
              );
            }
            if (assetGroup) {
              const relatedExpectations = expectationsByInject.filter(e => assetGroup.asset_group_assets?.includes(e.inject_expectation_asset ?? '')) ?? [];

              return (
                <TechnicalExpectationAssetGroup
                  key={expectationName}
                  expectation={expectation}
                  injectContract={injectContract}
                  relatedExpectations={relatedExpectations}
                  team={team}
                  assetGroup={assetGroup}
                />
              );
            }
            return (<div key={expectationName}></div>);
          }
          return (
            <ManualExpectations key={expectationName} inject={inject} expectations={es} />
          );
        })}
      </List>
    </div>
  );
};

export default TeamOrAssetLine;
