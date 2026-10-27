import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { type ReactNode, useState } from 'react';

interface Props { children: ReactNode }

/**
 * Anonymous pages (challenge player, channels, lessons...) render shared components, and a shared
 * component may read session data through queries. Without a session there is nothing to read: a
 * request would fail with a 401, which resets the store the public page relies on. Every query is
 * disabled, so these components render empty, like they would from an empty store.
 */
const PublicQueryProvider = ({ children }: Props) => {
  const [queryClient] = useState(() => new QueryClient({ defaultOptions: { queries: { enabled: false } } }));
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
};

export default PublicQueryProvider;
