import { Text } from '@filigran/design-system';
import { ArrowForwardOutlined, BlockOutlined, ErrorOutlineOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { Link } from 'react-router';

import { tint } from '../../../utils/tint';

interface Props {
  tone: 'warning' | 'critical';
  message: string;
  link?: {
    label: string;
    to: string;
  };
}

// FDS-WORKAROUND: Figma CardBanner (part of Card 8467:4560) built locally — remove when the library ships CardBanner — see sf-approval-card-ooux.md §7
const ThreatArsenalCardBanner: FunctionComponent<Props> = ({ tone, message, link }) => {
  const theme = useTheme();
  const colour = tone === 'critical' ? theme.palette.error.main : theme.palette.warning.main;
  const Icon = tone === 'critical' ? BlockOutlined : ErrorOutlineOutlined;
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
        style={{
          flex: 1,
          minWidth: 0,
          whiteSpace: 'nowrap',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          color: 'inherit',
        }}
        title={message}
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
    </div>
  );
};

export default ThreatArsenalCardBanner;
