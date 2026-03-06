<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/state';
	import { auth } from '$lib/auth.svelte';

	const username = page.params.username;
	let loading = $state(true);
	let student = $state<any>(null);
	let tasks = $state<any[]>([]);
	let groupedTasks = $derived.by(() => {
		const groups: Record<string, any[]> = {};
		tasks.forEach((t) => {
			if (!groups[t.moduleTitle]) groups[t.moduleTitle] = [];
			groups[t.moduleTitle].push(t);
		});
		return groups;
	});
	let newPassword = $state('');
	let success = $state('');
	let processingTaskId = $state<number | null>(null);

	async function fetchData() {
		try {
			const [studentRes, tasksRes] = await Promise.all([
				auth.apiFetch(`/api/users/profile?username=${username}`),
				auth.apiFetch(`/api/admin/students/${username}/tasks`)
			]);

			if (studentRes.ok && tasksRes.ok) {
				student = await studentRes.json();
				tasks = await tasksRes.json();
			}
		} catch (e) {
			console.error(e);
		} finally {
			loading = false;
		}
	}

	async function toggleTask(task: any) {
		if (processingTaskId !== null) return;

		processingTaskId = task.id;
		const originalState = task.isCompleted;

		try {
			const res = await auth.apiFetch(`/api/admin/students/${username}/tasks/${task.id}/toggle`, {
				method: 'PUT'
			});

			if (res.ok) {
				task.isCompleted = !originalState;
				// Refresh student stats to show updated points
				const studentRes = await auth.apiFetch(`/api/users/profile?username=${username}`);
				if (studentRes.ok) {
					student = await studentRes.json();
				}
			} else {
				alert('Fehler beim Speichern.');
			}
		} catch (e) {
			console.error(e);
		} finally {
			processingTaskId = null;
		}
	}

	async function handleResetPassword() {
		if (!newPassword) return;
		try {
			const res = await auth.apiFetch(`/api/admin/students/${username}/reset-password`, {
				method: 'PUT',
				body: JSON.stringify({ password: newPassword })
			});
			if (res.ok) {
				success = 'Passwort wurde zurückgesetzt!';
				newPassword = '';
				setTimeout(() => (success = ''), 3000);
			}
		} catch (e) {
			console.error(e);
		}
	}

	onMount(fetchData);
</script>

