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
 * tracking link: the backend marks the click and 302s to /phishing/{tenantId}/{token}, which this
 * route handles. It fetches the sanitized page content by token, renders it full-screen, and
 * intercepts the first form submit to capture the entered fields (posted to the submit endpoint),
 * then follows the configured redirect URL.
 *
 * Every call targets the token-authenticated public tracking endpoints directly (never the
 * tenant-rewritten API client) since the victim has no session and the tenant lives in the path.
 */
const PhishingPage = () => {
  const { tenantId, token } = useParams() as {
    tenantId: string;
    token: string;
  };
  const [page, setPage] = useState<PhishingLandingPageReader | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const containerRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    let active = true;
    api<PhishingLandingPageReader>()
      .get(`${APP_BASE_PATH}/api/phishing/tracking/${tenantId}/page/${token}`)
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
  }, [tenantId, token]);

  const submit = async (fields: Record<string, string>) => {
    setSubmitting(true);
    try {
      const response = await api<{ redirect_url?: string }>().post(
        `${APP_BASE_PATH}/api/phishing/tracking/${tenantId}/s/${token}`,
        { data: fields },
      );
      const redirectUrl = response.data?.redirect_url;
      if (redirectUrl) {
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
