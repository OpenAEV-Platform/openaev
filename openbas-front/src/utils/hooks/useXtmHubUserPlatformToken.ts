import { useEffect, useRef, useState } from 'react';

import { setSessionStorageItem } from '../sessionStorage';

const XTM_HUB_USER_PLATFORM_TOKEN_KEY = 'XTM_HUB_USER_PLATFORM_TOKEN_KEY';

interface Return { token: string | null }

const useXtmHubUserPlatformToken = (): Return => {
  const [token, setToken] = useState<string | null>(null);
  const hasRequestedToken = useRef(false);

  useEffect(() => {
    const handleMessage = (event: MessageEvent) => {
      if (event.source === window.opener) {
        const { action, token: newToken } = event.data;
        if (action === 'set-token') {
          setToken(newToken);
          setSessionStorageItem<string>(XTM_HUB_USER_PLATFORM_TOKEN_KEY, newToken);
        }
      }
    };

    window.addEventListener('message', handleMessage);

    if (!hasRequestedToken.current) {
      hasRequestedToken.current = true;
      window.opener?.postMessage({ action: 'refresh-token' }, '*');
    }

    return () => {
      window.removeEventListener('message', handleMessage);
    };
  }, []);

  return { token };
};
export default useXtmHubUserPlatformToken;