<div class="flex flex-col gap-12">
	<header class="border-b border-slate-200 pb-8">
		<a
			href="/admin"
			class="mb-6 inline-block font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase transition-colors hover:text-institutional-navy"
			>← Zurück zur Administration</a
		>
		<h1 class="font-sans text-4xl font-extrabold tracking-tight text-institutional-navy uppercase">
			Teilnehmer-Akte: {username}
		</h1>
	</header>

	{#if loading}
		<div class="flex justify-center py-24">
			<div class="h-12 w-12 border-4 border-slate-200 border-t-institutional-navy animate-spin"></div>
		</div>
	{:else if student}
		<div class="grid grid-cols-1 gap-12 lg:grid-cols-3">
			<div class="space-y-12 lg:col-span-2">
				<!-- Task Checklist -->
				<div class="border border-slate-200 bg-white p-10 shadow-sm rounded-none">
					<h3 class="mb-10 font-sans text-sm font-black tracking-widest text-slate-400 uppercase">
						Leistungshistorie nach Modulen
					</h3>
					<div class="space-y-12">
						{#each Object.entries(groupedTasks) as [moduleTitle, moduleTasks] (moduleTitle)}
							<div>
								<h4
									class="mb-6 border-b border-slate-100 pb-3 font-sans text-[11px] font-black tracking-[3px] text-institutional-navy uppercase"
								>
									{moduleTitle}
								</h4>
								<div class="grid grid-cols-1 gap-3">
									{#each moduleTasks as task (task.id)}
										<button
											onclick={() => toggleTask(task)}
											disabled={processingTaskId === task.id}
											class="group flex w-full items-center justify-between border p-6 text-left transition-all rounded-none
                                            {task.isCompleted
												? 'border-green-200 bg-green-50/30 hover:border-green-300'
												: 'border-slate-100 bg-slate-50 hover:border-slate-200'}"
										>
											<div class="flex items-center gap-6">
												<div
													class="flex h-10 w-10 items-center justify-center border transition-colors rounded-none
                                                    {task.isCompleted
														? 'border-green-600 bg-green-600 text-white'
														: 'border-slate-200 bg-white text-slate-300 group-hover:border-slate-400'}"
												>
													{#if processingTaskId === task.id}
														<div class="h-4 w-4 border-2 border-white border-t-transparent animate-spin"></div>
													{:else if task.isCompleted}
														<svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" viewBox="0 0 20 20" fill="currentColor">
															<path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd" />
														</svg>
													{/if}
												</div>
												<div>
													<p class="font-sans text-sm font-bold {task.isCompleted ? 'text-green-900' : 'text-slate-700'} uppercase tracking-tight">
														{task.title}
													</p>
													<p class="mt-1 font-mono text-[9px] font-bold tracking-widest text-slate-400 uppercase">
														{task.type} · {task.points} PKT
													</p>
												</div>
											</div>
											<span
												class="font-mono text-[10px] font-black uppercase {task.isCompleted
													? 'text-green-600'
													: 'text-slate-300'}"
											>
												{task.isCompleted ? 'Validiert' : 'Offen'}
											</span>
										</button>
									{/each}
								</div>
							</div>
						{/each}
					</div>
				</div>
			</div>

			<div class="space-y-8">
				<!-- Stats Card -->
				<div class="bg-institutional-navy p-10 text-white shadow-sm rounded-none">
					<h3 class="mb-10 font-sans text-[10px] font-black tracking-[4px] text-institutional-gold uppercase">
						Kennzahlen
					</h3>
					<div class="grid grid-cols-1 gap-8">
						<div class="border-l-4 border-white/10 bg-white/5 p-6 rounded-none">
							<p class="mb-2 font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase">
								Gesamtpunktzahl
							</p>
							<p class="text-5xl font-black text-institutional-gold">{student.totalPoints}</p>
						</div>
						<div class="border-l-4 border-white/10 bg-white/5 p-6 rounded-none">
							<p class="mb-2 font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase">
								Erledigte Aufgaben
							</p>
							<p class="text-5xl font-black text-white">{student.completedTasksCount}</p>
						</div>
					</div>
					<a
						href={`/admin/students/${username}/submissions`}
						class="mt-10 flex w-full items-center justify-center gap-3 bg-institutional-gold py-5 text-center text-[11px] font-bold tracking-[3px] text-white uppercase shadow-lg transition-all hover:opacity-90 rounded-none"
					>
						Abgaben verifizieren
						{#if student.pendingSubmissionsCount > 0}
							<span class="flex h-6 min-w-[1.5rem] items-center justify-center bg-white px-2 font-mono text-[10px] font-black text-institutional-gold">
								{student.pendingSubmissionsCount}
							</span>
						{/if}
					</a>
				</div>

				<!-- Password Reset Card -->
				<div class="border border-slate-200 bg-white p-10 shadow-sm rounded-none">
					<h3 class="mb-6 font-sans text-[10px] font-black tracking-widest text-slate-400 uppercase">
						Sicherheits-Optionen
					</h3>
					{#if success}
						<div class="mb-6 border border-green-100 bg-green-50 p-4 font-mono text-[10px] text-green-700 uppercase tracking-widest rounded-none">
							{success}
						</div>
					{/if}
					<div class="space-y-6">
						<div>
							<label for="pwd" class="mb-2 block font-mono text-[9px] font-bold text-slate-400 uppercase tracking-widest">Neues Systempasswort</label>
							<input
								bind:value={newPassword}
								type="password"
								id="pwd"
								placeholder="••••••••"
								class="w-full border-slate-200 bg-slate-50 px-4 py-3 font-mono text-sm outline-none focus:border-institutional-navy rounded-none"
							/>
						</div>
						<button
							onclick={handleResetPassword}
							class="w-full bg-institutional-navy py-4 text-[10px] font-bold tracking-widest text-white uppercase shadow-sm transition-all hover:opacity-90 rounded-none"
							>Passwort überschreiben</button
						>
					</div>
				</div>
			</div>
		</div>
	{/if}
</div>
