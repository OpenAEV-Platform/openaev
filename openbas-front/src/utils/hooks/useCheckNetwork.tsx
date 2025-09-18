import { useEffect, useState } from 'react';

interface UseNetworkCheckResult { isReachable: boolean | undefined }

const useNetworkCheck = (url?: string | null): UseNetworkCheckResult => {
  const [isReachable, setIsReachable] = useState<boolean | undefined>(undefined);

  useEffect(() => {
    if (!url) {
      setIsReachable(false);
      return;
    }

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 5000);
    fetch(url, {
      method: 'HEAD',
      mode: 'no-cors',
      signal: controller.signal,
    })
      .then(() => setIsReachable(true))
      .catch(() => setIsReachable(false))
      .finally(() => clearTimeout(timeoutId));
  }, [url]);

  return { isReachable };
};

export default useNetworkCheck;
