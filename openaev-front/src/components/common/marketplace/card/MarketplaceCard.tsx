import {
  Card,
  CardDescription,
  CardFooter,
  CardHeader,
  CardInfo,
  CardInfoGroup,
  CardOverview,
  CardTitle,
  Text,
  Thumbnail,
} from '@filigran/design-system';
import { Box } from '@mui/material';
import { type FunctionComponent, type ReactNode } from 'react';
import { Link } from 'react-router';

interface Props {
  icon: ReactNode;
  title: string;
  /** Small label above the title, e.g. the kind of content. */
  overline?: string;
  /** One line under the header, e.g. the author. */
  subtitle?: string;
  description?: string;
  chips?: ReactNode;
  /** Metadata at the start of the footer, e.g. the version. */
  caption?: string;
  /** The card's actions or status, at the end of the footer. */
  action?: ReactNode;
  linkTo?: string;
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
  caption,
  action,
  linkTo,
  testId,
}) => {
  return (
    <Card data-testid={testId} to={linkTo} linkComponent={Link}>
      <CardHeader icon={(
        <Thumbnail>
          <Box sx={{
            'display': 'flex',
            '& img': {
              width: theme => theme.spacing(4),
              height: theme => theme.spacing(4),
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
      {chips}
      {description && <CardDescription>{description}</CardDescription>}
      {(caption || action) && (
        <CardFooter
          start={caption && <Text variant="content-caption" className="text-default-secondary">{caption}</Text>}
          end={action}
        />
      )}
    </Card>
  );
};

export default MarketplaceCard;
