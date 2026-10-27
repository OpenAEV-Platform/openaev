import { Chip, Hero, HeroBody, HeroHeader, Text } from '@filigran/design-system';
import { Box } from '@mui/material';
import { type FunctionComponent, type ReactNode } from 'react';

interface Stat {
  id: string;
  label: string;
  value: number | string;
  color?: string;
}

interface Props {
  title: string;
  subtitle?: string;
  /** Counters beside the title; left out on pages that show none (integrations). */
  totalLabel?: string;
  totalElements?: number;
  stats?: Stat[];
  rightSlot?: ReactNode;
  bottomSlot?: ReactNode;
}

// Page header shared by the marketplaces (integrations, threat arsenal, scenarios), on the design system Hero.
const MarketplaceHero: FunctionComponent<Props> = ({
  title,
  subtitle,
  totalLabel,
  totalElements,
  stats = [],
  rightSlot,
  bottomSlot,
}) => (
  <Hero aria-label={title}>
    <HeroHeader action={rightSlot}>
      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 0.5,
        minWidth: 0,
      }}
      >
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: 1.5,
          minWidth: 0,
        }}
        >
          <Text as="h1" variant="title-md" style={{ margin: 0 }}>{title}</Text>
          {(totalLabel || stats.length > 0) && (
            <Box sx={{
              display: 'flex',
              flexWrap: 'wrap',
              gap: 0.75,
            }}
            >
              {totalLabel && <Chip label={`${totalElements ?? 0} ${totalLabel}`} />}
              {stats.map(stat => <Chip key={stat.id} label={`${stat.value} ${stat.label}`} />)}
            </Box>
          )}
        </Box>
        {subtitle && <Text variant="content-base" className="text-default-secondary">{subtitle}</Text>}
      </Box>
    </HeroHeader>
    {bottomSlot && <HeroBody>{bottomSlot}</HeroBody>}
  </Hero>
);

export default MarketplaceHero;
