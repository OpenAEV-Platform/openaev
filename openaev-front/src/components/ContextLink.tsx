import { Box, Tooltip } from '@mui/material';
import { type FunctionComponent, type ReactElement } from 'react';
import { Link } from 'react-router';

import { truncate } from '../utils/String';

interface Props {
  title: string;
  url: string;
  // Optional leading entity-type icon (inject / simulation / scenario / ...).
  icon?: ReactElement;
}

// Plain text that navigates on click. Deliberately NOT a hyperlink (no
// underline, no primary color) and NOT a button (no padding, background,
// border or hover effect): it must read as regular cell text, with only the
// cursor indicating it is clickable.
const ContextLink: FunctionComponent<Props> = ({
  title,
  url,
  icon,
}) => {
  return (
    <Tooltip title={title}>
      <Box
        component={Link}
        to={url}
        sx={{
          'display': 'inline-flex',
          'alignItems': 'center',
          'gap': 0.75,
          'minWidth': 0,
          'maxWidth': '100%',
          'color': 'inherit',
          'textDecoration': 'none',
          'lineHeight': 1.4,
          '& > svg': {
            fontSize: '1rem',
            flexShrink: 0,
            color: 'text.secondary',
          },
        }}
      >
        {icon}
        <span style={{
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
        >
          {truncate(title, 30)}
        </span>
      </Box>
    </Tooltip>
  );
};

export default ContextLink;
