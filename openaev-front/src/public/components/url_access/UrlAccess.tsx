import { useEffect } from 'react';

import Loader from '../../../components/Loader';

const ERROR_URL = '/error?code=401';

const UrlAccess = () => {
  useEffect(() => {
    const run = async () => {
      const params = new URLSearchParams(globalThis.window.location.search);
      const token = params.get('token');

      if (!token) {
        globalThis.window.location.assign(ERROR_URL);
        return;
      }

      try {
        const response = await fetch(`/api/url/access?token=${encodeURIComponent(token)}`, {
          credentials: 'include',
        });

        if (response.status === 401) {
          globalThis.window.location.assign(ERROR_URL);
          return;
        }

        if (response.redirected && response.url) {
          globalThis.window.location.assign(response.url);
          return;
        }

        // Fallback to the generic error page for unexpected responses.
        globalThis.window.location.assign(ERROR_URL);
      } catch {
        globalThis.window.location.assign(ERROR_URL);
      }
    };

    void run();
  }, []);

  return <Loader />;
};

export default UrlAccess;

