import { useCallback, useEffect, useRef, useState } from 'react';

import { fetchIocValidation } from '../../../actions/ioc_validations/ioc-validation-actions';
import type { IocValidationOutput } from '../../../utils/api-types';

export type IocValidationLoadError = 'not_found' | 'failed';

/**
 * The IOC validation request of a page, loaded by id. The route reuses its page for another request: what is loaded is
 * kept with the request it belongs to, and a response (load, poll or decision) for a request that is no longer the one
 * displayed is dropped, so a page never shows, nor acts upon, the previous request. Only a 404 means the request is
 * gone; any other failure keeps what is loaded, and polling, going.
 */
const useIocValidation = (iocValidationId: string) => {
  const [loaded, setLoaded] = useState<{
    id: string;
    validation: IocValidationOutput;
  } | null>(null);
  const [failure, setFailure] = useState<{
    id: string;
    error: IocValidationLoadError;
  } | null>(null);
  const displayedId = useRef(iocValidationId);
  useEffect(() => {
    displayedId.current = iocValidationId;
  }, [iocValidationId]);

  const load = useCallback(() => fetchIocValidation(iocValidationId)
    .then((result: { data: IocValidationOutput }) => {
      if (displayedId.current !== iocValidationId) {
        return;
      }
      setLoaded({
        id: iocValidationId,
        validation: result.data,
      });
      setFailure(null);
    })
    .catch((error: {
      status?: number;
      response?: { status?: number };
    }) => {
      if (displayedId.current !== iocValidationId) {
        return;
      }
      setFailure({
        id: iocValidationId,
        error: (error?.response?.status ?? error?.status) === 404 ? 'not_found' : 'failed',
      });
    }), [iocValidationId]);

  const update = useCallback((validation: IocValidationOutput) => {
    if (validation.ioc_validation_id === displayedId.current) {
      setLoaded({
        id: validation.ioc_validation_id,
        validation,
      });
    }
  }, []);

  return {
    iocValidation: loaded?.id === iocValidationId ? loaded.validation : null,
    loadError: failure?.id === iocValidationId ? failure.error : null,
    load,
    update,
  };
};

export default useIocValidation;
