import { Link as MUILink, Tooltip, Typography } from '@mui/material';
import { type FunctionComponent } from 'react';
import { Link } from 'react-router';

import { truncate } from '../utils/String';

interface Props {
  title: string;
  url: string;
}

const ContextLink: FunctionComponent<Props> = ({
  title,
  url,
}) => {
  return (
    <Tooltip title={title}>
      <MUILink
        component={Link}
        to={url}
        underline="none"
      >
        <Typography
          overflow="hidden"
          textOverflow="ellipsis"
        >
          {truncate(title, 30)}
        </Typography>
      </MUILink>
    </Tooltip>
  );
};

export default ContextLink;
