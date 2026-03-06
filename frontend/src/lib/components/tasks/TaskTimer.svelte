<script lang="ts">
  import { onMount, onDestroy } from 'svelte';

  let { startTime = 0 } = $props();
  let seconds = $state(startTime);
  let interval: any;

  onMount(() => {
    interval = setInterval(() => {
      seconds++;
    }, 1000);
  });

  onDestroy(() => {
    clearInterval(interval);
  });

  export function getSeconds() {
    return seconds;
  }

  function formatTime(s: number) {
    const mins = Math.floor(s / 60);
    const secs = s % 60;
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  }
</script>

<div class="flex items-center gap-2 font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase">
  <svg xmlns="http://www.w3.org/2000/svg" class="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
  </svg>
  Bearbeitungszeit: {formatTime(seconds)}
</div>
