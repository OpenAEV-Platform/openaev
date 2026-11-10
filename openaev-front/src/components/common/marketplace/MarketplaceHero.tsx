import { Chip, Hero, HeroBody, HeroHeader, Text } from '@filigran/design-system';
import { Box } from '@mui/material';
import { type ComponentType, type FunctionComponent, type ReactNode } from 'react';

import { HeroStat, HeroStats } from '../detail/EntityDetailCommon';

interface Stat {
  id: string;
  label: string;
  value: number | string;
  color?: string;
  /** With an icon, the stat is a tile in the Hero body (design system Hero, as on the scenario page). */
  icon?: ComponentType<{ sx?: object }>;
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
}) => {
  const tileStats = stats.filter((stat): stat is Stat & Required<Pick<Stat, 'icon'>> => !!stat.icon);
  const chipStats = stats.filter(stat => !stat.icon);
  return (
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
            {(totalLabel || chipStats.length > 0) && (
              <Box sx={{
                display: 'flex',
                flexWrap: 'wrap',
                gap: 0.75,
              }}
              >
                {totalLabel && <Chip label={`${totalElements ?? 0} ${totalLabel}`} />}
                {chipStats.map(stat => <Chip key={stat.id} label={`${stat.value} ${stat.label}`} />)}
              </Box>
            )}
          </Box>
          {subtitle && <Text variant="content-base" className="text-default-secondary">{subtitle}</Text>}
        </Box>
      </HeroHeader>
      {(tileStats.length > 0 || bottomSlot) && (
        <HeroBody className="justify-between">
          {tileStats.length > 0 && (
            <HeroStats>
              {tileStats.map(stat => (
                <HeroStat key={stat.id} icon={stat.icon} label={stat.label} value={stat.value} color={stat.color} />
              ))}
            </HeroStats>
          )}
          {bottomSlot}
        </HeroBody>
      )}
    </Hero>
  );
};

export default MarketplaceHero;
