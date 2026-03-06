<!-- frontend/src/routes/modules/[slug]/[taskSlug]/PracticeTask.svelte -->
<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import { auth } from '$lib/auth.svelte';
  import PracticeEditor from '$lib/components/editor/PracticeEditor.svelte';
  import TaskTimer from '$lib/components/tasks/TaskTimer.svelte';

  let { task, moduleSlug } = $props();

  let editorRef = $state<PracticeEditor | null>(null);
  let timerRef = $state<TaskTimer | null>(null);

  // Initialisiere result basierend auf dem vom Backend gelieferten submissionStatus
  let result = $state(null);
  
  $effect(() => {
    if (task.submissionStatus && task.submissionStatus !== 'NOT_SUBMITTED') {
      result = { status: task.submissionStatus, feedback: '', adminComment: task.adminComment };
    }
  });

  let loading = $state(false);
  let error = $state('');
  let pollInterval: any = null;

  async function handleSubmit() {
    if (!editorRef || editorRef.isEmpty()) {
      error = 'Bitte schreibe erst eine Lösung.';
      return;
    }

    loading = true;
    error = '';
    try {
      const editorContent = editorRef.getHTML();
      const currentSeconds = timerRef ? timerRef.getSeconds() : (task.timeSpentSeconds || 0);
      const timeSpentSinceLastSubmit = currentSeconds - (task.timeSpentSeconds || 0);

      const res = await auth.apiFetch(`/api/modules/${moduleSlug}/${task.slug}/submit`, {
        method: 'POST',
        body: JSON.stringify({ 
          payload: { 
            practiceContent: editorContent 
          },
          timeSpentSeconds: timeSpentSinceLastSubmit
        })
      });
      
      const data = await res.json();
      if (!res.ok) throw new Error(data.message || 'Fehler beim Einreichen.');
      
      result = { status: data.status, feedback: data.feedback };
      task.timeSpentSeconds = currentSeconds; // Update local state
      startPolling();
    } catch (e: any) {
      error = e.message || 'Ein technischer Fehler ist aufgetreten.';
    } finally {
      loading = false;
    }
  }

  async function checkStatus() {
    try {
      const res = await auth.apiFetch(`/api/modules/${moduleSlug}/${task.slug}`);
      if (!res.ok) return;
      
      const data = await res.json();
      if (data.isCompleted) {
        task.isCompleted = true;
        result = {
          status: 'APPROVED',
          feedback: 'Aufgabe wurde von der Lehrkraft freigegeben! Gutschrift: +' + task.points + ' Punkte'
        };
        stopPolling();
      } else if (data.submissionStatus === 'NOT_SUBMITTED' || data.submissionStatus === 'REJECTED') {
        result = { status: data.submissionStatus, feedback: '', adminComment: data.adminComment };
        stopPolling();
      }
    } catch (e) {
      console.error('Polling error', e);
    }
  }

  function startPolling() {
    if (pollInterval) return;
    pollInterval = setInterval(checkStatus, 10000);
  }

  function stopPolling() {
    if (pollInterval) {
      clearInterval(pollInterval);
      pollInterval = null;
    }
  }

  onMount(() => {
    // Wenn bereits ausstehend, Polling starten
    if (!task.isCompleted && result?.status === 'PENDING') {
      startPolling();
    }
  });

  onDestroy(() => stopPolling());
</script>

