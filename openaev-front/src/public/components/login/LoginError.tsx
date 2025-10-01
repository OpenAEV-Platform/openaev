import { type FunctionComponent } from 'react';
import { useLocation } from 'react-router';

import Message from '../../../components/Message';
import { MESSAGING$ } from '../../../utils/Environment';

const ERROR_KEY = 'error=';

const LoginError: FunctionComponent = () => {
  const { search } = useLocation();
  const params: string[] = search.split('&');
  let error = '';
  params.forEach((param) => {
    if (param.includes(ERROR_KEY)) {
      error = param.substring(param.indexOf(ERROR_KEY) + ERROR_KEY.length);
    }
    if (error) {
      MESSAGING$.notifyError(decodeURIComponent(error));
    }
  });
  return <Message sticky={false} />;
};

export default LoginError;
