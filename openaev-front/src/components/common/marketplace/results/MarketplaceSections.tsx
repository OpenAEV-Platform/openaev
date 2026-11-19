import { Card, CardFooter, CardHeader, CardTitle, SkeletonGroup, Thumbnail } from '@filigran/design-system';
import { Box } from '@mui/material';
import { type ReactNode } from 'react';

import { useFormatter } from '../../../i18n';
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
  loading?: boolean;
}

const MarketplaceSections = <T, >({
  items,
  sectionKey,
  sectionLabel,
  sectionIcon,
  renderItem,
  emptyState,
  loading = false,
}: Props<T>) => {
  const { t } = useFormatter();
  if (loading) {
    return (
      <SkeletonGroup loading label={t('Loading')}>
        <MarketplaceGrid>
          {Array.from({ length: 12 }).map((_, idx) => (
            <Card key={idx} loading>
              <CardHeader icon={<Thumbnail>{null}</Thumbnail>}>
                <CardTitle>{t('Loading')}</CardTitle>
              </CardHeader>
              <CardFooter />
            </Card>
          ))}
        </MarketplaceGrid>
      </SkeletonGroup>
    );
  }
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