<div class="border border-slate-200 bg-white p-10 shadow-sm rounded-none">
  <div class="mb-10 flex flex-col md:flex-row md:items-center md:justify-between gap-6">
    <h3 class="font-sans text-xl font-bold tracking-tight text-institutional-navy uppercase">
      Praktischer Leistungsnachweis:
    </h3>
    <TaskTimer bind:this={timerRef} startTime={task.timeSpentSeconds || 0} />
  </div>

  <div class="mb-10 border-l-4 border-amber-400 bg-amber-50 p-8 font-sans text-sm leading-relaxed text-amber-900 rounded-none shadow-inner">
    <p class="mb-2 font-black uppercase tracking-[2px] text-[10px] text-amber-600">Instruktionen</p>
    {task.config.instructions || 'Bearbeiten Sie die Aufgabe gemäß den Anweisungen und reichen Sie Ihre Lösung zur manuellen Prüfung ein.'}
  </div>

  {#if task.isCompleted}
    <div class="mb-8 border border-green-200 bg-green-50 p-8 flex items-center gap-6 font-sans font-bold text-green-700 uppercase tracking-widest text-xs rounded-none">
      <svg xmlns="http://www.w3.org/2000/svg" class="h-10 w-10" viewBox="0 0 20 20" fill="currentColor">
        <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd" />
      </svg>
      <div>
        <p class="block">Prüfung bestanden</p>
        <p class="mt-1 text-[10px] font-normal opacity-70 italic tracking-normal">Gutschrift: +{task.points} Punkte</p>
      </div>
    </div>
  {:else if loading}
    <div class="mb-8 border border-blue-200 bg-blue-50 p-8 flex items-center gap-6 font-sans font-bold text-blue-700 uppercase tracking-widest text-xs rounded-none shadow-sm">
      <div class="h-6 w-6 border-2 border-blue-700 border-t-transparent animate-spin"></div>
      Übermittlung Ihrer Ausarbeitung läuft...
    </div>
  {:else if result?.status === 'PENDING'}
    <div class="mb-8 border border-blue-200 bg-blue-50 p-8 flex items-center gap-6 font-sans font-bold text-blue-700 uppercase tracking-widest text-xs rounded-none shadow-sm">
      <div class="h-6 w-6 border-2 border-blue-700 border-t-transparent animate-spin"></div>
      In Prüfung durch Fachdozent...
    </div>
  {:else if result?.status === 'REJECTED'}
    <div class="mb-8 border border-red-200 bg-red-50 p-8 flex flex-col gap-4 font-sans text-red-700 rounded-none shadow-sm">
      <div class="flex items-center gap-6 font-bold uppercase tracking-widest text-xs">
        <svg xmlns="http://www.w3.org/2000/svg" class="h-10 w-10" viewBox="0 0 20 20" fill="currentColor">
          <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd" />
        </svg>
        Prüfung nicht bestanden: Korrektur erforderlich
      </div>
      {#if result.adminComment}
        <div class="mt-4 border-l-2 border-red-200 pl-6 py-2">
          <p class="text-[9px] font-black uppercase tracking-widest opacity-60 mb-2">Feedback der Lehrkraft:</p>
          <p class="font-sans text-sm italic leading-relaxed">{result.adminComment}</p>
        </div>
      {/if}
      <button
        onclick={() => result = null}
        class="mt-4 self-start bg-red-700 px-6 py-3 font-mono text-[10px] font-bold tracking-widest text-white uppercase transition-all hover:bg-red-800 rounded-none"
      >
        Lösung überarbeiten
      </button>
    </div>
  {:else}
    <div class="space-y-6">
      <p class="font-mono text-[9px] font-bold tracking-[3px] text-slate-400 uppercase">Deine Ausarbeitung</p>
      
      <PracticeEditor 
        bind:this={editorRef}
        placeholder="Schreiben Sie hier Ihre Lösung. Sie können Text formatieren, Code einbetten und Bilder hochladen..."
      />

      {#if error}
        <p class="text-xs font-bold text-red-600 uppercase tracking-tight">{error}</p>
      {/if}

      <button
        onclick={handleSubmit}
        class="w-full bg-institutional-navy py-6 font-sans text-[11px] font-bold tracking-[4px] text-white uppercase shadow-sm transition-all hover:opacity-90 rounded-none"
      >
        Lösung jetzt zur Prüfung einreichen
      </button>
    </div>
  {/if}
</div>
