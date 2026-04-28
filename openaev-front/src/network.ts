import axios, { type AxiosInstance, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';
import { normalize, type Schema } from 'normalizr';

interface ExtendedAxiosRequestConfig extends InternalAxiosRequestConfig {
  // eslint-disable-next-line @typescript-eslint/naming-convention
  __isRetryRequest?: boolean;
}

interface ApiErrorResponse {
  status: number;
  [key: string]: unknown;
}

let csrfBootstrapPromise: Promise<void> | null = null;

const hasCsrfCookie = (): boolean =>
  document.cookie.split('; ').some(row => row.startsWith('XSRF-TOKEN='));

const ensureCsrfCookie = async (instance: AxiosInstance): Promise<void> => {
  if (hasCsrfCookie()) return;

  csrfBootstrapPromise ??= instance
    .get('/csrf')
    .then(() => undefined)
    .finally(() => {
      csrfBootstrapPromise = null;
    });

  await csrfBootstrapPromise;
};

// eslint-disable-next-line import/prefer-default-export
export const api = <T>(schema?: Schema<T> | null): AxiosInstance => {
  const instance = axios.create({
    headers: { responseType: 'json' },
    withCredentials: true,
  });

  // Intercept REQUEST to inject CSRF token
  instance.interceptors.request.use(async (config) => {
    const method = (config.method ?? 'GET').toUpperCase();
    const mutating = ['POST', 'PUT', 'DELETE', 'PATCH'].includes(method);

    if (mutating) {
      await ensureCsrfCookie(instance);

      const match = document.cookie
        .split('; ')
        .find(row => row.startsWith('XSRF-TOKEN='));
      const token = match ? decodeURIComponent(match.split('=')[1]) : null;

      if (token) {
        config.headers['X-XSRF-TOKEN'] = token;
      }
    }
    return config;
  });

  // Intercept to apply schema and test unauthorized users
  instance.interceptors.response.use(
    (response: AxiosResponse) => {
      if (response.data && schema) {
        if (typeof response.data === 'object') {
          response.data = normalize(response.data, schema);
        }
      }
      return response;
    },
    (err) => {
      const res = err.response;
      const config = err.config as ExtendedAxiosRequestConfig | undefined;

      // Automatic retry on 403 if XSRF cookie have just been dropped
      // eslint-disable-next-line no-underscore-dangle
      if (res?.status === 403 && config && !config.__isRetryRequest) {
        const csrfCookie = document.cookie
          .split('; ')
          .find(row => row.startsWith('XSRF-TOKEN='));

        if (csrfCookie) {
          // eslint-disable-next-line no-underscore-dangle
          config.__isRetryRequest = true;
          return instance(config);
        }
      }

      if (
        res?.status === 503
        && config
        // eslint-disable-next-line no-underscore-dangle
        && !config.__isRetryRequest
      ) {
        // eslint-disable-next-line no-underscore-dangle
        config.__isRetryRequest = true;
        return axios(config);
      }
      if (res) {
        // eslint-disable-next-line prefer-promise-reject-errors
        return Promise.reject({
          status: res.status,
          ...res.data,
        } as ApiErrorResponse);
      }
      // eslint-disable-next-line prefer-promise-reject-errors
      return Promise.reject(false);
    },
  );
  return instance;
};
