import axios, { type AxiosError, type AxiosInstance, type AxiosRequestConfig, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';
import { normalize, type NormalizedSchema, type Schema } from 'normalizr';

import { type EntityKeys } from './reducers/entities';

type ElementType<T> = T extends readonly (infer U)[] ? U : T;
export interface CustomAxiosResponse<T = unknown> extends AxiosResponse<T> { normalizedData: NormalizedSchema<Record<EntityKeys, Record<string, ElementType<T>>>, string> }

interface RetryableAxiosRequestConfig extends InternalAxiosRequestConfig { __isRetryRequest?: boolean }

export interface CustomAxiosInstance<TData = unknown> extends Omit<AxiosInstance, 'request' | 'get' | 'delete' | 'head' | 'options' | 'post' | 'put' | 'patch'> {
  request<T = TData, R = CustomAxiosResponse<T>>(config: AxiosRequestConfig): Promise<R>;
  get<T = TData, R = CustomAxiosResponse<T>>(url: string, config?: AxiosRequestConfig): Promise<R>;
  delete<T = TData, R = CustomAxiosResponse<T>>(url: string, config?: AxiosRequestConfig): Promise<R>;
  head<T = TData, R = CustomAxiosResponse<T>>(url: string, config?: AxiosRequestConfig): Promise<R>;
  options<T = TData, R = CustomAxiosResponse<T>>(url: string, config?: AxiosRequestConfig): Promise<R>;
  post<T = TData, R = CustomAxiosResponse<T>>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<R>;
  put<T = TData, R = CustomAxiosResponse<T>>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<R>;
  patch<T = TData, R = CustomAxiosResponse<T>>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<R>;
}

export const api = <T = unknown>(schema: Schema) => {
  const instance = axios.create({ headers: { responseType: 'json' } });

  // Intercept to apply schema
  instance.interceptors.response.use(
    (response: AxiosResponse<T>) => {
      if (response.data && schema && typeof response.data === 'object') {
        const customResponse = response as CustomAxiosResponse<T>;
        customResponse.normalizedData = normalize(response.data, schema);
        return customResponse;
      }
      return response;
    },
    (err: AxiosError) => {
      const res = err.response;
      const config = err.config as RetryableAxiosRequestConfig;
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
        if (typeof res.data === 'object') {
          // eslint-disable-next-line prefer-promise-reject-errors
          return Promise.reject({
            status: res.status,
            ...(res.data),
          });
        }
        // eslint-disable-next-line prefer-promise-reject-errors
        return Promise.reject({ status: res.status });
      }
      // eslint-disable-next-line prefer-promise-reject-errors
      return Promise.reject(false);
    },
  );
  if (schema) {
    return instance as CustomAxiosInstance<T>;
  }
  return instance;
};

export const simpleApi = axios.create({ headers: { responseType: 'json' } });
