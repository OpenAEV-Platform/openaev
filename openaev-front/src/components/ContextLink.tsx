import { Button, Tooltip } from '@mui/material';
import { type FunctionComponent, type ReactElement } from 'react';
import { Link } from 'react-router';

import { truncate } from '../utils/String';

interface Props {
  title: string;
  url: string;
  // Optional leading entity-type icon (inject / simulation / scenario / ...).
  icon?: ReactElement;
}

// A navigating control rendered as a plain button (NOT an underlined hyperlink).
// The app deliberately avoids anchor-style links in favour of buttons, so this
// is a flat, effect-free text button that routes on click.
const ContextLink: FunctionComponent<Props> = ({
  title,
  url,
  icon,
}) => {
  return (
    <Tooltip title={title}>
      <Button
        component={Link}
        to={url}
        disableRipple
        disableElevation
        variant="text"
        size="small"
        startIcon={icon}
        sx={{
          'textTransform': 'none',
          'fontWeight': 400,
          'fontSize': 'inherit',
          'justifyContent': 'flex-start',
          'textAlign': 'left',
          'minWidth': 0,
          'maxWidth': '100%',
          'padding': '2px 6px',
          'color': 'primary.main',
          'lineHeight': 1.4,
          '& .MuiButton-startIcon': { marginRight: 0.5 },
          '& .MuiButton-startIcon > svg': { fontSize: '1rem' },
          '&:hover': { backgroundColor: 'transparent' },
        }}
      >
        <span style={{
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
        >
          {truncate(title, 30)}
        </span>
      </Button>
    </Tooltip>
  );
};

export default ContextLink;
