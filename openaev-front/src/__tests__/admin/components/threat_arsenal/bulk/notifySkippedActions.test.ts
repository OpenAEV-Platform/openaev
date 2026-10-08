import { type AxiosResponse } from 'axios';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import notifySkippedActions, { skippedActionsCount } from '../../../../../admin/components/threat_arsenal/bulk/notifySkippedActions';
import { type Translate } from '../../../../../components/i18n';
import type * as EnvironmentModule from '../../../../../utils/Environment';

const { mockNotifySuccess } = vi.hoisted(() => ({ mockNotifySuccess: vi.fn() }));

vi.mock('../../../../../utils/Environment', async (importOriginal) => {
  const original = await importOriginal<typeof EnvironmentModule>();
  return {
    ...original,
    MESSAGING$: {
      ...original.MESSAGING$,
      notifySuccess: mockNotifySuccess,
    },
  };
});

const responseWith = (headers: Record<string, string>) => ({ headers } as unknown as AxiosResponse);
const t = ((key: string, values?: Record<string, string>) => `${key}|${values?.count}`) as Translate;

describe('notifySkippedActions', () => {
  beforeEach(() => {
    mockNotifySuccess.mockClear();
  });

  it('reads the skipped actions count from the response header', () => {
    expect(skippedActionsCount(responseWith({ 'x-openaev-skipped-actions': '3' }))).toBe(3);
    expect(skippedActionsCount(responseWith({}))).toBe(0);
    expect(skippedActionsCount(responseWith({ 'x-openaev-skipped-actions': 'abc' }))).toBe(0);
  });

  it('tells the user how many selected actions were skipped', () => {
    notifySkippedActions(responseWith({ 'x-openaev-skipped-actions': '2' }), t);

    expect(mockNotifySuccess).toHaveBeenCalledTimes(1);
    expect(mockNotifySuccess.mock.calls[0][0]).toContain('|2');
  });

  it('says nothing when no action was skipped', () => {
    notifySkippedActions(responseWith({ 'x-openaev-skipped-actions': '0' }), t);

    expect(mockNotifySuccess).not.toHaveBeenCalled();
  });
});
