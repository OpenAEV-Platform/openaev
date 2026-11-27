import { beforeEach, describe, expect, it, vi } from 'vitest';

const {
  mockSimplePostCall,
  mockNotifyErrorHandler,
} = vi.hoisted(() => ({
  mockSimplePostCall: vi.fn(),
  mockNotifyErrorHandler: vi.fn(),
}));

vi.mock('../../../utils/Action', () => ({
  simplePostCall: mockSimplePostCall,
  simpleCall: vi.fn(),
  delReferential: vi.fn(),
  postReferential: vi.fn(),
  putReferential: vi.fn(),
}));

vi.mock('../../../utils/error/errorHandlerUtil', () => ({ notifyErrorHandler: mockNotifyErrorHandler }));

import { exportMarkingDefinitions, importMarkingDefinitions } from '../../../actions/marking_definitions/marking-definition-actions';
import { ImportFileError } from '../../../components/common/import/import-report-types';
import { type SearchPaginationInput } from '../../../utils/api-types';

describe('marking definition export', () => {
  beforeEach(() => mockSimplePostCall.mockReset());

  it('posts the search input of the list and reads the filename from the content-disposition', async () => {
    // Arrange
    const input = {
      page: 0,
      size: 20,
      textSearch: 'amber',
    } as SearchPaginationInput;
    mockSimplePostCall.mockResolvedValue({
      data: 'type,definition,color,order\r\n',
      headers: { 'content-disposition': 'attachment; filename=MarkingDefinitions202611191200.csv' },
    });

    // Act
    const result = await exportMarkingDefinitions(input);

    // Assert
    expect(mockSimplePostCall).toHaveBeenCalledWith('/api/marking_definitions/export/csv', input);
    expect(result).toEqual({
      data: 'type,definition,color,order\r\n',
      filename: 'MarkingDefinitions202611191200.csv',
    });
  });
});

describe('marking definition import', () => {
  beforeEach(() => {
    mockSimplePostCall.mockReset();
    mockNotifyErrorHandler.mockReset();
  });

  it('posts the file as multipart form data, without the generic error notification, and returns the report', async () => {
    // Arrange
    const report = {
      import_created: 1,
      import_skipped: 0,
      import_ignored: 0,
      import_columns: [],
      import_rows: [],
    };
    mockSimplePostCall.mockResolvedValue({ data: report });
    const file = new File(['type,definition,color,order'], 'markings.csv', { type: 'text/csv' });

    // Act
    const result = await importMarkingDefinitions(file);

    // Assert
    const [uri, body, , notify] = mockSimplePostCall.mock.calls[0];
    expect(uri).toBe('/api/marking_definitions/import/csv');
    expect((body as FormData).get('file')).toBe(file);
    expect(notify).toBe(false);
    expect(result).toEqual(report);
  });

  it('turns a 400 into an ImportFileError carrying the server message, with no generic notification', async () => {
    // Arrange
    mockSimplePostCall.mockRejectedValue({
      status: 400,
      message: 'The file is empty',
    });

    // Act / Assert
    await expect(importMarkingDefinitions(new File([''], 'markings.csv'))).rejects.toThrow(new ImportFileError('The file is empty'));
    expect(mockNotifyErrorHandler).not.toHaveBeenCalled();
  });

  it('notifies and rethrows any other failure', async () => {
    // Arrange
    const error = {
      status: 403,
      message: 'Forbidden',
    };
    mockSimplePostCall.mockRejectedValue(error);

    // Act / Assert
    await expect(importMarkingDefinitions(new File([''], 'markings.csv'))).rejects.toBe(error);
    expect(mockNotifyErrorHandler).toHaveBeenCalledWith(error);
  });
});
