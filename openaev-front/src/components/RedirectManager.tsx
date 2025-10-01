import { type FunctionComponent, type ReactElement, useEffect } from 'react';
import { useNavigate } from 'react-router';

import { MESSAGING$ } from '../utils/Environment';

interface Props { children: ReactElement }

const RedirectManager: FunctionComponent<Props> = ({ children }) => {
  const navigate = useNavigate();

  useEffect(() => {
    const subscription = MESSAGING$.redirect.subscribe({ next: url => navigate(url) });

    return () => subscription.unsubscribe();
  }, []);

  return children;
};

export default RedirectManager;
