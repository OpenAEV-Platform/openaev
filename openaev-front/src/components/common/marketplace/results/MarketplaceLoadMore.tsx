import { Spinner } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useRef } from 'react';

interface Props {
  hasMore: boolean;
  loading: boolean;
  // The first page is loading (new search or filter): the old count must not ask for page two.
  paused: boolean;
  onLoadMore: () => void;
}

// Bottom of a marketplace list: asks for the next page once it scrolls into view.
const MarketplaceLoadMore: FunctionComponent<Props> = ({ hasMore, loading, paused, onLoadMore }) => {
  const theme = useTheme();
  const sentinel = useRef<HTMLDivElement>(null);
  const loadMore = useRef(onLoadMore);
  loadMore.current = onLoadMore;

  useEffect(() => {
    const node = sentinel.current;
    if (!node || !hasMore || loading || paused) return undefined;
    const observer = new IntersectionObserver((entries) => {
      if (entries.some(entry => entry.isIntersecting)) loadMore.current();
    }, { rootMargin: '200px' });
    observer.observe(node);
    return () => observer.disconnect();
  }, [hasMore, loading, paused]);

  if (!hasMore && !loading) return null;
  return (
    <div
      ref={sentinel}
      style={{
        display: 'flex',
        justifyContent: 'center',
        padding: theme.spacing(2),
      }}
    >
      {loading && <Spinner size="md" />}
    </div>
  );
};

export default MarketplaceLoadMore;
