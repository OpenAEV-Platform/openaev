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
 */
const FLAG = '__openaev_chatbot_csrf_installed__';
const CHAT_URL_PREFIX = '/api/xtmone/chat/';

const readCsrfToken = (): string | null => {
  const match = document.cookie.split('; ').find(row => row.startsWith('XSRF-TOKEN='));
  return match ? decodeURIComponent(match.split('=')[1]) : null;
};

const matchesChatUrl = (input: RequestInfo | URL): boolean => {
  const url = typeof input === 'string'
    ? input
    : input instanceof URL ? input.toString() : input.url;
  try {
    const path = new URL(url, window.location.origin).pathname;
    return path.startsWith(CHAT_URL_PREFIX);
  } catch {
    return false;
  }
};

const installChatbotCsrf = (): void => {
  const w = window as Window & Record<string, unknown>;
  if (w[FLAG]) return;
  w[FLAG] = true;

  const originalFetch = window.fetch.bind(window);
  window.fetch = (input, init = {}) => {
    if (!matchesChatUrl(input)) return originalFetch(input, init);
    const csrf = readCsrfToken();
    if (!csrf) return originalFetch(input, init);
    const headers = new Headers(init.headers ?? (input instanceof Request ? input.headers : undefined));
    if (!headers.has('X-XSRF-TOKEN')) headers.set('X-XSRF-TOKEN', csrf);
    return originalFetch(input, { ...init, headers });
  };
};

export default installChatbotCsrf;
