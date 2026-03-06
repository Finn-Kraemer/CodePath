<script lang="ts">
  import { onMount } from 'svelte';
  import { auth } from '$lib/auth.svelte';

  let content = $state('');
  let status = $state<{ enabled: boolean; alreadySubmitted: boolean } | null>(null);
  let loading = $state(true);
  let submitting = $state(false);
  let error = $state('');
  let success = $state(false);

  async function fetchStatus() {
    try {
      const res = await auth.apiFetch('/api/feedback/status');
      if (res.ok) {
        status = await res.json();
      }
    } catch (e) {
      console.error(e);
    } finally {
      loading = false;
    }
  }

  async function handleSubmit() {
    if (!content.trim()) return;
    
    const confirmed = confirm('Sind Sie sicher? Nach dem Absenden können Sie Ihr Feedback nicht mehr ändern.');
    if (!confirmed) return;

    submitting = true;
    error = '';
    try {
      const res = await auth.apiFetch('/api/feedback', {
        method: 'POST',
        body: JSON.stringify({ content })
      });
      if (res.ok) {
        success = true;
        status!.alreadySubmitted = true;
      } else {
        error = 'Fehler beim Senden des Feedbacks.';
      }
    } catch (e) {
      error = 'Netzwerkfehler.';
    } finally {
      submitting = false;
    }
  }

  onMount(fetchStatus);
</script>

<div class="max-w-3xl mx-auto py-12">
  <header class="mb-12 border-b border-slate-200 pb-8 text-center md:text-left">
    <h1 class="font-sans text-4xl font-extrabold tracking-tight text-institutional-navy uppercase">
      Kurs-Feedback
    </h1>
    <p class="mt-2 text-slate-500">Ihre Meinung hilft uns, den Kurs stetig zu verbessern.</p>
  </header>

  {#if loading}
    <div class="flex justify-center py-24">
      <div class="h-12 w-12 border-4 border-slate-200 border-t-institutional-navy animate-spin"></div>
    </div>
  {:else if !status?.enabled}
    <div class="border border-dashed border-slate-200 bg-slate-50 p-24 text-center rounded-none shadow-sm">
      <svg xmlns="http://www.w3.org/2000/svg" class="mx-auto h-12 w-12 text-slate-300 mb-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m0 0v2m0-2h2m-2 0H10m11 3a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
      <p class="font-serif text-lg text-slate-400 italic">Die Feedback-Option ist zurzeit deaktiviert.</p>
      <p class="mt-2 text-xs font-mono text-slate-400 uppercase tracking-widest">Bitte versuchen Sie es später erneut.</p>
    </div>
  {:else if status?.alreadySubmitted}
    <div class="border border-green-200 bg-green-50 p-16 text-center rounded-none shadow-sm">
      <div class="mx-auto h-16 w-16 bg-green-100 flex items-center justify-center text-green-600 mb-6 border border-green-200 shadow-inner">
        <svg xmlns="http://www.w3.org/2000/svg" class="h-10 w-10" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
        </svg>
      </div>
      <h2 class="font-sans text-2xl font-black text-green-800 uppercase tracking-tight mb-2">Vielen Dank!</h2>
      <p class="font-sans text-sm text-green-700 leading-relaxed uppercase tracking-widest font-bold">Ihre Rückmeldung wurde erfolgreich erfasst.</p>
      <p class="mt-8 text-[10px] font-mono text-green-600/50 uppercase tracking-[3px]">Feedback-Sperre aktiv: Nur eine Einreichung möglich.</p>
    </div>
  {:else}
    <div class="border border-slate-200 bg-white p-10 shadow-sm rounded-none">
      <div class="mb-8 border-l-4 border-institutional-gold bg-slate-50 p-6 font-sans text-xs italic text-slate-600 leading-relaxed uppercase tracking-widest">
        Ihre Antwort wird Ihrem Profil zugeordnet, aber wir schätzen ehrliche und konstruktive Kritik sehr.
      </div>

      <div class="space-y-6">
        <label for="feedback" class="block font-mono text-[10px] font-bold tracking-[3px] text-slate-400 uppercase">Ihre Nachricht an uns</label>
        <textarea
          id="feedback"
          bind:value={content}
          placeholder="Was hat Ihnen gefallen? Was können wir besser machen?"
          class="w-full border border-slate-200 p-6 font-sans text-sm focus:border-institutional-navy focus:outline-none rounded-none shadow-inner min-h-[300px]"
          disabled={submitting}
        ></textarea>

        {#if error}
          <p class="text-[10px] font-bold text-red-600 uppercase tracking-widest">{error}</p>
        {/if}

        <button
          onclick={handleSubmit}
          disabled={submitting || !content.trim()}
          class="w-full bg-institutional-navy py-6 font-sans text-[11px] font-bold tracking-[4px] text-white uppercase shadow-sm transition-all hover:opacity-90 disabled:opacity-20 rounded-none"
        >
          {submitting ? 'Übermittlung läuft...' : 'Feedback jetzt verbindlich abschicken'}
        </button>
      </div>
    </div>
  {/if}
</div>
