import { DnsOutlined } from '@mui/icons-material';
import { ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type EndpointHelper } from '../../../../../../actions/assets/asset-helper';
import { type Contract } from '../../../../../../actions/contract/contract';
import { useHelper } from '../../../../../../store';
import { type AssetGroup, type Endpoint, type Team } from '../../../../../../utils/api-types';
import { type InjectExpectationsStore } from '../../../../common/injects/expectations/Expectation';
import { typeIcon } from '../../../../common/injects/expectations/ExpectationUtils';
import ExpectationLine from './ExpectationLine';
import { groupedByAsset } from './ExpectationUtils';
import TechnicalExpectationAsset from './TechnicalExpectationAsset';

const useStyles = makeStyles()(() => ({
  item: { height: 40 },
  bodyItem: {
    height: '100%',
    float: 'left',
    fontSize: 13,
  },
}));

interface Props {
  expectation: InjectExpectationsStore;
  injectContract: Contract;
  relatedExpectations: InjectExpectationsStore[];
  team: Team;
  assetGroup: AssetGroup;
}

const TechnicalExpectationAssetGroup: FunctionComponent<Props> = ({
  expectation,
  injectContract,
  relatedExpectations,
  team,
  assetGroup,
}) => {
  // Standard hooks
  const { classes } = useStyles();

  // Fetching data
  const { assetsMap } = useHelper((helper: EndpointHelper) => {
    return { assetsMap: helper.getEndpointsMap() };
  });

  return (
    <>
      <ExpectationLine
        expectation={expectation}
        info={injectContract.config.label?.en}
        title={injectContract.label.en}
        icon={typeIcon(expectation.inject_expectation_type)}
      />
      {Array.from(groupedByAsset(relatedExpectations)).map(([groupedId, groupedExpectations]) => {
        const relatedAsset: Endpoint = assetsMap[groupedId];
        return (
          <div key={relatedAsset?.asset_id}>
            <ListItem
              divider
              sx={{ pl: 12 }}
              classes={{ root: classes.item }}
            >
              <ListItemIcon>
                {!!relatedAsset && <DnsOutlined fontSize="small" />}
              </ListItemIcon>
              <ListItemText
                primary={(
                  <div className={classes.bodyItem} style={{ width: '20%' }}>
                    {team?.team_name || relatedAsset?.asset_name || assetGroup?.asset_group_name}
                  </div>
                )}
              />
            </ListItem>
            {groupedExpectations.map((e: InjectExpectationsStore) => (
              <TechnicalExpectationAsset key={e.inject_expectation_id} expectation={e} injectContract={injectContract} gap={16} />
            ))}
          </div>
        );
      })}
    </>
  );
};

export default TechnicalExpectationAssetGroup;
