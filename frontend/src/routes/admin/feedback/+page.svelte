<script lang="ts">
  import { onMount } from 'svelte';
  import { auth } from '$lib/auth.svelte';

  interface Feedback {
    id: number;
    username: string;
    displayName: string;
    content: string;
    submittedAt: string;
  }

  let feedbacks = $state<Feedback[]>([]);
  let enabled = $state(false);
  let loading = $state(true);
  let toggling = $state(false);

  async function fetchData() {
    try {
      const [fRes, sRes] = await Promise.all([
        auth.apiFetch('/api/admin/feedback'),
        auth.apiFetch('/api/admin/feedback/status')
      ]);
      if (fRes.ok) feedbacks = await fRes.json();
      if (sRes.ok) {
        const sData = await sRes.json();
        enabled = sData.enabled;
      }
    } catch (e) {
      console.error(e);
    } finally {
      loading = false;
    }
  }

  async function toggleStatus() {
    toggling = true;
    try {
      const res = await auth.apiFetch('/api/admin/feedback/toggle', { method: 'PUT' });
      if (res.ok) {
        const data = await res.json();
        enabled = data.enabled;
      }
    } catch (e) {
      console.error(e);
    } finally {
      toggling = false;
    }
  }

  onMount(fetchData);

  function formatDate(dateStr: string) {
    return new Date(dateStr).toLocaleString('de-DE');
  }
</script>

<div class="flex flex-col gap-12 pb-24">
  <header class="flex flex-col items-start justify-between gap-6 border-b border-slate-200 pb-8 md:flex-row md:items-center">
    <div>
      <a href="/admin" class="font-mono text-[9px] font-bold tracking-[4px] text-slate-400 uppercase hover:text-institutional-navy">← Administration</a>
      <h1 class="mt-4 font-sans text-4xl font-extrabold tracking-tight text-institutional-navy uppercase">
        Feedback-Zentrum
      </h1>
      <p class="mt-2 text-slate-500">Einsehen und Steuern der globalen Feedback-Option.</p>
    </div>

    <div class="flex items-center gap-6 bg-slate-50 p-6 border border-slate-200 shadow-sm">
      <div class="flex flex-col">
        <span class="font-mono text-[9px] font-bold tracking-widest text-slate-400 uppercase">Status der Feedback-Runde</span>
        <span class="font-sans text-xs font-black uppercase {enabled ? 'text-teal-600' : 'text-slate-400'}">
          {enabled ? 'AKTIV: Schüler können schreiben' : 'INAKTIV: Gesperrt'}
        </span>
      </div>
      <button
        onclick={toggleStatus}
        disabled={toggling}
        class="px-6 py-3 font-mono text-[10px] font-bold tracking-widest uppercase transition-all shadow-sm rounded-none
        {enabled ? 'bg-rose-600 text-white hover:bg-rose-700' : 'bg-teal-600 text-white hover:bg-teal-700'} disabled:opacity-50"
      >
        {toggling ? '...' : enabled ? 'STOPPEN' : 'STARTEN'}
      </button>
    </div>
  </header>

  {#if loading}
    <div class="flex justify-center py-24">
      <div class="h-12 w-12 border-4 border-slate-200 border-t-institutional-navy animate-spin"></div>
    </div>
  {:else}
    <div class="grid grid-cols-1 gap-8 md:grid-cols-2 lg:grid-cols-3">
      {#each feedbacks as f (f.id)}
        <div class="border border-slate-200 bg-white p-8 shadow-sm transition-all hover:shadow-md rounded-none flex flex-col h-full">
          <div class="mb-6 flex items-center justify-between border-b border-slate-50 pb-4">
            <div class="flex flex-col">
              <span class="font-sans text-sm font-bold text-institutional-navy">{f.displayName || f.username}</span>
              <span class="font-mono text-[9px] text-slate-400 uppercase tracking-widest">@{f.username}</span>
            </div>
            <span class="font-mono text-[9px] text-slate-400">{formatDate(f.submittedAt)}</span>
          </div>
          <div class="prose prose-sm flex-grow">
            <p class="whitespace-pre-line font-serif italic text-slate-600 leading-relaxed">"{f.content}"</p>
          </div>
        </div>
      {:else}
        <div class="col-span-full border border-dashed border-slate-200 bg-slate-50 p-24 text-center">
          <p class="font-mono text-sm text-slate-400 italic">Noch keine Rückmeldungen vorhanden.</p>
        </div>
      {/each}
    </div>
  {/if}
</div>
