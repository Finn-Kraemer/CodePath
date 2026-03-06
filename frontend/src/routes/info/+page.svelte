<script lang="ts">
  import { onMount } from 'svelte';
  import { auth } from '$lib/auth.svelte';

  let announcement = $state<{ content: string; updatedAt: string; displayMode: string } | null>(null);
  let loading = $state(true);

  onMount(async () => {
    try {
      const res = await auth.apiFetch('/api/common/announcement');
      if (res.ok) {
        const data = await res.json();
        if (data.content && (data.displayMode === 'INFO_PAGE' || data.displayMode === 'BOTH')) {
          announcement = data;
        }
      }
    } catch (e) {
      console.error(e);
    } finally {
      loading = false;
    }
  });

  function formatDate(dateStr: string) {
    return new Date(dateStr).toLocaleString('de-DE');
  }
</script>

<div class="max-w-4xl mx-auto py-12">
  <header class="mb-12 border-b border-slate-200 pb-8">
    <h1 class="font-sans text-4xl font-extrabold tracking-tight text-institutional-navy uppercase">
      Wichtige Kursinformationen
    </h1>
    <p class="mt-2 text-slate-500">Aktuelle Mitteilungen und Informationen der Lehrkräfte.</p>
  </header>

  {#if loading}
    <div class="flex justify-center py-24">
      <div class="h-12 w-12 border-4 border-slate-200 border-t-institutional-navy animate-spin"></div>
    </div>
  {:else if announcement}
    <article class="border border-slate-200 bg-white p-12 shadow-sm rounded-none">
      <div class="mb-8 flex items-center justify-between border-b border-slate-100 pb-6">
        <span class="font-mono text-[10px] font-bold tracking-[3px] text-slate-400 uppercase">Offizielle Mitteilung</span>
        <span class="font-mono text-[10px] font-bold text-slate-400">Aktualisiert am: {formatDate(announcement.updatedAt)}</span>
      </div>
      
      <div class="prose max-w-none">
        <p class="whitespace-pre-line font-sans text-lg leading-relaxed text-slate-700">
          {announcement.content}
        </p>
      </div>

      <div class="mt-12 flex items-center gap-4 text-institutional-navy/40">
        <div class="h-px flex-grow bg-slate-100"></div>
        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <div class="h-px flex-grow bg-slate-100"></div>
      </div>
    </article>
  {:else}
    <div class="border border-dashed border-slate-200 bg-slate-50 p-24 text-center rounded-none">
      <p class="font-serif text-sm text-slate-400 italic">Aktuell liegen keine speziellen Mitteilungen vor.</p>
    </div>
  {/if}
</div>
