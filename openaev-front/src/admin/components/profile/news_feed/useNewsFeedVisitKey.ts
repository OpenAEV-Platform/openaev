import { useState } from 'react';
import { useLocation, useNavigationType } from 'react-router';

/**
 * Identifies the user's visit to the news feed page: it changes when they navigate to the page, including
 * to the page they are already on (a click on the top bar icon), so that the page reloads its list and marks
 * the items read again.
 *
 * It must not change when the list syncs its own search with the query string of the URL (`useUriState`, a
 * `replace` navigation that keeps the pathname, and that writes the `query` parameter or, back on the default
 * state of the list, removes it): the router gives that navigation a new `location.key`, so a key taken from
 * it would remount the list, which writes the URL again, and so on forever.
 *
 * On the default state of the list the URL is the bare page path, so a click on the top bar icon is a
 * `replace` of that same URL too: it is told from the list's own sync because it neither changes the query
 * string nor carries the `query` parameter.
 */
const useNewsFeedVisitKey = (): string => {
  const {
    key,
    pathname,
    search,
  } = useLocation();
  const navigationType = useNavigationType();
  const [visit, setVisit] = useState({
    key,
    pathname,
  });
  const [seen, setSeen] = useState({
    key,
    search,
  });

  // Derived from the render, not from an effect, so that the page never renders with the stale key.
  if (key !== seen.key) {
    const isOwnUrlSync = navigationType === 'REPLACE'
      && pathname === visit.pathname
      && (search !== seen.search || new URLSearchParams(search).has('query'));
    setSeen({
      key,
      search,
    });
    if (!isOwnUrlSync) {
      setVisit({
        key,
        pathname,
      });
    }
  }
  return visit.key;
};

export default useNewsFeedVisitKey;
