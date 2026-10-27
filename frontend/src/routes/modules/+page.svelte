<script lang="ts">
	import { onMount } from 'svelte';
	import { api, type ModuleSummary } from '$lib/api';

	let modules = $state<ModuleSummary[]>([]);
	let loading = $state(true);
	let now = $state(new Date());

	// Update now every second for countdowns
	onMount(() => {
		fetchModules();
		const interval = setInterval(() => (now = new Date()), 1000);
		return () => clearInterval(interval);
	});

	async function fetchModules() {
		try {
			modules = await api<ModuleSummary[]>('/api/modules');
		} catch (e) {
			console.error(e);
		} finally {
			loading = false;
		}
	}

	function getTimeLeft(deadline?: string) {
		if (!deadline) return '';
		const diff = new Date(deadline).getTime() - now.getTime();
		if (diff <= 0) return 'Abgelaufen';

		const h = Math.floor(diff / 3600000);
		const m = Math.floor((diff % 3600000) / 60000);
		const s = Math.floor((diff % 60000) / 1000);

		return `${h}h ${m}m ${s}s`;
	}

	function isExpired(deadline?: string) {
		if (!deadline) return false;
		return new Date(deadline).getTime() <= now.getTime();
	}
</script>

<div class="flex flex-col gap-12">
	<header class="border-b border-slate-200 pb-8">
		<h1 class="font-sans text-4xl font-extrabold tracking-tight text-institutional-navy uppercase">
			Lernmodule
		</h1>
		<p class="mt-4 text-slate-500">Wähle ein Modul aus, um mit den Aufgaben zu beginnen.</p>
	</header>

	{#if loading}
		<div class="flex justify-center py-24">
			<div
				class="h-12 w-12 animate-spin border-4 border-slate-200 border-t-institutional-navy"
			></div>
		</div>
	{:else if modules.length === 0}
		<div class="rounded-none border border-dashed border-slate-200 bg-white p-24 text-center">
			<p class="font-serif text-sm text-slate-400 italic">
				Aktuell sind keine Module zur Bearbeitung freigeschaltet.
			</p>
		</div>
	{:else}
		<div class="grid grid-cols-1 gap-8 md:grid-cols-2 lg:grid-cols-3">
			{#each modules as mod (mod.slug)}
				<div
					class="relative flex flex-col rounded-none border border-slate-200 bg-white shadow-sm transition-all
                    {isExpired(mod.availableUntil)
						? 'opacity-80 grayscale'
						: 'hover:border-institutional-navy hover:shadow-md'}"
				>
					{#if isExpired(mod.availableUntil)}
						<div class="pointer-events-none absolute inset-x-0 top-4 z-10 flex justify-center">
							<div
								class="-rotate-3 border-2 border-white bg-rose-600 px-4 py-2 font-mono text-[10px] font-black tracking-[3px] text-white uppercase shadow-xl"
							>
								Bearbeitungszeit Abgelaufen
							</div>
						</div>
					{/if}

					<div class="flex-grow p-8">
						<div class="mb-6 flex items-center justify-between">
							<span
								class="text-4xl grayscale transition-all {isExpired(mod.availableUntil)
									? ''
									: 'hover:grayscale-0'}">{mod.icon}</span
							>
							{#if mod.completedTasks === mod.totalTasks && mod.totalTasks > 0}
								<span
									class="rounded-none bg-green-100 px-3 py-1 font-mono text-[9px] font-black tracking-widest text-green-700 uppercase"
									>Abgeschlossen</span
								>
							{/if}
						</div>
						<h3
							class="mb-3 font-sans text-xl font-bold tracking-tight text-institutional-navy uppercase"
						>
							{mod.title}
						</h3>
						<p class="text-sm leading-relaxed text-slate-500">
							{mod.description}
						</p>
					</div>

					<!-- Progress & Action -->
					<div
						class="flex items-center justify-between border-t border-slate-100 bg-slate-50/50 p-8"
					>
						<div class="flex flex-col gap-4">
							<div class="flex items-center gap-4">
								<div class="h-1.5 w-32 overflow-hidden rounded-none bg-slate-200">
									<div
										class="h-full bg-institutional-navy transition-all duration-1000"
										style="width: {((mod.completedTasks ?? 0) / (mod.totalTasks || 1)) * 100}%"
									></div>
								</div>
								<span
									class="font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase"
								>
									{mod.completedTasks}/{mod.totalTasks} · {mod.pointsEarned}/{mod.maxPoints} P.
								</span>
							</div>

							{#if mod.availableUntil}
								<div class="flex items-center gap-2">
									<div
										class="h-2 w-2 rounded-full {isExpired(mod.availableUntil)
											? 'bg-rose-500'
											: 'animate-pulse bg-amber-500'}"
									></div>
									<span
										class="font-mono text-[9px] font-bold tracking-widest uppercase {isExpired(
											mod.availableUntil
										)
											? 'text-rose-600'
											: 'text-amber-600'}"
									>
										{isExpired(mod.availableUntil)
											? 'Zeit abgelaufen'
											: `Noch: ${getTimeLeft(mod.availableUntil)}`}
									</span>
								</div>
							{/if}
						</div>

						<a
							href={`/modules/${mod.slug}`}
							class="rounded-none bg-institutional-navy px-8 py-3 text-[10px] font-black tracking-[3px] text-white uppercase shadow-sm transition-all hover:bg-slate-800"
						>
							{isExpired(mod.availableUntil) ? 'Ansehen' : 'Öffnen'}
						</a>
					</div>
				</div>
			{/each}
		</div>
	{/if}
</div>
