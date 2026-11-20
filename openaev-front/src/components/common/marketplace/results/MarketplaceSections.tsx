import { Card, CardFooter, CardHeader, CardTitle, SkeletonGroup, Thumbnail } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type ReactNode } from 'react';

import { useFormatter } from '../../../i18n';
import groupSections from './groupSections';
import MarketplaceGrid from './MarketplaceGrid';
import MarketplaceSectionHeader from './MarketplaceSectionHeader';

interface Sections<T> {
  sectionKey: (item: T) => string | undefined;
  sectionLabel: (key: string | undefined) => string;
  sectionIcon: (key: string | undefined) => ReactNode;
}

interface Props<T> {
  items: T[];
  // Without sections, the items fill one grid.
  sections?: Sections<T>;
  renderItem: (item: T) => ReactNode;
  emptyState: ReactNode;
  loading?: boolean;
}

const MarketplaceSections = <T, >({
  items,
  sections,
  renderItem,
  emptyState,
  loading = false,
}: Props<T>) => {
  const { t } = useFormatter();
  const theme = useTheme();
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
  if (!sections) {
    return <MarketplaceGrid>{items.map(renderItem)}</MarketplaceGrid>;
  }
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(2),
    }}
    >
      {groupSections(items, sections.sectionKey).map(section => (
        <section
          key={section.key ?? 'other'}
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: theme.spacing(1.5),
          }}
        >
          <MarketplaceSectionHeader icon={sections.sectionIcon(section.key)} label={sections.sectionLabel(section.key)} count={section.items.length} />
          <MarketplaceGrid>{section.items.map(renderItem)}</MarketplaceGrid>
        </section>
      ))}
    </div>
  );
};

export default MarketplaceSections;
