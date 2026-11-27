import {
  Card,
  CardDescription,
  CardFooter,
  CardHeader,
  CardInfo,
  CardInfoGroup,
  CardOverview,
  CardTitle,
  Thumbnail,
} from '@filigran/design-system';
import { Box } from '@mui/material';
import { type FunctionComponent, type ReactNode } from 'react';
import { Link } from 'react-router';

interface Props {
  icon: ReactNode;
  title: string;
  overline?: string;
  subtitle?: string;
  description?: string;
  chips?: ReactNode;
  banner?: ReactNode;
  /** Label and value pairs under the description. */
  info?: {
    label: string;
    value: string;
  }[];
  footerStart?: ReactNode;
  /** The card's actions or status, at the end of the footer. */
  action?: ReactNode;
  linkTo?: string;
  onClick?: () => void;
  testId?: string;
}

// A marketplace item in the card grid, on the design system Card.
const MarketplaceCard: FunctionComponent<Props> = ({
  icon,
  title,
  overline,
  subtitle,
  description,
  chips,
  banner,
  info,
  footerStart,
  action,
  linkTo,
  onClick,
  testId,
}) => (
  <Card data-testid={testId} to={linkTo} linkComponent={Link} onClick={onClick}>
    {banner}
    <CardHeader icon={(
      <Thumbnail elevation={2}>
        <Box sx={{
          'display': 'flex',
          // An image fills the whole 48px Thumbnail frame; a glyph keeps its 24px.
          '& img': {
            width: theme => theme.spacing(6),
            height: theme => theme.spacing(6),
            objectFit: 'contain',
          },
        }}
        >
          {icon}
        </Box>
      </Thumbnail>
    )}
    >
      {overline && <CardOverview>{overline}</CardOverview>}
      <CardTitle>{title}</CardTitle>
    </CardHeader>
    {subtitle && (
      <CardInfoGroup>
        <CardInfo value={subtitle} />
      </CardInfoGroup>
    )}
    {chips && (
      // FDS-WORKAROUND: Card's tags slot (Figma only), chips 8px apart — remove when the DS Card ships it — see fds-migration/LIBRARY-FEEDBACK.md #75
      <div style={{
        display: 'flex',
        alignItems: 'center',
        flexWrap: 'wrap',
        gap: 'calc(var(--spacing) * 2)',
        minWidth: 0,
      }}
      >
        {chips}
      </div>
    )}
    {description && <CardDescription>{description}</CardDescription>}
    {info && (
      <CardInfoGroup type="block">
        {info.map(item => <CardInfo key={item.label} label={item.label} value={item.value} />)}
      </CardInfoGroup>
    )}
    {(footerStart || action) && <CardFooter start={footerStart} end={action} />}
  </Card>
);

export default MarketplaceCard;
