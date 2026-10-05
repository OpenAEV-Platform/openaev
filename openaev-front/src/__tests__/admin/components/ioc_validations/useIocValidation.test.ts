import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { fetchIocValidation } from '../../../../actions/ioc_validations/ioc-validation-actions';
import useIocValidation from '../../../../admin/components/ioc_validations/useIocValidation';
import { type IocValidationOutput } from '../../../../utils/api-types';

vi.mock('../../../../actions/ioc_validations/ioc-validation-actions', () => ({ fetchIocValidation: vi.fn() }));

const validation = (id: string) => ({
  ioc_validation_id: id,
  ioc_validation_status: 'AWAITING_APPROVAL',
}) as unknown as IocValidationOutput;

// A fetch resolved by the test, in the order it chooses
const deferredFetch = () => {
  let resolve: (value: { data: IocValidationOutput }) => void = () => {};
  const promise = new Promise<{ data: IocValidationOutput }>((done) => {
    resolve = done;
  });
  return {
    promise,
    resolve,
  };
};

describe('useIocValidation', () => {
  beforeEach(() => {
    vi.mocked(fetchIocValidation).mockReset();
  });

  it('never shows the previous request once the page displays another one, whatever the order of the responses', async () => {
    const slowA = deferredFetch();
    const fastB = deferredFetch();
    vi.mocked(fetchIocValidation).mockImplementation(((id: string) => (id === 'request-a' ? slowA.promise : fastB.promise)) as never);
    const { result, rerender } = renderHook(({ id }) => useIocValidation(id), { initialProps: { id: 'request-a' } });
    act(() => {
      result.current.load();
    });

    rerender({ id: 'request-b' });
    expect(result.current.iocValidation).toBeNull();
    act(() => {
      result.current.load();
    });
    await act(async () => {
      fastB.resolve({ data: validation('request-b') });
      await fastB.promise;
    });
    await act(async () => {
      slowA.resolve({ data: validation('request-a') });
      await slowA.promise;
    });

    await waitFor(() => expect(result.current.iocValidation?.ioc_validation_id).toEqual('request-b'));
  });

  it('drops a decision result that belongs to a request no longer displayed', async () => {
    vi.mocked(fetchIocValidation).mockImplementation(((id: string) => Promise.resolve({ data: validation(id) })) as never);
    const { result, rerender } = renderHook(({ id }) => useIocValidation(id), { initialProps: { id: 'request-a' } });
    rerender({ id: 'request-b' });
    await act(async () => {
      await result.current.load();
    });

    act(() => {
      result.current.update(validation('request-a'));
    });

    expect(result.current.iocValidation?.ioc_validation_id).toEqual('request-b');
  });

  it('reports a missing request only for the request it was asked for', async () => {
    vi.mocked(fetchIocValidation).mockImplementation((() => Promise.reject(Object.assign(new Error('Not found'), { response: { status: 404 } }))) as never);
    const { result, rerender } = renderHook(({ id }) => useIocValidation(id), { initialProps: { id: 'request-a' } });
    await act(async () => {
      await result.current.load();
    });
    expect(result.current.loadError).toEqual('not_found');

    rerender({ id: 'request-b' });

    expect(result.current.loadError).toBeNull();
  });
});
