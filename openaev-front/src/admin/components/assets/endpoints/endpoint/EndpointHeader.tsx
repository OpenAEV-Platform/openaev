import { Tooltip, Typography } from '@mui/material';
import { useNavigate, useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { getEndpointSelector } from '../../../../../actions/selectors';
import Loader from '../../../../../components/Loader';
import { useSelectorHelper } from '../../../../../store';
import { type EndpointOutput, type EndpointOverviewOutput as EndpointType } from '../../../../../utils/api-types';
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
  const endpoint = useSelectorHelper(state => getEndpointSelector(endpointId, state));

  if (!endpoint) {
    return <Loader variant="inElement" />;
  }

  return (
    <>
      <Tooltip title={endpoint?.asset_name}>
        <Typography
          variant="h1"
          gutterBottom
          classes={{ root: classes.title }}
        >
          {truncate(endpoint?.asset_name ?? '', 80)}
        </Typography>
      </Tooltip>
      <div className={classes.actions}>
        <EndpointPopover
          endpoint={endpoint as EndpointOutput}
          agentless={(endpoint?.asset_agents?.length ?? 0) === 0}
          onUpdate={() => endpoint}
          onDelete={() => navigate('/admin/assets/endpoints')}
        />
      </div>
      <div className="clearfix" />
    </>
  );
};

export default EndpointHeader;
