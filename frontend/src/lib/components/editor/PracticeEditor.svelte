<!-- frontend/src/lib/components/editor/PracticeEditor.svelte -->
<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import { Editor } from '@tiptap/core';
  import Document from '@tiptap/extension-document';
  import Paragraph from '@tiptap/extension-paragraph';
  import Text from '@tiptap/extension-text';
  import Heading from '@tiptap/extension-heading';
  import Bold from '@tiptap/extension-bold';
  import Italic from '@tiptap/extension-italic';
  import BulletList from '@tiptap/extension-bullet-list';
  import OrderedList from '@tiptap/extension-ordered-list';
  import ListItem from '@tiptap/extension-list-item';
  import Image from '@tiptap/extension-image';
  import Placeholder from '@tiptap/extension-placeholder';
  import HardBreak from '@tiptap/extension-hard-break';
  import History from '@tiptap/extension-history';
  import { CodeBlockLowlight } from '@tiptap/extension-code-block-lowlight';
  import { createLowlight, common } from 'lowlight';

  import EditorToolbar from './EditorToolbar.svelte';
  import './editor.css';

  let { 
    placeholder = 'Schreibe hier deine Lösung...', 
    disabled = false,
    initialContent = '',
    onchange
  } = $props<{ 
    placeholder?: string; 
    disabled?: boolean;
    initialContent?: string;
    onchange?: (html: string) => void;
  }>();

  const lowlight = createLowlight(common);

  let editorElement: HTMLDivElement;
  let editor = $state<Editor | null>(null);

  onMount(() => {
    editor = new Editor({
      element: editorElement,
      content: initialContent,
      extensions: [
        Document,
        Paragraph,
        Text,
        HardBreak,
        History,
        Heading.configure({ levels: [1, 2, 3] }),
        Bold,
        Italic,
        BulletList,
        OrderedList,
        ListItem,
        Image.configure({ inline: false, allowBase64: true }),
        Placeholder.configure({ placeholder }),
        CodeBlockLowlight.configure({ lowlight }),
      ],
      editable: !disabled,
      editorProps: {
        attributes: { class: 'tiptap-content p-4 focus:outline-none' },
      },
      onUpdate: ({ editor }) => {
        onchange?.(editor.getHTML());
      },
    });
  });

  onDestroy(() => editor?.destroy());

  // Reaktiv: disabled von außen änderbar
  $effect(() => {
    if (editor) editor.setEditable(!disabled);
  });

  // Methoden nach außen geben (via bindable oder einfach export)
  export function getHTML(): string {
    return editor?.getHTML() ?? '';
  }

  export function isEmpty(): boolean {
    return editor?.isEmpty ?? true;
  }
</script>

<div class="flex flex-col border border-slate-200 rounded-none
            {disabled ? 'opacity-60 pointer-events-none' : ''}">
  <EditorToolbar {editor} />
  <div bind:this={editorElement} class="min-h-[240px] bg-white"></div>
</div>
