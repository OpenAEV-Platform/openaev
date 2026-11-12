import { type AxiosResponse } from 'axios';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { fetchXtmHubRegistration } from '../../../actions/xtmhub/xtmhub-actions';
import * as Constants from '../../../constants/ActionTypes';
import { simpleCall } from '../../../utils/Action';

vi.mock('../../../utils/Action', () => ({
  simpleCall: vi.fn(),
  simplePostCall: vi.fn(),
  simplePutCall: vi.fn(),
  putReferential: vi.fn(),
}));
vi.mock('../../../store', () => ({ store: { getState: () => ({ referential: { getIn: () => undefined } }) } }));

describe('xtmhub-actions', () => {
  const dispatch = vi.fn();

  beforeEach(() => {
    dispatch.mockClear();
  });

  describe('fetchXtmHubRegistration', () => {
    it('given a registered tenant should resolve true and store the registration', async () => {
      // Arrange
      vi.mocked(simpleCall).mockResolvedValue({
        status: 200,
        data: { tenant_xtmhub_registration_id: 'registration-1' },
      } as AxiosResponse);

      // Act
      const known = await fetchXtmHubRegistration()(dispatch);

      // Assert
      expect(known).toBe(true);
      expect(dispatch).toHaveBeenCalledWith(expect.objectContaining({ type: Constants.DATA_FETCH_SUCCESS }));
    });

    it('given a tenant without registration (204) should resolve true', async () => {
      // Arrange
      vi.mocked(simpleCall).mockResolvedValue({
        status: 204,
        data: undefined,
      } as AxiosResponse);

      // Act
      const known = await fetchXtmHubRegistration()(dispatch);

      // Assert
      expect(known).toBe(true);
    });

    it('given a failed lookup should resolve false so the registration is not mistaken for absent', async () => {
      // Arrange
      vi.mocked(simpleCall).mockRejectedValue({ status: 500 });

      // Act
      const known = await fetchXtmHubRegistration()(dispatch);

      // Assert
      expect(known).toBe(false);
      expect(dispatch).toHaveBeenCalledWith(expect.objectContaining({ type: Constants.DATA_FETCH_ERROR }));
    });
  });
});
