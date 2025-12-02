import { type Page } from '../../components/common/queryable/Page';
import { simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';
import {
  type ExportMapperInput,
  type ImportMapper,
  type ImportMapperAddInput,
  type ImportMapperUpdateInput,
  type ImportPostSummary,
  type ImportTestSummary,
  type InjectsImportTestInput,
  type SearchPaginationInput,
} from '../../utils/api-types';

const XLS_MAPPER_URI = '/api/mappers';

export const searchMappers = (searchPaginationInput: Partial<SearchPaginationInput>) => {
  const data = searchPaginationInput;
  const uri = `${XLS_MAPPER_URI}/search`;
  return simplePostCall<Page<ImportMapper>>(uri, data);
};

export const fetchMapper = (mapperId: string) => {
  const uri = `${XLS_MAPPER_URI}/${mapperId}`;
  return simpleCall<ImportMapper>(uri);
};

export const deleteMapper = (mapperId: ImportMapper['import_mapper_id']) => {
  const uri = `${XLS_MAPPER_URI}/${mapperId}`;
  return simpleDelCall(uri);
};

export const createMapper = (data: ImportMapperAddInput) => {
  return simplePostCall<ImportMapper>(XLS_MAPPER_URI, data);
};

export const duplicateMapper = (mapperId: string) => {
  return simplePostCall<ImportMapper>(`${XLS_MAPPER_URI}/${mapperId}`, mapperId);
};

export const updateMapper = (mapperId: string, data: ImportMapperUpdateInput) => {
  const uri = `${XLS_MAPPER_URI}/${mapperId}`;
  return simplePutCall<ImportMapper>(uri, data);
};

export const storeXlsFile = (file: File) => {
  const uri = `${XLS_MAPPER_URI}/store`;
  const formData = new FormData();
  formData.append('file', file);
  return simplePostCall<ImportPostSummary>(uri, formData);
};

export const testXlsFile = (importId: string, input: InjectsImportTestInput) => {
  const uri = `${XLS_MAPPER_URI}/store/${importId}`;
  return simplePostCall<ImportTestSummary>(uri, input);
};

export const exportMapper = (input: ExportMapperInput) => {
  const uri = `${XLS_MAPPER_URI}/export`;
  return simplePostCall<string>(uri, input).then((response) => {
    return {
      data: response.data,
      filename: response.headers['content-disposition'].split('filename=')[1],
    };
  });
};

export const exportCsvMapper = (targetType: string, searchPaginationInput: SearchPaginationInput | undefined) => {
  const uri = `${XLS_MAPPER_URI}/export/csv?targetType=` + targetType;
  return simplePostCall(uri, searchPaginationInput).then((response) => {
    return {
      data: response.data,
      filename: response.headers['content-disposition'].split('filename=')[1],
    };
  });
};

export const importMapper = (formData: FormData) => {
  const uri = `${XLS_MAPPER_URI}/import`;
  return simplePostCall<void>(uri, formData);
};
