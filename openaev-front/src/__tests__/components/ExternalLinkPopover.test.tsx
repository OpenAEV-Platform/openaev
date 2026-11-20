import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { IntlProvider } from 'react-intl';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import ExternalLinkPopover from '../../components/ExternalLinkPopover';

const renderPopover = (externalLink: string | URL | undefined) => {
  const setDisplayExternalLink = vi.fn();
  const setExternalLink = vi.fn();
  render(
    <IntlProvider locale="en" defaultLocale="en" onError={() => {}}>
      <ExternalLinkPopover
        displayExternalLink
        externalLink={externalLink}
        setDisplayExternalLink={setDisplayExternalLink}
        setExternalLink={setExternalLink}
      />
    </IntlProvider>,
  );
  fireEvent.click(screen.getByRole('button', { name: 'Browse the link' }));
  return {
    setDisplayExternalLink,
    setExternalLink,
  };
};

describe('ExternalLinkPopover', () => {
  let windowOpen: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    windowOpen = vi.spyOn(window, 'open').mockImplementation(() => null);
  });
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it.each([
    ['an https url', 'https://filigran.io/docs'],
    ['an http url', 'http://filigran.io/docs'],
    ['a mailto link', 'mailto:contact@filigran.io'],
    ['a tel link', 'tel:+33123456789'],
    ['an URL object', new URL('https://filigran.io/docs')],
  ])('opens %s in a new tab', (_label, link) => {
    renderPopover(link);
    expect(windowOpen).toHaveBeenCalledTimes(1);
    expect(windowOpen).toHaveBeenCalledWith(link.toString(), '_blank');
  });

  it.each([
    ['a javascript: url', 'javascript:alert(document.domain)'],
    ['a mixed-case javascript: url', 'JaVaScRiPt:alert(1)'],
    ['a javascript: url with leading whitespace', ' \tjavascript:alert(1)'],
    ['a data: url', 'data:text/html,<script>alert(1)</script>'],
    ['a vbscript: url', 'vbscript:msgbox(1)'],
    ['a file: url', 'file:///etc/passwd'],
    ['a relative path', '/api/settings'],
    ['an empty string', ''],
    ['undefined', undefined],
  ])('never opens %s', (_label, link) => {
    renderPopover(link);
    expect(windowOpen).not.toHaveBeenCalled();
  });

  it('still closes the dialog and clears the link when the link is refused', () => {
    const {
      setDisplayExternalLink,
      setExternalLink,
    } = renderPopover('javascript:alert(1)');
    expect(setDisplayExternalLink).toHaveBeenCalledWith(false);
    expect(setExternalLink).toHaveBeenCalledWith(undefined);
  });
});
