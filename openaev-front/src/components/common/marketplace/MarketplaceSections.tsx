import { type ReactNode } from 'react';

import MarketplaceGrid from './MarketplaceGrid';
import MarketplaceSectionHeader from './MarketplaceSectionHeader';
import { type ViewMode } from './useMarketplaceView';

interface Props<T> {
  items: T[];
  // Items come sorted by this key: consecutive items with the same key make a section.
  sectionKey: (item: T) => string | undefined;
  sectionLabel: (key: string | undefined) => string;
  sectionIcon: ReactNode | ((key: string | undefined) => ReactNode);
  renderItem: (item: T) => ReactNode;
  loading: boolean;
  emptyState: ReactNode;
  view?: ViewMode;
  overlineLabel?: string;
}

// Marketplace results in sections (connector type, action domain, scenario category).
const MarketplaceSections = <T, >({
  items,
  sectionKey,
  sectionLabel,
  sectionIcon,
  renderItem,
  loading,
  emptyState,
  view,
  overlineLabel,
}: Props<T>) => {
  if (loading || items.length === 0) {
    return <MarketplaceGrid view={view} overlineLabel={overlineLabel} loading={loading} isEmpty={items.length === 0} emptyState={emptyState}>{null}</MarketplaceGrid>;
  }
  const sections: {
    key: string | undefined;
    items: T[];
  }[] = [];
  items.forEach((item) => {
    const key = sectionKey(item) || undefined;
    const last = sections[sections.length - 1];
    if (last && last.key === key) {
      last.items.push(item);
    } else {
      sections.push({
        key,
        items: [item],
      });
    }
  });
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 16,
    }}
    >
      {sections.map((section, idx) => (
        <section
          key={`${section.key ?? 'other'}-${idx}`}
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 12,
          }}
        >
          <MarketplaceSectionHeader icon={typeof sectionIcon === 'function' ? sectionIcon(section.key) : sectionIcon} label={sectionLabel(section.key)} count={section.items.length} />
          <MarketplaceGrid view={view} overlineLabel={overlineLabel} loading={false} isEmpty={false} emptyState={null}>
            {section.items.map(renderItem)}
          </MarketplaceGrid>
        </section>
      ))}
    </div>
  );
};

export default MarketplaceSections;
