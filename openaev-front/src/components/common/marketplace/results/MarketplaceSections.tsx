import { Box } from '@mui/material';
import { type ReactNode } from 'react';

import groupSections from './groupSections';
import MarketplaceGrid from './MarketplaceGrid';
import MarketplaceSectionHeader from './MarketplaceSectionHeader';

interface Props<T> {
  items: T[];
  sectionKey: (item: T) => string | undefined;
  sectionLabel: (key: string | undefined) => string;
  sectionIcon: (key: string | undefined) => ReactNode;
  renderItem: (item: T) => ReactNode;
  emptyState: ReactNode;
}

const MarketplaceSections = <T, >({
  items,
  sectionKey,
  sectionLabel,
  sectionIcon,
  renderItem,
  emptyState,
}: Props<T>) => {
  if (items.length === 0) {
    return <>{emptyState}</>;
  }
  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 2,
    }}
    >
      {groupSections(items, sectionKey).map(section => (
        <Box
          component="section"
          key={section.key ?? 'other'}
          sx={{
            display: 'flex',
            flexDirection: 'column',
            gap: 1.5,
          }}
        >
          <MarketplaceSectionHeader icon={sectionIcon(section.key)} label={sectionLabel(section.key)} count={section.items.length} />
          <MarketplaceGrid>{section.items.map(renderItem)}</MarketplaceGrid>
        </Box>
      ))}
    </Box>
  );
};

export default MarketplaceSections;
