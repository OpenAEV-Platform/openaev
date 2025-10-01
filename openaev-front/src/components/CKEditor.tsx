import { CKEditor as ReactCKEditor } from '@ckeditor/ckeditor5-react';
import {
  Alignment,
  Autoformat,
  AutoImage,
  AutoLink,
  Base64UploadAdapter,
  BlockQuote,
  Bold,
  ClassicEditor,
  Code,
  CodeBlock,
  type Editor,
  type EditorConfig,
  Essentials,
  FontBackgroundColor,
  FontColor,
  FontFamily,
  FontSize,
  Heading,
  Highlight,
  HorizontalLine,
  Image,
  ImageBlockEditing,
  ImageCaption,
  ImageEditing,
  ImageInsert,
  ImageResize,
  ImageStyle,
  ImageTextAlternative,
  ImageToolbar,
  Indent,
  IndentBlock,
  Italic,
  Link,
  LinkImage,
  List,
  ListProperties,
  Mention,
  Paragraph,
  PasteFromOffice,
  RemoveFormat,
  SourceEditing,
  SpecialCharacters,
  SpecialCharactersCurrency,
  SpecialCharactersEssentials,
  Strikethrough,
  Subscript,
  Superscript,
  Table,
  TableCaption,
  TableColumnResize,
  TableToolbar,
  TodoList,
  Underline,
} from 'ckeditor5';
// eslint-disable-next-line @typescript-eslint/ban-ts-comment
// @ts-ignore
// eslint-disable-next-line import/extensions
import en from 'ckeditor5/translations/en.js';
// eslint-disable-next-line @typescript-eslint/ban-ts-comment
// @ts-ignore
// eslint-disable-next-line import/extensions
import fr from 'ckeditor5/translations/fr.js';
// eslint-disable-next-line @typescript-eslint/ban-ts-comment
// @ts-ignore
// eslint-disable-next-line import/extensions
import zh from 'ckeditor5/translations/zh.js';
import { useEffect } from 'react';
import { useIntl } from 'react-intl';

const CKEDITOR_DEFAULT_CONFIG: EditorConfig = {
  licenseKey: 'GPL',
  translations: [en, fr, zh],
  plugins: [
    Alignment,
    AutoImage,
    Autoformat,
    AutoLink,
    Base64UploadAdapter,
    BlockQuote,
    Bold,
    Code,
    CodeBlock,
    Essentials,
    FontBackgroundColor,
    FontColor,
    FontFamily,
    FontSize,
    Heading,
    Highlight,
    HorizontalLine,
    Image,
    ImageBlockEditing,
    ImageCaption,
    ImageEditing,
    ImageInsert,
    ImageResize,
    ImageStyle,
    ImageToolbar,
    ImageTextAlternative,
    Indent,
    IndentBlock,
    Italic,
    Link,
    LinkImage,
    List,
    ListProperties,
    Mention,
    Paragraph,
    PasteFromOffice,
    RemoveFormat,
    SourceEditing,
    SpecialCharacters,
    SpecialCharactersCurrency,
    SpecialCharactersEssentials,
    Strikethrough,
    Subscript,
    Superscript,
    Table,
    TableCaption,
    TableColumnResize,
    TableToolbar,
    TodoList,
    Underline,
  ],
  toolbar: {
    items: [
      'heading',
      'fontFamily',
      'fontSize',
      'alignment',
      '|',
      'bold',
      'italic',
      'underline',
      'strikethrough',
      'link',
      'fontColor',
      'fontBackgroundColor',
      'highlight',
      '|',
      'bulletedList',
      'numberedList',
      'outdent',
      'indent',
      'todoList',
      '|',
      'imageInsert',
      'blockQuote',
      'code',
      'codeBlock',
      'insertTable',
      'specialCharacters',
      'subscript',
      'superscript',
      'horizontalLine',
      '|',
      'sourceEditing',
      'removeFormat',
      'undo',
      'redo',
    ],
  },
  image: {
    resizeUnit: 'px',
    toolbar: [
      'imageTextAlternative',
      'toggleImageCaption',
      'imageStyle:alignLeft',
      'imageStyle:alignCenter',
      'imageStyle:alignRight',
      'imageStyle:alignBlockLeft',
      'imageStyle:alignBlockRight',
      'linkImage',
    ],
  },
  table: {
    contentToolbar: [
      'tableColumn',
      'tableRow',
      'mergeTableCells',
    ],
  },
};

type CKEditorProps<T extends Editor> = Omit<ReactCKEditor<T>['props'], 'editor' | 'config'>;

const CKEditor = (props: CKEditorProps<ClassicEditor> & { toolbarDropdownSize?: string }) => {
  const { locale } = useIntl();
  const { toolbarDropdownSize } = props;

  const config: EditorConfig = {
    ...CKEDITOR_DEFAULT_CONFIG,
    language: locale.slice(0, 2),
  };

  useEffect(() => {
    if (toolbarDropdownSize) {
      // @ts-expect-error Property style does not exist on type Element
      document?.querySelector(':root')?.style?.setProperty('--ck-toolbar-dropdown-max-width', toolbarDropdownSize);
    }
  }, [toolbarDropdownSize]);

  return (
    <ReactCKEditor
      editor={ClassicEditor}
      config={config}
      {...props}
    />
  );
};

export default CKEditor;
