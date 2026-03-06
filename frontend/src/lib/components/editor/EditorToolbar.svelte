<!-- frontend/src/lib/components/editor/EditorToolbar.svelte -->
<script lang="ts">
  import type { Editor } from '@tiptap/core';

  let { editor } = $props<{ editor: Editor | null }>();

  function uploadImage() {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = 'image/*';
    input.onchange = async () => {
      const file = input.files?.[0];
      if (!file || !editor) return;

      // Bild als Base64 in den Editor einfügen
      const reader = new FileReader();
      reader.onload = (e) => {
        const src = e.target?.result as string;
        editor!.chain().focus().setImage({ src }).run();
      };
      reader.readAsDataURL(file);
    };
    input.click();
  }

  type ToolButton = {
    label: string;
    icon: string;
    action: () => void;
    isActive?: () => boolean;
  };

  let buttons: ToolButton[] = $derived(editor
    ? [
        {
          label: 'Fett',
          icon: 'B',
          action: () => editor!.chain().focus().toggleBold().run(),
          isActive: () => editor!.isActive('bold'),
        },
        {
          label: 'Kursiv',
          icon: 'I',
          action: () => editor!.chain().focus().toggleItalic().run(),
          isActive: () => editor!.isActive('italic'),
        },
        {
          label: 'H1',
          icon: 'H1',
          action: () => editor!.chain().focus().toggleHeading({ level: 1 }).run(),
          isActive: () => editor!.isActive('heading', { level: 1 }),
        },
        {
          label: 'H2',
          icon: 'H2',
          action: () => editor!.chain().focus().toggleHeading({ level: 2 }).run(),
          isActive: () => editor!.isActive('heading', { level: 2 }),
        },
        {
          label: 'Liste',
          icon: '≡',
          action: () => editor!.chain().focus().toggleBulletList().run(),
          isActive: () => editor!.isActive('bulletList'),
        },
        {
          label: 'Nummeriert',
          icon: '1.',
          action: () => editor!.chain().focus().toggleOrderedList().run(),
          isActive: () => editor!.isActive('orderedList'),
        },
        {
          label: 'Code-Block',
          icon: '</>',
          action: () => editor!.chain().focus().toggleCodeBlock().run(),
          isActive: () => editor!.isActive('codeBlock'),
        },
      ]
    : []);
</script>

<div class="flex flex-wrap items-center gap-1 border-b border-slate-200 bg-slate-50 p-2">
  {#each buttons as btn}
    <button
      type="button"
      onclick={btn.action}
      class="flex h-8 min-w-[2rem] items-center justify-center px-2 font-mono text-xs font-bold
             transition-colors rounded-none border
             {btn.isActive?.()
               ? 'border-teal-700 bg-teal-700 text-white'
               : 'border-slate-200 bg-white text-slate-600 hover:border-slate-400 hover:text-slate-900'}"
      title={btn.label}
    >
      {btn.icon}
    </button>
  {/each}

  <!-- Trennlinie -->
  <div class="mx-1 h-6 w-px bg-slate-200"></div>

  <!-- Bild hochladen -->
  <button
    type="button"
    onclick={uploadImage}
    class="flex h-8 items-center gap-1.5 border border-slate-200 bg-white px-3
           font-mono text-xs font-bold text-slate-600 transition-colors rounded-none
           hover:border-slate-400 hover:text-slate-900"
    title="Bild einfügen"
  >
    <svg class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
      <path stroke-linecap="round" stroke-linejoin="round"
        d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14
           m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
    </svg>
    Bild
  </button>

  <!-- Undo / Redo -->
  <div class="mx-1 h-6 w-px bg-slate-200"></div>
  <button
    type="button"
    onclick={() => editor?.chain().focus().undo().run()}
    disabled={!editor?.can().undo()}
    class="flex h-8 w-8 items-center justify-center border border-slate-200 bg-white
           font-mono text-xs text-slate-600 transition-colors rounded-none
           hover:border-slate-400 disabled:opacity-30"
    title="Rückgängig"
  >↩</button>
  <button
    type="button"
    onclick={() => editor?.chain().focus().redo().run()}
    disabled={!editor?.can().redo()}
    class="flex h-8 w-8 items-center justify-center border border-slate-200 bg-white
           font-mono text-xs text-slate-600 transition-colors rounded-none
           hover:border-slate-400 disabled:opacity-30"
    title="Wiederholen"
  >↪</button>
</div>
