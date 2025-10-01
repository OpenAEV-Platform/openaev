import { Tooltip, Typography } from '@mui/material';
import { useNavigate, useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { type EndpointHelper } from '../../../../../actions/assets/asset-helper';
import { type UserHelper } from '../../../../../actions/helper';
import { useHelper } from '../../../../../store';
import { type EndpointOverviewOutput as EndpointType } from '../../../../../utils/api-types';
import { truncate } from '../../../../../utils/String';
import EndpointPopover from '../EndpointPopover';

const useStyles = makeStyles()(() => ({
  title: { float: 'left' },
  actions: {
    margin: '-6px 0 0 0',
    float: 'right',
  },
}));

const EndpointHeader = () => {
  // Standard hooks
  const { classes } = useStyles();
  const navigate = useNavigate();
  const { endpointId } = useParams() as { endpointId: EndpointType['asset_id'] };

  // Fetching data
  const { endpoint } = useHelper((helper: EndpointHelper & UserHelper) => ({ endpoint: helper.getEndpoint(endpointId) }));

  return (
    <>
      <Tooltip title={endpoint.asset_name}>
        <Typography
          variant="h1"
          gutterBottom
          classes={{ root: classes.title }}
        >
          {truncate(endpoint.asset_name, 80)}
        </Typography>
      </Tooltip>
      <div className={classes.actions}>
        <EndpointPopover
          endpoint={{
            ...endpoint,
            type: 'static',
          }}
          agentless={endpoint.asset_agents.length === 0}
          onUpdate={() => endpoint}
          onDelete={() => navigate('/admin/assets/endpoints')}
        />
      </div>
      <div className="clearfix" />
    </>
  );
};

export default EndpointHeader;
