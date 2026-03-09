<script lang="ts">
	import { onMount } from 'svelte';
	import { auth } from '$lib/auth.svelte';

	interface Module {
		id: number;
		slug: string;
		title: string;
		description: string;
		iconEmoji: string;
		isUnlocked: boolean;
		totalTasks: number;
		completedTasks: number;
		availableUntil?: string;
	}

	let modules = $state<Module[]>([]);
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
			const res = await auth.apiFetch('/api/modules');
			if (res.ok) {
				modules = await res.json();
			}
		} catch (e) {
			console.error(e);
		} finally {
			loading = false;
		}
	}

	function getTimeLeft(deadline: string) {
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
			<div class="h-12 w-12 border-4 border-slate-200 border-t-institutional-navy animate-spin"></div>
		</div>
	{:else if modules.length === 0}
		<div class="border border-dashed border-slate-200 bg-white p-24 text-center rounded-none">
			<p class="font-serif text-sm text-slate-400 italic">
				Aktuell sind keine Module zur Bearbeitung freigeschaltet.
			</p>
		</div>
	{:else}
		<div class="grid grid-cols-1 gap-8 md:grid-cols-2 lg:grid-cols-3">
			{#each modules as mod (mod.slug)}
				<div
					class="relative flex flex-col border border-slate-200 bg-white shadow-sm transition-all rounded-none
                    {isExpired(mod.availableUntil) ? 'grayscale opacity-80' : 'hover:border-institutional-navy hover:shadow-md'}"
				>
                    {#if isExpired(mod.availableUntil)}
                        <div class="absolute inset-0 z-10 flex items-center justify-center bg-white/40 backdrop-blur-[1px] pointer-events-none">
                            <div class="bg-rose-600 text-white px-4 py-2 font-mono text-[10px] font-black tracking-[3px] uppercase shadow-xl -rotate-3 border-2 border-white">
                                Bearbeitungszeit Abgelaufen
                            </div>
                        </div>
                    {/if}

					<div class="flex-grow p-8">
						<div class="mb-6 flex items-center justify-between">
							<span class="text-4xl grayscale transition-all {isExpired(mod.availableUntil) ? '' : 'hover:grayscale-0'}"
								>{mod.iconEmoji}</span
							>
							{#if mod.completedTasks === mod.totalTasks && mod.totalTasks > 0}
								<span
									class="bg-green-100 px-3 py-1 font-mono text-[9px] font-black text-green-700 uppercase tracking-widest rounded-none"
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
					<div class="flex items-center justify-between border-t border-slate-100 bg-slate-50/50 p-8">
						<div class="flex flex-col gap-4">
							<div class="flex items-center gap-4">
								<div class="h-1.5 w-32 bg-slate-200 rounded-none overflow-hidden">
									<div
										class="h-full bg-institutional-navy transition-all duration-1000"
										style="width: {(mod.completedTasks / (mod.totalTasks || 1)) * 100}%"
									></div>
								</div>
								<span class="font-mono text-[10px] font-bold text-slate-400 uppercase tracking-widest">
									{mod.completedTasks}/{mod.totalTasks}
								</span>
							</div>

							{#if mod.availableUntil}
								<div class="flex items-center gap-2">
									<div
										class="h-2 w-2 rounded-full {isExpired(mod.availableUntil)
											? 'bg-rose-500'
											: 'bg-amber-500 animate-pulse'}"
									></div>
									<span
										class="font-mono text-[9px] font-bold uppercase tracking-widest {isExpired(
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
							class="bg-institutional-navy px-8 py-3 text-[10px] font-black tracking-[3px] text-white uppercase shadow-sm transition-all hover:bg-slate-800 rounded-none
                            {isExpired(mod.availableUntil) ? 'pointer-events-none opacity-20' : ''}"
						>
							{isExpired(mod.availableUntil) ? 'Gesperrt' : 'Öffnen'}
						</a>
					</div>
				</div>
			{/each}
		</div>
	{/if}
</div>
