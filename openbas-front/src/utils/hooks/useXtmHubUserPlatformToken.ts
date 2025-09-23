import { useEffect, useRef, useState } from 'react';

const XTM_HUB_USER_PLATFORM_TOKEN_KEY = 'XTM_HUB_USER_PLATFORM_TOKEN_KEY';

interface Return { userPlatformToken: string | null }

const useXtmHubUserPlatformToken = (): Return => {
  const [userPlatformToken, setUserPlatformToken] = useState<string | null>(null);
  const hasRequestedToken = useRef(false);

  useEffect(() => {
    const handleMessage = (event: MessageEvent) => {
      if (event.source === window.opener) {
        const { action, token: newToken } = event.data;
        if (action === 'set-token') {
          setUserPlatformToken(newToken);
          sessionStorage.setItem(XTM_HUB_USER_PLATFORM_TOKEN_KEY, JSON.stringify(newToken));
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

  return { userPlatformToken };
};
export default useXtmHubUserPlatformToken;
