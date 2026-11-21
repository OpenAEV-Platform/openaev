import { act, renderHook, waitFor } from '@testing-library/react';
import { type ReactNode } from 'react';
import { MemoryRouter, useNavigate } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import useOpenedAction from '../../../../../admin/components/threat_arsenal/hook/useOpenedAction';
import { type ThreatArsenalAction } from '../../../../../utils/api-types';
import { anAction, intlWrapper } from '../threatArsenalTestUtils';

const { fetchAction, fetchActionOfPayload } = vi.hoisted(() => ({
  fetchAction: vi.fn(),
  fetchActionOfPayload: vi.fn(),
}));

vi.mock('../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({
  fetchThreatArsenalAction: fetchAction,
  fetchThreatArsenalActionOfPayload: fetchActionOfPayload,
}));

const action = anAction();

const renderAt = (path: string, actions: ThreatArsenalAction[]) => {
  const searchByName = vi.fn();
  const wrapper = ({ children }: { children: ReactNode }) => (
    <MemoryRouter initialEntries={[path]}>
      {intlWrapper({ children })}
    </MemoryRouter>
  );
  const hook = renderHook(({ listed }) => ({
    ...useOpenedAction(listed, searchByName),
    navigate: useNavigate(),
  }), {
    wrapper,
    initialProps: { listed: actions },
  });
  return {
    ...hook,
    searchByName,
  };
};

describe('useOpenedAction', () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  describe('given a link to an action', () => {
    it('should search the action by its name', async () => {
      // Arrange
      fetchAction.mockResolvedValue({ data: { action_labels: { en: 'Mimikatz' } } });

      // Act
      const { searchByName } = renderAt('/?action=action-v2', []);

      // Assert
      await waitFor(() => expect(searchByName).toHaveBeenCalledWith('Mimikatz'));
      expect(fetchAction).toHaveBeenCalledWith('action-v2');
    });

    it('should open the action already listed, on the linked tab', async () => {
      // Arrange
      fetchAction.mockResolvedValue({ data: { action_labels: { en: 'Mimikatz' } } });
      const { result } = renderAt('/', [action]);

      // Act
      act(() => {
        result.current.navigate('/?action=action-v2&tab=versions');
      });

      // Assert
      await waitFor(() => expect(result.current.openedAction).toBe(action));
      expect(result.current.openedTab).toBe('versions');
    });

    it('should open the action once the list shows it', async () => {
      // Arrange
      fetchAction.mockResolvedValue({ data: { action_labels: { en: 'Mimikatz' } } });
      const { result, rerender } = renderAt('/?action=action-v2', []);
      await waitFor(() => expect(fetchAction).toHaveBeenCalled());
      expect(result.current.openedAction).toBeNull();

      // Act
      rerender({ listed: [action] });

      // Assert
      await waitFor(() => expect(result.current.openedAction).toBe(action));
      expect(result.current.openedTab).toBeUndefined();
    });
  });

  describe('given a link to a payload version', () => {
    it('should open the action the list shows for it', async () => {
      // Arrange
      fetchActionOfPayload.mockResolvedValue({ data: { action_id: 'action-v2' } });
      fetchAction.mockResolvedValue({ data: { action_labels: { en: 'Mimikatz' } } });
      const { result, rerender, searchByName } = renderAt('/?payload=payload-v1', []);
      await waitFor(() => expect(searchByName).toHaveBeenCalledWith('Mimikatz'));

      // Act
      rerender({ listed: [action] });

      // Assert
      await waitFor(() => expect(result.current.openedAction).toBe(action));
      expect(fetchActionOfPayload).toHaveBeenCalledWith('payload-v1');
    });
  });

  describe('given no link', () => {
    it('should open, show the versions of, then close an action', () => {
      // Arrange
      const { result } = renderAt('/', [action]);
      expect(fetchAction).not.toHaveBeenCalled();

      // Act
      act(() => result.current.openAction(action));

      // Assert
      expect(result.current.openedAction).toBe(action);
      expect(result.current.openedTab).toBeUndefined();

      // Act
      act(() => result.current.showVersions(action));

      // Assert
      expect(result.current.openedTab).toBe('versions');

      // Act
      act(() => result.current.closeAction());

      // Assert
      expect(result.current.openedAction).toBeNull();
      expect(result.current.openedTab).toBeUndefined();
    });
  });
});
