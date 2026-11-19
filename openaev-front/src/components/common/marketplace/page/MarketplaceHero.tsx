import { Hero, HeroHeader, Text } from '@filigran/design-system';
import { Box } from '@mui/material';
import { type FunctionComponent } from 'react';

interface Props {
  title: string;
  subtitle?: string;
}

// Page header shared by the marketplaces (integrations, threat arsenal, scenarios), on the design system Hero.
const MarketplaceHero: FunctionComponent<Props> = ({ title, subtitle }) => (
  <Hero aria-label={title}>
    <HeroHeader>
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
  </Hero>
);

export default MarketplaceHero;
