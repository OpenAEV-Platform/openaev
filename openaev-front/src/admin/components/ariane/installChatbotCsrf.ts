/**
 * Installs a one-time `window.fetch` wrapper that injects the Spring Security
 * `X-XSRF-TOKEN` header into requests made to the XTM One chat endpoints.
 *
 * The bundled `@filigran/chatbot` widget uses a plain `fetch(url)` call with no
 * way to customize headers, so we have to intercept globally. The wrapper is
 * scoped to `/api/xtmone/chat/` URLs and is a no-op for everything else.
 *
 * The `XSRF-TOKEN` cookie is already bootstrapped by the OpenAEV axios
 * interceptors (see network.ts) before the user can open the chat panel.
 * The cookie is read through the shared `getCsrfToken` helper from `network.ts`
 * so cookie name, decoding and bootstrap behaviour stay in sync with axios callers.
 */
import { getCsrfToken } from '../../../network';

const FLAG = '__openaev_chatbot_csrf_installed__';
const CHAT_URL_PREFIX = '/api/xtmone/chat/';

const matchesChatUrl = (input: RequestInfo | URL): boolean => {
  let url: string;
  if (typeof input === 'string') {
    url = input;
  } else if (input instanceof URL) {
    url = input.toString();
  } else {
    url = input.url;
  }
  try {
    const path = new URL(url, window.location.origin).pathname;
    return path.startsWith(CHAT_URL_PREFIX);
  } catch {
    return false;
  }
};

const installChatbotCsrf = (): void => {
  const w = window as unknown as Record<string, unknown>;
  if (w[FLAG]) return;
  w[FLAG] = true;

  const originalFetch = window.fetch.bind(window);
  window.fetch = (input, init = {}) => {
    if (!matchesChatUrl(input)) return originalFetch(input, init);
    const csrf = getCsrfToken();
    if (!csrf) return originalFetch(input, init);
    const baseHeaders = init.headers ?? (input instanceof Request ? input.headers : undefined);
    const headers = new Headers(baseHeaders);
    if (!headers.has('X-XSRF-TOKEN')) headers.set('X-XSRF-TOKEN', csrf);
    return originalFetch(input, {
      ...init,
      headers,
    });
  };
};

export default installChatbotCsrf;
