import { type CKEditor as ReactCKEditorType } from '@ckeditor/ckeditor5-react';
import { type ClassicEditor, type Editor, type EditorConfig } from 'ckeditor5';
import { lazy, Suspense, useEffect } from 'react';
import { useIntl } from 'react-intl';

import Loader from './Loader';

type CKEditorProps<T extends Editor> = Omit<ReactCKEditorType<T>['props'], 'editor' | 'config'>;

// Lazy load the CKEditor component and all dependencies
const LazyCKEditorComponent = lazy(async () => {
  const [
    { CKEditor: ReactCKEditor },
    {
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
    },
    { defautl: en },
    { defautl: fr },
    { defautl: zh },
  ] = await Promise.all([
    import('@ckeditor/ckeditor5-react'),
    import('ckeditor5'),
    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-ignore
    // eslint-disable-next-line import/extensions
    import('ckeditor5/translations/en.js'),
    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-ignore
    // eslint-disable-next-line import/extensions
    import('ckeditor5/translations/fr.js'),
    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-ignore
    // eslint-disable-next-line import/extensions
    import('ckeditor5/translations/zh.js'),
  ]);

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

  const InnerCKEditor = (props: CKEditorProps<ClassicEditor> & { toolbarDropdownSize?: string }) => {
    const { locale } = useIntl();
    const { toolbarDropdownSize, ...restProps } = props;

    const config: EditorConfig = {
      ...CKEDITOR_DEFAULT_CONFIG,
      language: locale.slice(0, 2),
      link: { defaultProtocol: 'https://' },
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
        {...restProps}
      />
    );
  };

  return { default: InnerCKEditor };
});

const CKEditor = (props: CKEditorProps<ClassicEditor> & { toolbarDropdownSize?: string }) => {
  return (
    <Suspense fallback={<Loader variant="inElement" />}>
      <LazyCKEditorComponent {...props} />
    </Suspense>
  );
};

export default CKEditor;
