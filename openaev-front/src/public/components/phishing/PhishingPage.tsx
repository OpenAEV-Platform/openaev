import purify from 'dompurify';
import { useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router';

import Loader from '../../../components/Loader';
import { api } from '../../../network';
import { APP_BASE_PATH } from '../../../utils/Environment';

interface PhishingLandingPageReader {
  phishing_landing_page_name?: string;
  phishing_landing_page_html?: string;
  phishing_landing_page_css?: string;
}

/**
 * Public, unauthenticated renderer for a phishing landing page. Reached when the victim clicks the
 * link embedded in the lure email. Two shapes are supported:
 *
 * - Benign, tenant-less link (current): `/auth/{token}`. The tenant is resolved server-side from the
 *   globally unique token, so the URL leaks neither the tenant id nor the word "phishing". Content,
 *   click tracking and form capture go through the token-authenticated `/api/hosted/*` endpoints.
 * - Legacy link (already-sent emails): `/phishing/{tenantId}/{token}`, served through the older
 *   `/api/phishing/tracking/{tenantId}/*` endpoints. Kept so links sent before the redesign keep
 *   working.
 *
 * Every call targets the token-authenticated public endpoints directly (never the tenant-rewritten
 * API client) since the victim has no session.
 */

/**
 * Defense-in-depth mirror of the server-side redirect validation
 * (PhishingLandingPageService.validateRedirectUrl): only relative paths and http(s) URLs may reach
 * window.location.href, so a stored `javascript:` or `data:` scheme can never execute in the
 * OpenAEV origin. Browsers strip ASCII control/whitespace before parsing a scheme, so the value is
 * normalized the same way before inspection.
 */
const isSafeRedirect = (url: string): boolean => {
  // eslint-disable-next-line no-control-regex
  const normalized = url.replace(/[\u0000-\u0020]/g, '');
  const schemeSeparator = normalized.indexOf(':');
  const firstSlash = normalized.indexOf('/');
  const hasScheme = schemeSeparator > 0 && (firstSlash < 0 || schemeSeparator < firstSlash);
  if (!hasScheme) {
    return true;
  }
  const scheme = normalized.slice(0, schemeSeparator).toLowerCase();
  return scheme === 'http' || scheme === 'https';
};

const PhishingPage = () => {
  const { tenantId, token } = useParams() as {
    tenantId?: string;
    token: string;
  };

  // The benign route omits the tenant (resolved server-side from the token). The legacy route still
  // carries it, so target the matching public endpoint family.
  const pageUrl = tenantId
    ? `${APP_BASE_PATH}/api/phishing/tracking/${tenantId}/page/${token}`
    : `${APP_BASE_PATH}/api/hosted/page/${token}`;
  const submitUrl = tenantId
    ? `${APP_BASE_PATH}/api/phishing/tracking/${tenantId}/s/${token}`
    : `${APP_BASE_PATH}/api/hosted/s/${token}`;

  const [page, setPage] = useState<PhishingLandingPageReader | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const containerRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    let active = true;
    api<PhishingLandingPageReader>()
      .get(pageUrl)
      .then((response) => {
        if (!active) return;
        if (response.data) {
          setPage(response.data);
        } else {
          setNotFound(true);
        }
      })
      .catch(() => {
        if (active) setNotFound(true);
      });
    return () => {
      active = false;
    };
  }, [pageUrl]);

  const submit = async (fields: Record<string, string>) => {
    setSubmitting(true);
    try {
      const response = await api<{ redirect_url?: string }>().post(submitUrl, { data: fields });
      const redirectUrl = response.data?.redirect_url;
      if (redirectUrl && isSafeRedirect(redirectUrl)) {
        window.location.href = redirectUrl;
      }
    } catch {
      // Silently ignore: the victim should never see an application error.
    } finally {
      setSubmitting(false);
    }
  };

  // Capture the first form submission inside the rendered (untrusted) markup. The listener lives on
  // the wrapper so it survives dangerouslySetInnerHTML and does not require the author's markup to
  // wire anything up.
  useEffect(() => {
    const container = containerRef.current;
    if (!container || !page) return undefined;

    const onSubmit = (event: Event) => {
      event.preventDefault();
      const form = event.target as HTMLFormElement;
      const fields: Record<string, string> = {};
      const formData = new FormData(form);
      formData.forEach((value, key) => {
        fields[key] = typeof value === 'string' ? value : '';
      });
      void submit(fields);
    };

    container.addEventListener('submit', onSubmit, true);
    return () => {
      container.removeEventListener('submit', onSubmit, true);
    };
  }, [page]);

  if (notFound) {
    return null;
  }
  if (!page) {
    return <Loader />;
  }

  const sanitizedHtml = purify.sanitize(page.phishing_landing_page_html ?? '', { FORBID_TAGS: ['script'] });

  return (
    <div
      style={{
        minHeight: '100vh',
        width: '100%',
        opacity: submitting ? 0.6 : 1,
      }}
    >
      {page.phishing_landing_page_css
        ? <style>{page.phishing_landing_page_css}</style>
        : null}
      <div
        ref={containerRef}
        // eslint-disable-next-line react/no-danger
        dangerouslySetInnerHTML={{ __html: sanitizedHtml }}
      />
    </div>
  );
};

export default PhishingPage;
