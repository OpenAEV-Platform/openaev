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

// Plain text that navigates on click. At rest it reads as regular cell text
// (no underline, no primary color, no button chrome); hovering reveals the
// affordance by tinting the text and icon with the primary color.
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
          'transition': 'color 0.2s',
          '& > svg': {
            fontSize: '1rem',
            flexShrink: 0,
            color: 'text.secondary',
            transition: 'color 0.2s',
          },
          '&:hover': {
            'color': 'primary.main',
            '& > svg': { color: 'primary.main' },
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
