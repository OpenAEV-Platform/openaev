import { useEffect, useRef, useState } from 'react';

import { getXtmHubOrigin } from '../xtm-hub-client';

const XTM_HUB_USER_PLATFORM_TOKEN_KEY = 'XTM_HUB_USER_PLATFORM_TOKEN_KEY';

interface Return { userPlatformToken: string | null }

const useXtmHubUserPlatformToken = (hubUrl?: string | null): Return => {
  const [userPlatformToken, setUserPlatformToken] = useState<string | null>(null);
  const hasRequestedToken = useRef(false);
  const hubOrigin = getXtmHubOrigin(hubUrl);

  useEffect(() => {
    const handleMessage = (event: MessageEvent) => {
      if (event.source === window.opener && event.origin === hubOrigin) {
        const { action, token: newToken } = event.data;
        if (action === 'set-token') {
          setUserPlatformToken(newToken);
          sessionStorage.setItem(XTM_HUB_USER_PLATFORM_TOKEN_KEY, JSON.stringify(newToken));
        }
      }
    };

    window.addEventListener('message', handleMessage);

    if (hubOrigin && !hasRequestedToken.current) {
      hasRequestedToken.current = true;
      window.opener?.postMessage({ action: 'refresh-token' }, hubOrigin);
    }

    return () => {
      window.removeEventListener('message', handleMessage);
    };
  }, [hubOrigin]);

  return { userPlatformToken };
};
export default useXtmHubUserPlatformToken;
