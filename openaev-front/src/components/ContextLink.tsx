import { Link as MUILink, Tooltip } from '@mui/material';
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
        sx={{
          color: 'primary.main',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          display: 'inline-block',
          whiteSpace: 'nowrap',
        }}
      >
        {truncate(title, 30)}
      </MUILink>
    </Tooltip>
  );
};

export default ContextLink;
