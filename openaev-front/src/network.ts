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

// eslint-disable-next-line import/prefer-default-export
export const api = <T>(schema?: Schema<T> | null): AxiosInstance => {
  const instance = axios.create({ headers: { responseType: 'json' } });
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
      if (
        res
        && res.status === 503
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
