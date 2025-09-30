import { List, ListItem, Paper, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { type EndpointHelper } from '../../../../../actions/assets/asset-helper';
import { searchDistinctFindingsOnEndpoint, searchFindingsOnEndpoint } from '../../../../../actions/findings/finding-actions';
import InverseBooleanFragment from '../../../../../components/common/list/fragments/InverseBooleanFragment';
import Empty from '../../../../../components/Empty';
import ExpandableMarkdown from '../../../../../components/ExpandableMarkdown';
import { useFormatter } from '../../../../../components/i18n';
import ItemTags from '../../../../../components/ItemTags';
import ItemTargets from '../../../../../components/ItemTargets';
import PlatformIcon from '../../../../../components/PlatformIcon';
import { INJECT, SIMULATION } from '../../../../../constants/Entities';
import { useHelper } from '../../../../../store';
import { type AggregatedFindingOutput, type EndpointOverviewOutput as EndpointType, type RelatedFindingOutput, type SearchPaginationInput, type TargetSimple } from '../../../../../utils/api-types';
import { emptyFilled, formatIp, formatMacAddress } from '../../../../../utils/String';
import FindingContextLink from '../../../findings/FindingContextLink';
import FindingList from '../../../findings/FindingList';
import AgentList from './AgentList';

const useStyles = makeStyles()(theme => ({
  endpointPage: {
    'marginTop': theme.spacing(2),
    '& > div:nth-child(even)': { marginBottom: theme.spacing(2) },
  },
  gridContainer: {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr 1fr 1fr 1fr',
    gap: theme.spacing(2),
  },
  chipInList: {
    fontSize: 12,
    lineHeight: '12px',
    height: 20,
    textTransform: 'uppercase',
    borderRadius: 4,
    width: 100,
  },
}));

const Endpoint = () => {
  // Standard hooks
  const { classes } = useStyles();
  const { endpointId } = useParams() as { endpointId: EndpointType['asset_id'] };
  const { t } = useFormatter();
  const theme = useTheme();

  // Fetching data
  const { endpoint } = useHelper((helper: EndpointHelper) => ({ endpoint: helper.getEndpoint(endpointId) }));

  const additionalFilterNames = [
    'finding_inject_id',
    'finding_simulation',
  ];

  const additionalHeaders = [
    {
      field: 'finding_inject',
      label: 'Inject',
      isSortable: false,
      value: (finding: RelatedFindingOutput) => <FindingContextLink finding={finding} type={INJECT} />,
    },
    {
      field: 'finding_simulation',
      label: 'Simulation',
      isSortable: false,
      value: (finding: RelatedFindingOutput) => <FindingContextLink finding={finding} type={SIMULATION} />,
    },
    {
      field: 'finding_asset_groups',
      label: 'Asset groups',
      isSortable: false,
      value: (finding: AggregatedFindingOutput) => (
        <ItemTargets targets={(finding.finding_asset_groups || []).map(group => ({
          target_id: group.asset_group_id,
          target_name: group.asset_group_name,
          target_type: 'ASSETS_GROUPS',
        })) as TargetSimple[]}
        />
      ),
    },
  ];

  const search = (input: SearchPaginationInput) => {
    return searchFindingsOnEndpoint(endpointId, input);
  };
  const searchDistinct = (input: SearchPaginationInput) => {
    return searchDistinctFindingsOnEndpoint(endpointId, input);
  };

  return (
    <div className={classes.endpointPage}>
      <Typography variant="h4">{t('Endpoint Information')}</Typography>
      <Paper className={`paper ${classes.gridContainer}`} variant="outlined">
        <div>
          <Typography variant="h3" gutterBottom>{t('Description')}</Typography>
          <ExpandableMarkdown source={endpoint.asset_description} limit={300} />
        </div>
        <div>
          <Typography variant="h3" gutterBottom>{t('Hostname')}</Typography>
          <Typography variant="body2" gutterBottom>{emptyFilled(endpoint.endpoint_hostname)}</Typography>
        </div>
        <div>
          <Typography variant="h3" gutterBottom>{t('Seen IP address')}</Typography>
          <Typography variant="body2" gutterBottom>{emptyFilled(endpoint.endpoint_seen_ip)}</Typography>
        </div>
        <div>
          <Typography variant="h3" gutterBottom>{t('Platform')}</Typography>
          <span style={{ display: 'flex' }}>
            <PlatformIcon platform={endpoint.endpoint_platform} width={20} marginRight={theme.spacing(2)} />
            &nbsp;
            {endpoint.endpoint_platform}
          </span>
        </div>
        <div>
          <Typography variant="h3" gutterBottom>{t('End of Life')}</Typography>
          <InverseBooleanFragment bool={endpoint.endpoint_is_eol} />
        </div>
        <div>
          <Typography variant="h3" gutterBottom>{t('Architecture')}</Typography>
          <Typography variant="body2" gutterBottom>{emptyFilled(endpoint.endpoint_arch)}</Typography>
        </div>
        <div>
          <Typography variant="h3" gutterBottom>{t('IP Addresses')}</Typography>
          <div style={{
            maxHeight: theme.spacing(20),
            overflowY: 'auto',
            overflowX: 'hidden',
            marginRight: theme.spacing(1.5),
          }}
          >
            <Typography variant="body2" gutterBottom>
              {endpoint.endpoint_ips?.map((ip: string) => (
                <ListItem key={ip} disableGutters sx={{ py: 0 }}>
                  <Tooltip title={ip}>
                    <Typography
                      variant="body2"
                      noWrap
                      sx={{
                        maxWidth: '100%',
                        textOverflow: 'ellipsis',
                        overflow: 'hidden',
                      }}
                    >
                      {formatIp(ip)}
                    </Typography>
                  </Tooltip>
                </ListItem>
              ))}
            </Typography>
          </div>
        </div>
        <div>
          <Typography variant="h3" gutterBottom>{t('MAC Addresses')}</Typography>
          <div style={{
            maxHeight: theme.spacing(20),
            overflowY: 'auto',
            marginRight: theme.spacing(1),
          }}
          >
            <Typography variant="body2" gutterBottom>
              {endpoint.endpoint_mac_addresses?.map((mac: string) => (
                <ListItem key={mac} disableGutters sx={{ py: 0 }}>
                  <Tooltip title={mac}>
                    <Typography
                      variant="body2"
                      noWrap
                      sx={{
                        maxWidth: '100%',
                        textOverflow: 'ellipsis',
                        overflow: 'hidden',
                      }}
                    >
                      {formatMacAddress(mac)}
                    </Typography>
                  </Tooltip>
                </ListItem>
              ))}
            </Typography>
          </div>
        </div>
        <div>
          <Typography variant="h3" gutterBottom>{t('Tags')}</Typography>
          <ItemTags variant="list" tags={endpoint.asset_tags} />
        </div>
      </Paper>
      <Typography variant="h4">{t('Agents')}</Typography>
      <Paper className="paper" variant="outlined">
        {endpoint.asset_agents ? (
          <List>
            <AgentList agents={endpoint.asset_agents}></AgentList>
          </List>
        ) : (
          <Empty message={t('No agents installed.')} />
        )}
      </Paper>
      <Typography variant="h4">{t('Findings')}</Typography>
      <Paper className="paper" variant="outlined">
        <FindingList
          filterLocalStorageKey="endpoint-findings"
          searchDistinctFindings={searchDistinct}
          searchFindings={search}
          additionalHeaders={additionalHeaders}
          additionalFilterNames={additionalFilterNames}
          contextId={endpointId}
        />
      </Paper>
    </div>
  );
};
export default Endpoint;
