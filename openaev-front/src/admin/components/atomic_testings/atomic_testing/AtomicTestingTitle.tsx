import { Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import Loader from '../../../../components/Loader';
import type { InjectResultOverviewOutput, InjectStatus as InjectStatusType } from '../../../../utils/api-types';
import { truncate } from '../../../../utils/String';
import InjectStatus from '../../common/injects/status/InjectStatus';

interface Props { injectResultOverview: InjectResultOverviewOutput }

const AtomicTestingTitle = ({ injectResultOverview }: Props) => {
  const theme = useTheme();

  if (!injectResultOverview) {
    return <Loader variant="inElement" />;
  }

  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      gap: theme.spacing(1),
      minWidth: 0,
    }}
    >
      <Tooltip title={injectResultOverview.inject_title}>
        <Typography
          variant="h1"
          sx={{
            fontWeight: 700,
            fontSize: 22,
            lineHeight: 1.3,
            margin: 0,
            minWidth: 0,
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {truncate(injectResultOverview.inject_title, 80)}
        </Typography>
      </Tooltip>
      <InjectStatus status={injectResultOverview.inject_status?.status_name as InjectStatusType['status_name']} />
    </div>
  );
};

export default AtomicTestingTitle;
