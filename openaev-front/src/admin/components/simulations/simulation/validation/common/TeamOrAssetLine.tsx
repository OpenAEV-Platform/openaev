import { CastForEducationOutlined, LanOutlined } from '@mui/icons-material';
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
import AssetCategoryIcon from '../../../../assets/AssetCategoryIcon';
import { ExpectationType, type InjectExpectationsStore } from '../../../../common/injects/expectations/Expectation';
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

  const groupByExpectationContext = (es: InjectExpectationsStore[]): Map<string, InjectExpectationsStore[]> => {
    return es.reduce((group, expectation) => {
      const { inject_expectation_name, inject_expectation_article, inject_expectation_challenge } = expectation;
      if (inject_expectation_name) {
        const key = [inject_expectation_name, inject_expectation_article ?? '', inject_expectation_challenge ?? ''].join('|');
        const values = group.get(key) ?? [];
        values.push(expectation);
        group.set(key, values);
      }
      return group;
    }, new Map<string, InjectExpectationsStore[]>());
  };

  const findMainExpectation = (es: InjectExpectationsStore[]): InjectExpectationsStore | undefined => {
    if (assetGroup) {
      return es.find(e => e.inject_expectation_asset_group === id && e.inject_expectation_asset == null);
    }
    if (asset) {
      return es.find(e => e.inject_expectation_asset === id && e.inject_expectation_agent == null);
    }
    return es.find(e => e.inject_expectation_team === id && e.inject_expectation_user == null);
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
          {/* Category-aware glyph: hosts keep the server icon (HOST maps to it),
              web apps / cloud / AI targets get their taxonomy icon. */}
          {!!asset && <AssetCategoryIcon category={asset.asset_category} fontSize="small" />}
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
        {Array.from(groupByExpectationContext(expectations)).map(([groupKey, es]) => {
          const mainExpectation = findMainExpectation(es);
          if (!mainExpectation) {
            return null;
          }
          const type = mainExpectation.inject_expectation_type;
          if (type === ExpectationType.ARTICLE) {
            const article = articlesMap[mainExpectation.inject_expectation_article ?? ''] || {};
            const channel = channelsMap[article.article_channel] || {};
            return (
              <ChannelExpectation key={groupKey} channel={channel} article={article} expectation={mainExpectation} />
            );
          }
          if (type === ExpectationType.CHALLENGE) {
            const challenge = challengesMap[mainExpectation.inject_expectation_challenge ?? ''] || {};
            return (
              <ChallengeExpectation key={groupKey} challenge={challenge} expectation={mainExpectation} />
            );
          }
          if (type === ExpectationType.PREVENTION || type === ExpectationType.DETECTION) {
            if (asset) {
              return (
                <TechnicalExpectationAsset
                  key={groupKey}
                  expectation={mainExpectation}
                  injectContract={injectContract}
                />
              );
            }
            if (assetGroup) {
              const relatedExpectations = expectationsByInject.filter(e => assetGroup.asset_group_assets?.includes(e.inject_expectation_asset ?? '')) ?? [];

              return (
                <TechnicalExpectationAssetGroup
                  key={groupKey}
                  expectation={mainExpectation}
                  injectContract={injectContract}
                  relatedExpectations={relatedExpectations}
                  team={team}
                  assetGroup={assetGroup}
                />
              );
            }
            return (<div key={groupKey}></div>);
          }
          return (
            <ManualExpectations key={groupKey} inject={inject} expectations={es} />
          );
        })}
      </List>
    </div>
  );
};

export default TeamOrAssetLine;
