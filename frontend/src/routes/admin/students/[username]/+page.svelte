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

	// Reaktiv für ausgeklappte Module (Standard: leer = alle zu)
	let expandedModules = $state<Record<string, boolean>>({});

	function toggleModule(title: string) {
		expandedModules[title] = !expandedModules[title];
	}

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

	async function toggleTask(task: any, halfPoints: boolean = false) {
		if (processingTaskId !== null) return;

		processingTaskId = task.id;
		
		try {
			const res = await auth.apiFetch(`/api/admin/students/${username}/tasks/${task.id}/toggle`, {
				method: 'PUT',
				body: JSON.stringify({ halfPoints })
			});

			if (res.ok) {
				// Refresh all data to get consistent state
				await fetchData();
			} else {
				alert('Fehler beim Speichern.');
			}
		} catch (e) {
			console.error(e);
		} finally {
			processingTaskId = null;
		}
	}

	async function toggleTaskLock(task: any) {
		if (processingTaskId !== null) return;

		processingTaskId = task.id;
		const originalState = task.isLocked;

		try {
			const res = await auth.apiFetch(`/api/admin/students/${username}/tasks/${task.id}/toggle-lock`, {
				method: 'PUT'
			});

			if (res.ok) {
				task.isLocked = !originalState;
			} else {
				alert('Fehler beim Sperren/Entsperren.');
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
					<div class="space-y-8">
						{#each Object.entries(groupedTasks) as [moduleTitle, moduleTasks] (moduleTitle)}
							<div class="border border-slate-100 rounded-none overflow-hidden">
								<button
									onclick={() => toggleModule(moduleTitle)}
									class="flex w-full items-center justify-between bg-slate-50 p-4 transition-colors hover:bg-slate-100"
								>
									<h4 class="font-sans text-[11px] font-black tracking-[3px] text-institutional-navy uppercase text-left">
										{moduleTitle}
									</h4>
									<div class="flex items-center gap-3 shrink-0">
										<span class="font-mono text-[9px] text-slate-400 uppercase tracking-widest">
											{moduleTasks.filter(t => t.isCompleted).length} / {moduleTasks.length} gelöst
										</span>
										<svg 
											xmlns="http://www.w3.org/2000/svg" 
											class="h-4 w-4 text-slate-400 transition-transform duration-300 {expandedModules[moduleTitle] ? '' : '-rotate-90'}" 
											fill="none" viewBox="0 0 24 24" stroke="currentColor"
										>
											<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
										</svg>
									</div>
								</button>
								
								{#if expandedModules[moduleTitle]}
									<div class="p-4 grid grid-cols-1 gap-3 animate-in fade-in slide-in-from-top-2 duration-200">
										{#each moduleTasks as task (task.id)}
											<div class="group flex w-full items-stretch gap-2">
												<!-- Task Info Card (Status Display) -->
												<div
													class="flex-1 flex items-center justify-between border p-6 text-left transition-all rounded-none
													{task.isLocked 
														? 'border-red-200 bg-red-50/30' 
														: task.isCompleted
															? task.supportUsed
																? 'border-amber-200 bg-amber-50/30'
																: 'border-green-200 bg-green-50/30'
															: 'border-slate-100 bg-slate-50'}"
												>
													<div class="flex items-center gap-6">
														<div
															class="flex h-10 w-10 items-center justify-center border transition-colors rounded-none
															{task.isLocked
																? 'border-red-600 bg-red-600 text-white'
																: task.isCompleted
																	? task.supportUsed
																		? 'border-amber-500 bg-amber-500 text-white'
																		: 'border-green-600 bg-green-600 text-white'
																	: 'border-slate-200 bg-white text-slate-300'}"
														>
															{#if processingTaskId === task.id}
																<div class="h-4 w-4 border-2 border-institutional-navy border-t-transparent animate-spin"></div>
															{:else if task.isLocked}
																<svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" viewBox="0 0 20 20" fill="currentColor">
																	<path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd" />
																</svg>
															{:else if task.isCompleted}
																<svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" viewBox="0 0 20 20" fill="currentColor">
																	<path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd" />
																</svg>
															{/if}
														</div>
														<div>
															<p class="font-sans text-sm font-bold {task.isLocked ? 'text-red-900' : task.isCompleted ? (task.supportUsed ? 'text-amber-900' : 'text-green-900') : 'text-slate-700'} uppercase tracking-tight">
																{task.title}
															</p>
															<p class="mt-1 font-mono text-[9px] font-bold tracking-widest text-slate-400 uppercase">
																{task.type} · {task.points} PKT {#if task.supportUsed}(50%){/if}
															</p>
														</div>
													</div>
													<span
														class="font-mono text-[10px] font-black uppercase 
														{task.isLocked ? 'text-red-600' : task.isCompleted ? (task.supportUsed ? 'text-amber-600' : 'text-green-600') : 'text-slate-300'}"
													>
														{task.isLocked ? 'Gesperrt' : task.isCompleted ? (task.supportUsed ? 'Teil-Valid.' : 'Validiert') : 'Offen'}
													</span>
												</div>

												<!-- Actions -->
												<div class="flex gap-1">
													<button
														onclick={() => toggleTask(task, false)}
														disabled={processingTaskId === task.id}
														title="Volle Punkte (Toggle)"
														class="px-4 border transition-all 
														{task.isCompleted && !task.supportUsed 
															? 'bg-green-600 border-green-600 text-white' 
															: 'bg-white border-slate-200 text-slate-400 hover:border-green-600 hover:text-green-600'}"
													>
														<span class="font-mono text-[10px] font-black">100%</span>
													</button>
													<button
														onclick={() => toggleTask(task, true)}
														disabled={processingTaskId === task.id}
														title="Halbe Punkte (Toggle)"
														class="px-4 border transition-all 
														{task.isCompleted && task.supportUsed 
															? 'bg-amber-500 border-amber-500 text-white' 
															: 'bg-white border-slate-200 text-slate-400 hover:border-amber-500 hover:text-amber-500'}"
													>
														<span class="font-mono text-[10px] font-black">50%</span>
													</button>
													<button
														onclick={() => toggleTaskLock(task)}
														disabled={processingTaskId === task.id}
														title={task.isLocked ? 'Entsperren' : 'Sperren'}
														class="px-4 border transition-all 
														{task.isLocked 
															? 'bg-red-600 border-red-600 text-white' 
															: 'bg-white border-slate-200 text-slate-300 hover:border-red-600 hover:text-red-600'}"
													>
														{#if task.isLocked}
															<svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
																<path fill-rule="evenodd" d="M5 9V7a5 5 0 0110 0v2a2 2 0 012 2v5a2 2 0 01-2 2H5a2 2 0 01-2-2v-5a2 2 0 012-2zm8-2v2H7V7a3 3 0 016 0z" clip-rule="evenodd" />
															</svg>
														{:else}
															<svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
																<path d="M10 2a5 5 0 00-5 5v2a2 2 0 00-2 2v5a2 2 0 002 2h10a2 2 0 002-2v-5a2 2 0 00-2-2V7a5 5 0 00-5-5zM7 7a3 3 0 016 0v2H7V7z" />
															</svg>
														{/if}
													</button>
												</div>
											</div>
										{/each}
									</div>
								{/if}
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
