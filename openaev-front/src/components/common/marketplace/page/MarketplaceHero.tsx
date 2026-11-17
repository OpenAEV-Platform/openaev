import { Hero, HeroBody, HeroHeader, HeroStat, type IconName, Text } from '@filigran/design-system';
import { Box } from '@mui/material';
import { type FunctionComponent, type ReactNode } from 'react';
import { Link } from 'react-router';

interface Stat {
  id: string;
  label: string;
  value: number;
  icon: IconName;
  to: string;
}

interface Props {
  title: string;
  subtitle?: string;
  stats?: Stat[];
  action?: ReactNode;
}

// Page header shared by the marketplaces (integrations, threat arsenal, scenarios), on the design system Hero.
const MarketplaceHero: FunctionComponent<Props> = ({ title, subtitle, stats = [], action }) => (
  <Hero aria-label={title}>
    <HeroHeader action={action}>
      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 0.5,
        minWidth: 0,
      }}
      >
        <Text as="h1" variant="title-md" style={{ margin: 0 }}>{title}</Text>
        {subtitle && <Text variant="content-base" className="text-default-secondary">{subtitle}</Text>}
      </Box>
    </HeroHeader>
    {stats.length > 0 && (
      <HeroBody>
        {stats.map(stat => (
          <HeroStat key={stat.id} asChild icon={stat.icon} label={stat.label} value={stat.value} severity="info">
            <Link to={stat.to} />
          </HeroStat>
        ))}
      </HeroBody>
    )}
  </Hero>
);

export default MarketplaceHero;
