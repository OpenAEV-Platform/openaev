import { Spinner } from '@filigran/design-system';
import { Box } from '@mui/material';
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
    <Box
      ref={sentinel}
      sx={{
        display: 'flex',
        justifyContent: 'center',
        padding: 2,
      }}
    >
      {loading && <Spinner size="md" />}
    </Box>
  );
};

export default MarketplaceLoadMore;
