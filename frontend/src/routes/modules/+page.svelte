<script lang="ts">
	import { onMount, onDestroy } from 'svelte';
	import { auth } from '$lib/auth.svelte';

	interface Module {
		slug: string;
		title: string;
		description: string;
		iconEmoji: string;
		isUnlocked: boolean;
		totalTasks: number;
		completedTasks: number;
	}

	let modules = $state<Module[]>([]);
	let loading = $state(true);
	let error = $state('');
	let pollInterval: any;

	async function fetchModules() {
		try {
			const res = await auth.apiFetch('/api/modules');
			if (!res.ok) throw new Error('Module konnten nicht geladen werden');
			modules = await res.json();
		} catch (err: any) {
			error = err.message;
		} finally {
			loading = false;
		}
	}

	onMount(() => {
		fetchModules();
		pollInterval = setInterval(fetchModules, 10000);
	});

	onDestroy(() => {
		if (pollInterval) clearInterval(pollInterval);
	});
</script>

<div class="mx-auto max-w-6xl">
	<header class="mb-16 border-b border-slate-200 pb-8">
		<h1 class="font-sans text-4xl font-extrabold tracking-tight text-institutional-navy uppercase">
			Modul-Übersicht
		</h1>
		<p class="mt-4 text-lg text-slate-500">
			Wählen Sie einen Fachbereich aus, um mit den Lerninhalten zu beginnen.
		</p>
	</header>

	{#if loading && modules.length === 0}
		<div class="flex justify-center py-24">
			<div class="h-12 w-12 border-4 border-slate-200 border-t-institutional-navy animate-spin"></div>
		</div>
	{:else if error && modules.length === 0}
		<div class="border border-red-200 bg-red-50 p-8 text-red-700 font-mono text-sm">
			<span class="font-bold">SYSTEM-FEHLER:</span> {error}
		</div>
	{:else}
		<div class="grid grid-cols-1 gap-10 md:grid-cols-2 lg:grid-cols-3">
			{#each modules as module (module.slug)}
				<a
					href={`/modules/${module.slug}`}
					class="group flex flex-col border border-slate-200 bg-white p-10 transition-all hover:border-institutional-navy shadow-sm rounded-none"
				>
					<div class="mb-8 text-5xl grayscale transition-all group-hover:grayscale-0">
						{module.iconEmoji}
					</div>

					<h2 class="mb-4 font-sans text-xl font-bold tracking-tight text-institutional-navy uppercase transition-colors group-hover:text-amber-700">
						{module.title}
					</h2>

					<p class="mb-10 text-sm leading-relaxed text-slate-500">
						{module.description}
					</p>

					<div class="mt-auto border-t border-slate-100 pt-8">
						<div class="mb-3 flex items-center justify-between">
							<span class="font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase">Fortschritt</span>
							<span class="font-mono text-[10px] font-black text-institutional-navy">
								{Math.round((module.completedTasks / module.totalTasks) * 100) || 0}%
							</span>
						</div>
						<div class="h-1 w-full bg-slate-100">
							<div
								class="h-full bg-institutional-navy transition-all duration-700"
								style="width: {(module.completedTasks / module.totalTasks) * 100 || 0}%"
							></div>
						</div>
						<p class="mt-4 font-mono text-[9px] font-bold tracking-widest text-slate-400 uppercase">
							{module.completedTasks} / {module.totalTasks} Aufgaben abgeschlossen
						</p>
					</div>
				</a>
			{/each}
		</div>
	{/if}
</div>
