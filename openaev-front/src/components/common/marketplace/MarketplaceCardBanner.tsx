import { Text, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { ArrowForwardOutlined, BlockOutlined, CloseOutlined, ErrorOutlineOutlined, UpdateOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { Link } from 'react-router';

import { tint } from '../../../utils/tint';

interface Props {
  tone: 'info' | 'warning' | 'critical';
  message: string;
  /** Where the fix is (a page); or `action` when the fix is a dialog. */
  link?: {
    label: string;
    to: string;
  };
  action?: {
    label: string;
    onClick: () => void;
  };
  /** Lets the user hide the banner (e.g. keep injects on an older version on purpose). */
  dismiss?: {
    /** Accessible name, e.g. "Keep them on their version". */
    label: string;
    /** What hiding does, shown on hover. */
    hint: string;
    onClick: () => void;
  };
}

// FDS-WORKAROUND: Figma CardBanner (part of Card 8467:4560) built locally — remove when the library ships CardBanner — see sf-approval-card-ooux.md §7
const MarketplaceCardBanner: FunctionComponent<Props> = ({ tone, message, link, action, dismiss }) => {
  const theme = useTheme();
  const colour = {
    critical: theme.palette.error.main,
    warning: theme.palette.warning.main,
    info: theme.palette.primary.main,
  }[tone];
  const Icon = {
    critical: BlockOutlined,
    warning: ErrorOutlineOutlined,
    info: UpdateOutlined,
  }[tone];
  return (
    <div
      role="status"
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: theme.spacing(1),
        padding: theme.spacing(1, 3),
        backgroundColor: tint(colour, 12),
        borderBottom: `1px solid ${tint(colour, 30)}`,
        color: colour,
      }}
    >
      <Icon fontSize="small" />
      <Text
        variant="content-compact-medium"
        // The message is the point of the banner: on a narrow card it wraps rather than being cut.
        style={{
          flex: 1,
          minWidth: 0,
          color: 'inherit',
        }}
      >
        {message}
      </Text>
      {link && (
        <Link
          to={link.to}
          onClick={event => event.stopPropagation()}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 4,
            color: 'inherit',
            textDecoration: 'none',
            flexShrink: 0,
          }}
        >
          <Text variant="content-compact-medium" style={{ color: 'inherit' }}>{link.label}</Text>
          <ArrowForwardOutlined fontSize="inherit" />
        </Link>
      )}
      {action && (
        <button
          type="button"
          onClick={(event) => {
            event.preventDefault();
            event.stopPropagation();
            action.onClick();
          }}
          style={{
            all: 'unset',
            cursor: 'pointer',
            display: 'inline-flex',
            alignItems: 'center',
            gap: 4,
            color: 'inherit',
            flexShrink: 0,
          }}
        >
          <Text variant="content-compact-medium" style={{ color: 'inherit' }}>{action.label}</Text>
          <ArrowForwardOutlined fontSize="inherit" />
        </button>
      )}
      {dismiss && (
        <Tooltip>
          <TooltipTrigger asChild>
            <button
              type="button"
              aria-label={dismiss.label}
              onClick={(event) => {
                event.preventDefault();
                event.stopPropagation();
                dismiss.onClick();
              }}
              style={{
                all: 'unset',
                cursor: 'pointer',
                display: 'inline-flex',
                alignItems: 'center',
                color: 'inherit',
                flexShrink: 0,
                // Above the card's stretched title link, like the other banner controls.
                position: 'relative',
                zIndex: 1,
              }}
            >
              <CloseOutlined fontSize="small" />
            </button>
          </TooltipTrigger>
          <TooltipContent>{dismiss.hint}</TooltipContent>
        </Tooltip>
      )}
    </div>
  );
};

export default MarketplaceCardBanner;
