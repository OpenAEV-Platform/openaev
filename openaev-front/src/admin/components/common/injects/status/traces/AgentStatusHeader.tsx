import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import ItemStatus from '../../../../../../components/ItemStatus';

interface Props {
  agentName?: string;
  statusName?: string;
}

const AgentStatusHeader: FunctionComponent<Props> = ({ agentName, statusName }) => {
  const theme = useTheme();

  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
    }}
    >
      <Typography
        variant="body1"
        sx={{
          fontWeight: 600,
          mr: theme.spacing(1.5),
        }}
      >
        {agentName || '-'}
      </Typography>
      {statusName
        && (
          <ItemStatus
            status={statusName}
            label={statusName}
          />
        )}
    </div>
  );
};

export default AgentStatusHeader;
