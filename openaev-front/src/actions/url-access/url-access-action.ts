import { simpleCall } from '../../utils/Action';

const URLACCESS_TOKEN_URI = '/api/url/access';

export const fetchUrlAccessAction = (token: string) => {
  const uri = `${URLACCESS_TOKEN_URI}?token=${token}`;
  return simpleCall(uri);
};

export default fetchUrlAccessAction;
