import { useState } from 'react';
import { useLocation, useNavigationType } from 'react-router';

/**
 * Identifies the user's visit to the news feed page: it changes when they navigate to the page, including
 * to the page they are already on (a click on the top bar icon), so that the page reloads its list and marks
 * the items read again.
 *
 * It must not change when the list writes its own search to the query string of the URL (`useUriState`, a
 * `replace` navigation that keeps the pathname and always carries the `query` parameter): the router gives
 * that navigation a new `location.key`, so a key taken from it would remount the list, which writes the URL
 * again, and so on forever.
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

  const isOwnUrlSync = navigationType === 'REPLACE'
    && pathname === visit.pathname
    && new URLSearchParams(search).has('query');
  if (key !== visit.key && !isOwnUrlSync) {
    // Derived from the render, not from an effect, so that the page never renders with the stale key.
    setVisit({
      key,
      pathname,
    });
  }
  return isOwnUrlSync ? visit.key : key;
};

export default useNewsFeedVisitKey;
