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
  /** Small label above the title, e.g. the kind of content. */
  overline?: string;
  /** One line under the header, e.g. the author. */
  subtitle?: string;
  description?: string;
  chips?: ReactNode;
  /** Label and value pairs under the description. */
  info?: {
    label: string;
    value: string;
  }[];
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
  info,
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
              width: theme => theme.spacing(3),
              height: theme => theme.spacing(3),
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
      {info && (
        <CardInfoGroup type="block">
          {info.map(item => <CardInfo key={item.label} label={item.label} value={item.value} />)}
        </CardInfoGroup>
      )}
      {action && <CardFooter end={action} />}
    </Card>
  );
};

export default MarketplaceCard;
