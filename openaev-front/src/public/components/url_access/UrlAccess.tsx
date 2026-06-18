import validateUrlAccessAction from '../../../actions/url-access/url-access-action';
import Loader from '../../../components/Loader';

const ERROR_URL = '/handle-error';
const CODE_QUERY_PARAM = '?code=';

const UrlAccess = () => {
  const params = new URLSearchParams(globalThis.window.location.search);
  const token = params.get('token');

  if (!token) {
    globalThis.window.location.assign(ERROR_URL);
  }

  validateUrlAccessAction(encodeURIComponent(token!))
    .then((response) => {
      const redirectedUrl = response?.request?.responseURL;
      if (redirectedUrl && redirectedUrl !== globalThis.window.location.href) {
        globalThis.window.location.assign(redirectedUrl);
      }
    })
    .catch((error) => {
      if (error.status === 401) {
        globalThis.window.location.assign(ERROR_URL + CODE_QUERY_PARAM + error.status);
      } else {
        // Fallback to the generic error page for unexpected responses.
        globalThis.window.location.assign(ERROR_URL);
      }
    });

  return <Loader />;
};

export default UrlAccess;
