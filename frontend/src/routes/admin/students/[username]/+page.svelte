<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/state';
	import { api, type Schemas } from '$lib/api';
	import { auth } from '$lib/auth.svelte';
	import PathMap from '$lib/pathway/PathMap.svelte';

	type StudentPathAdminView = Schemas['StudentPathAdminView'];

	const username = page.params.username;
	let loading = $state(true);
	let activeTab = $state<'tasks' | 'path'>('tasks');
	let student = $state<any>(null);
	let tasks = $state<any[]>([]);
	let pathAdmin = $state<StudentPathAdminView | null>(null);
	let pathBusy = $state(false);
	let pathMessage = $state('');
	let pathError = $state('');
	let resetProgressByChoice = $state<Record<string, boolean>>({});
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

	async function fetchData(requestedSlug?: string) {
		try {
			const query = requestedSlug ? `?slug=${encodeURIComponent(requestedSlug)}` : '';
			const [studentRes, tasksRes, pathData] = await Promise.all([
				auth.apiFetch(`/api/users/profile?username=${username}`),
				auth.apiFetch(`/api/admin/students/${username}/tasks`),
				api<StudentPathAdminView>(`/api/admin/students/${username}/learning-path${query}`).catch(
					() => null
				)
			]);

			if (studentRes.ok && tasksRes.ok) {
				student = await studentRes.json();
				tasks = await tasksRes.json();
			}
			if (pathData) {
				pathAdmin = pathData;
			}
		} catch (e) {
			console.error(e);
		} finally {
			loading = false;
		}
	}

	function flashPath(text: string) {
		pathMessage = text;
		setTimeout(() => (pathMessage = ''), 3000);
	}

	async function resetChoice(pathSlug: string, nodeId: string) {
		const key = `${pathSlug}:${nodeId}`;
		const resetProgress = !!resetProgressByChoice[key];
		pathBusy = true;
		pathError = '';
		try {
			await api(`/api/admin/students/${username}/learning-paths/${pathSlug}/choices/reset`, {
				method: 'POST',
				body: { nodeId, resetProgress }
			});
			await fetchData(pathAdmin?.selectedSlug ?? undefined);
			flashPath(
				resetProgress
					? 'Wahl und Fortschritt im verlassenen Weg zurückgesetzt.'
					: 'Wahl zurückgesetzt.'
			);
		} catch (e) {
			pathError = (e as Error).message;
		} finally {
			pathBusy = false;
		}
	}

	async function setChoiceForStudent(pathSlug: string, nodeId: string, to: string) {
		pathBusy = true;
		pathError = '';
		try {
			await api(`/api/admin/students/${username}/learning-paths/${pathSlug}/choices/set`, {
				method: 'POST',
				body: { nodeId, to }
			});
			await fetchData(pathAdmin?.selectedSlug ?? undefined);
			flashPath('Weg für Schüler/in gesetzt.');
		} catch (e) {
			pathError = (e as Error).message;
		} finally {
			pathBusy = false;
		}
	}

	async function toggleUnlockBranch(pathSlug: string, nodeId: string, to: string) {
		pathBusy = true;
		pathError = '';
		try {
			await api(`/api/admin/students/${username}/learning-paths/${pathSlug}/branches/unlock`, {
				method: 'POST',
				body: { nodeId, to }
			});
			await fetchData(pathAdmin?.selectedSlug ?? undefined);
			flashPath('Zweig-Freigabe aktualisiert.');
		} catch (e) {
			pathError = (e as Error).message;
		} finally {
			pathBusy = false;
		}
	}

	async function toggleUnlockNode(pathSlug: string, nodeId: string) {
		pathBusy = true;
		pathError = '';
		try {
			await api(`/api/admin/students/${username}/learning-paths/${pathSlug}/nodes/unlock`, {
				method: 'POST',
				body: { nodeId }
			});
			await fetchData(pathAdmin?.selectedSlug ?? undefined);
			flashPath('Stations-Freigabe aktualisiert.');
		} catch (e) {
			pathError = (e as Error).message;
		} finally {
			pathBusy = false;
		}
	}

	async function resetBranchCondition(pathSlug: string, edgeKey?: string) {
		pathBusy = true;
		pathError = '';
		try {
			const query = edgeKey ? `?edgeKey=${encodeURIComponent(edgeKey)}` : '';
			await api(`/api/admin/students/${username}/learning-paths/${pathSlug}/conditions/reset${query}`, {
				method: 'POST'
			});
			await fetchData(pathAdmin?.selectedSlug ?? undefined);
			flashPath('Bedingung wird neu bewertet.');
		} catch (e) {
			pathError = (e as Error).message;
		} finally {
			pathBusy = false;
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
			const res = await auth.apiFetch(
				`/api/admin/students/${username}/tasks/${task.id}/toggle-lock`,
				{
					method: 'PUT'
				}
			);

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

	onMount(() => fetchData());
</script>

<div class="flex flex-col gap-12">
	<header class="border-b border-slate-200 pb-8">
		<a
			href="/admin"
			class="mb-6 inline-block font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase transition-colors hover:text-institutional-navy"
			>← Zurück zur Administration</a
		>
		<div class="flex flex-wrap items-end justify-between gap-4">
			<h1 class="font-sans text-4xl font-extrabold tracking-tight text-institutional-navy uppercase">
				Teilnehmer-Akte: {username}
			</h1>
			<div class="flex border border-slate-200 bg-white" role="tablist" aria-label="Ansicht">
				<button
					type="button"
					role="tab"
					aria-selected={activeTab === 'tasks'}
					class="px-5 py-2.5 text-[10px] font-black tracking-[2px] uppercase {activeTab === 'tasks'
						? 'bg-institutional-navy text-white'
						: 'text-slate-500 hover:text-institutional-navy'}"
					onclick={() => (activeTab = 'tasks')}
				>
					Aufgaben
				</button>
				<button
					type="button"
					role="tab"
					aria-selected={activeTab === 'path'}
					class="px-5 py-2.5 text-[10px] font-black tracking-[2px] uppercase {activeTab === 'path'
						? 'bg-institutional-navy text-white'
						: 'text-slate-500 hover:text-institutional-navy'}"
					onclick={() => (activeTab = 'path')}
				>
					Lernpfad
				</button>
			</div>
		</div>
	</header>

	{#if loading}
		<div class="flex justify-center py-24">
			<div
				class="h-12 w-12 animate-spin border-4 border-slate-200 border-t-institutional-navy"
			></div>
		</div>
	{:else if student}
		<div class="grid grid-cols-1 gap-12 lg:grid-cols-3">
			<div class="space-y-12 lg:col-span-2">
				{#if activeTab === 'tasks'}
				<!-- Task Checklist -->
				<div class="rounded-none border border-slate-200 bg-white p-10 shadow-sm">
					<h3 class="mb-10 font-sans text-sm font-black tracking-widest text-slate-400 uppercase">
						Leistungshistorie nach Modulen
					</h3>
					<div class="space-y-8">
						{#each Object.entries(groupedTasks) as [moduleTitle, moduleTasks] (moduleTitle)}
							<div class="overflow-hidden rounded-none border border-slate-100">
								<button
									onclick={() => toggleModule(moduleTitle)}
									class="flex w-full items-center justify-between bg-slate-50 p-4 transition-colors hover:bg-slate-100"
								>
									<h4
										class="text-left font-sans text-[11px] font-black tracking-[3px] text-institutional-navy uppercase"
									>
										{moduleTitle}
									</h4>
									<div class="flex shrink-0 items-center gap-3">
										<span class="font-mono text-[9px] tracking-widest text-slate-400 uppercase">
											{moduleTasks.filter((t) => t.isCompleted).length} / {moduleTasks.length} gelöst
										</span>
										<svg
											xmlns="http://www.w3.org/2000/svg"
											class="h-4 w-4 text-slate-400 transition-transform duration-300 {expandedModules[
												moduleTitle
											]
												? ''
												: '-rotate-90'}"
											fill="none"
											viewBox="0 0 24 24"
											stroke="currentColor"
										>
											<path
												stroke-linecap="round"
												stroke-linejoin="round"
												stroke-width="2"
												d="M19 9l-7 7-7-7"
											/>
										</svg>
									</div>
								</button>

								{#if expandedModules[moduleTitle]}
									<div
										class="animate-in fade-in slide-in-from-top-2 grid grid-cols-1 gap-3 p-4 duration-200"
									>
										{#each moduleTasks as task (task.id)}
											<div class="group flex w-full items-stretch gap-2">
												<!-- Task Info Card (Status Display) -->
												<div
													class="flex flex-1 items-center justify-between rounded-none border p-6 text-left transition-all
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
															class="flex h-10 w-10 items-center justify-center rounded-none border transition-colors
															{task.isLocked
																? 'border-red-600 bg-red-600 text-white'
																: task.isCompleted
																	? task.supportUsed
																		? 'border-amber-500 bg-amber-500 text-white'
																		: 'border-green-600 bg-green-600 text-white'
																	: 'border-slate-200 bg-white text-slate-300'}"
														>
															{#if processingTaskId === task.id}
																<div
																	class="h-4 w-4 animate-spin border-2 border-institutional-navy border-t-transparent"
																></div>
															{:else if task.isLocked}
																<svg
																	xmlns="http://www.w3.org/2000/svg"
																	class="h-6 w-6"
																	viewBox="0 0 20 20"
																	fill="currentColor"
																>
																	<path
																		fill-rule="evenodd"
																		d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z"
																		clip-rule="evenodd"
																	/>
																</svg>
															{:else if task.isCompleted}
																<svg
																	xmlns="http://www.w3.org/2000/svg"
																	class="h-6 w-6"
																	viewBox="0 0 20 20"
																	fill="currentColor"
																>
																	<path
																		fill-rule="evenodd"
																		d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
																		clip-rule="evenodd"
																	/>
																</svg>
															{/if}
														</div>
														<div>
															<p
																class="font-sans text-sm font-bold {task.isLocked
																	? 'text-red-900'
																	: task.isCompleted
																		? task.supportUsed
																			? 'text-amber-900'
																			: 'text-green-900'
																		: 'text-slate-700'} tracking-tight uppercase"
															>
																{task.title}
															</p>
															<p
																class="mt-1 font-mono text-[9px] font-bold tracking-widest text-slate-400 uppercase"
															>
																{task.type} · {task.pointsEarned}/{task.points} PKT {#if task.hintsUsed}·
																	{task.hintsUsed} Hinweis(e){/if}{#if task.solutionRevealed}
																	· Lösung aufgedeckt{/if}
															</p>
														</div>
													</div>
													<span
														class="font-mono text-[10px] font-black uppercase
														{task.isLocked
															? 'text-red-600'
															: task.isCompleted
																? task.supportUsed
																	? 'text-amber-600'
																	: 'text-green-600'
																: 'text-slate-300'}"
													>
														{task.isLocked
															? 'Gesperrt'
															: task.isCompleted
																? task.supportUsed
																	? 'Teil-Valid.'
																	: 'Validiert'
																: 'Offen'}
													</span>
												</div>

												<!-- Actions -->
												<div class="flex gap-1">
													<button
														onclick={() => toggleTask(task, false)}
														disabled={processingTaskId === task.id}
														title="Volle Punkte (Toggle)"
														class="border px-4 transition-all
														{task.isCompleted && !task.supportUsed
															? 'border-green-600 bg-green-600 text-white'
															: 'border-slate-200 bg-white text-slate-400 hover:border-green-600 hover:text-green-600'}"
													>
														<span class="font-mono text-[10px] font-black">100%</span>
													</button>
													<button
														onclick={() => toggleTask(task, true)}
														disabled={processingTaskId === task.id}
														title="Halbe Punkte (Toggle)"
														class="border px-4 transition-all
														{task.isCompleted && task.supportUsed
															? 'border-amber-500 bg-amber-500 text-white'
															: 'border-slate-200 bg-white text-slate-400 hover:border-amber-500 hover:text-amber-500'}"
													>
														<span class="font-mono text-[10px] font-black">50%</span>
													</button>
													<button
														onclick={() => toggleTaskLock(task)}
														disabled={processingTaskId === task.id}
														title={task.isLocked ? 'Entsperren' : 'Sperren'}
														class="border px-4 transition-all
														{task.isLocked
															? 'border-red-600 bg-red-600 text-white'
															: 'border-slate-200 bg-white text-slate-300 hover:border-red-600 hover:text-red-600'}"
													>
														{#if task.isLocked}
															<svg
																xmlns="http://www.w3.org/2000/svg"
																class="h-4 w-4"
																viewBox="0 0 20 20"
																fill="currentColor"
															>
																<path
																	fill-rule="evenodd"
																	d="M5 9V7a5 5 0 0110 0v2a2 2 0 012 2v5a2 2 0 01-2 2H5a2 2 0 01-2-2v-5a2 2 0 012-2zm8-2v2H7V7a3 3 0 016 0z"
																	clip-rule="evenodd"
																/>
															</svg>
														{:else}
															<svg
																xmlns="http://www.w3.org/2000/svg"
																class="h-4 w-4"
																viewBox="0 0 20 20"
																fill="currentColor"
															>
																<path
																	d="M10 2a5 5 0 00-5 5v2a2 2 0 00-2 2v5a2 2 0 002 2h10a2 2 0 002-2v-5a2 2 0 00-2-2V7a5 5 0 00-5-5zM7 7a3 3 0 016 0v2H7V7z"
																/>
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
				{:else}
					<div class="space-y-8 border border-slate-200 bg-white p-10 shadow-sm">
						<div class="flex flex-wrap items-center justify-between gap-4">
							<div>
								<h3 class="font-sans text-sm font-black tracking-widest text-slate-400 uppercase">
									Lernpfad-Stand &amp; Eingriffe
								</h3>
								{#if pathAdmin?.view}
									<p class="mt-1 text-lg font-bold text-institutional-navy">
										{pathAdmin.view.title}
										<span class="ml-2 font-mono text-xs text-slate-400">
											({pathAdmin.view.stationsDone}/{pathAdmin.view.stations} Stationen)
										</span>
									</p>
								{/if}
							</div>
							{#if (pathAdmin?.paths?.length ?? 0) > 1}
								<select
									aria-label="Lernpfad auswählen"
									class="border border-slate-200 bg-white px-3 py-2 text-sm"
									value={pathAdmin?.selectedSlug ?? ''}
									onchange={(e) => fetchData((e.currentTarget as HTMLSelectElement).value)}
								>
									{#each pathAdmin?.paths ?? [] as p (p.slug)}
										<option value={p.slug}>{p.title}</option>
									{/each}
								</select>
							{/if}
						</div>

						{#if pathMessage}
							<div
								class="border border-emerald-200 bg-emerald-50 p-3 font-mono text-[10px] tracking-widest text-emerald-800 uppercase"
								role="status"
							>
								{pathMessage}
							</div>
						{/if}
						{#if pathError}
							<div
								class="border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700"
								role="alert"
							>
								{pathError}
							</div>
						{/if}

						{#if !pathAdmin?.view}
							<p class="py-12 text-center text-sm text-slate-400 italic">
								Für diese Person ist aktuell kein veröffentlichter Lernpfad aktiv.
							</p>
						{:else}
							<PathMap
								path={pathAdmin.view}
								student={username}
								onupdate={() => fetchData(pathAdmin?.selectedSlug ?? undefined)}
							/>

							{#if (pathAdmin.choices?.length ?? 0) > 0}
								<div class="space-y-4 border-t border-slate-100 pt-6">
									<h4
										class="font-mono text-[10px] font-black tracking-widest text-slate-400 uppercase"
									>
										Entscheidungen an Weichen
									</h4>
									{#each pathAdmin.choices as ch (`${ch.pathSlug}:${ch.nodeId}`)}
										{@const choiceKey = `${ch.pathSlug}:${ch.nodeId}`}
										<div class="space-y-3 border border-slate-200 bg-slate-50/60 p-4">
											<div class="flex flex-wrap items-start justify-between gap-3">
												<div>
													<div class="flex flex-wrap items-center gap-2">
														<span class="text-sm font-bold text-institutional-navy">
															🔀 {ch.nodeLabel}
														</span>
														{#if ch.subpath}
															<span
																class="border border-violet-200 bg-violet-50 px-2 py-0.5 font-mono text-[9px] font-bold text-violet-800 uppercase"
															>
																🧩 Baustein: {ch.pathTitle}
															</span>
														{/if}
													</div>
													{#if ch.chosenTo}
														<p class="mt-1 text-xs text-slate-600">
															Gewählter Weg: <strong class="text-institutional-navy"
																>„{ch.chosenLabel}“</strong
															>
															· {ch.chosenBy === 'TEACHER'
																? 'gesetzt durch Lehrkraft'
																: 'gewählt durch Schüler/in'}
														</p>
													{:else}
														<p class="mt-1 text-xs text-slate-500">Noch keine Wahl getroffen.</p>
													{/if}
												</div>

												{#if ch.chosenTo}
													<div class="flex flex-wrap items-center gap-3">
														<label class="flex items-center gap-1.5 text-xs text-slate-600">
															<input
																type="checkbox"
																checked={!!resetProgressByChoice[choiceKey]}
																onchange={(e) =>
																	(resetProgressByChoice[choiceKey] = (
																		e.currentTarget as HTMLInputElement
																	).checked)}
															/>
															<span>Fortschritt im verlassenen Weg zurücksetzen</span>
														</label>
														<button
															type="button"
															disabled={pathBusy}
															class="border border-rose-300 bg-white px-3 py-1.5 font-mono text-[10px] font-bold tracking-widest text-rose-700 uppercase hover:bg-rose-50 disabled:opacity-50"
															onclick={() => resetChoice(ch.pathSlug, ch.nodeId)}
														>
															Wahl zurücksetzen
														</button>
													</div>
												{/if}
											</div>

											<div class="grid grid-cols-1 gap-2 sm:grid-cols-2">
												{#each ch.options ?? [] as opt (opt.to)}
													<div
														class="flex items-center justify-between gap-2 border border-slate-200 bg-white px-3 py-2 text-xs"
													>
														<span class="font-bold text-slate-800">{opt.label}</span>
														<div class="flex flex-wrap items-center gap-1.5">
															<button
																type="button"
																disabled={pathBusy || opt.chosen}
																class="border px-2.5 py-1 font-mono text-[9px] font-bold uppercase {opt.chosen
																	? 'border-emerald-600 bg-emerald-600 text-white'
																	: 'border-slate-200 text-institutional-navy hover:border-institutional-navy'}"
																onclick={() => setChoiceForStudent(ch.pathSlug, ch.nodeId, opt.to)}
															>
																{opt.chosen ? 'Gewählt ✓' : 'Wahl setzen'}
															</button>
															{#if !opt.chosen}
																<button
																	type="button"
																	disabled={pathBusy}
																	class="border px-2.5 py-1 font-mono text-[9px] font-bold uppercase {opt.unlocked
																		? 'border-sky-600 bg-sky-600 text-white'
																		: 'border-slate-200 text-slate-600 hover:border-sky-500 hover:text-sky-700'}"
																	onclick={() =>
																		toggleUnlockBranch(ch.pathSlug, ch.nodeId, opt.to)}
																>
																	{opt.unlocked ? 'Zusätzlich frei ✓' : '+ Weg freischalten'}
																</button>
															{/if}
														</div>
													</div>
												{/each}
											</div>
										</div>
									{/each}
								</div>
							{/if}

							<div class="space-y-3 border-t border-slate-100 pt-6">
								<h4
									class="font-mono text-[10px] font-black tracking-widest text-slate-400 uppercase"
								>
									Stationen einzeln freischalten ({pathAdmin.view.title})
								</h4>
								<div class="grid grid-cols-1 gap-2 sm:grid-cols-2">
									{#each pathAdmin.view.nodes.filter((n) => n.kind !== 'CHOICE') as node (node.id)}
										<div
											class="flex items-center justify-between gap-2 border border-slate-200 bg-slate-50/40 px-3 py-2 text-xs"
										>
											<div class="min-w-0">
												<span class="block truncate font-bold text-institutional-navy">
													{node.icon} {node.label}
												</span>
												<span class="font-mono text-[9px] text-slate-400 uppercase">
													{node.state}
												</span>
											</div>
											<button
												type="button"
												disabled={pathBusy}
												class="shrink-0 border px-2.5 py-1 font-mono text-[9px] font-bold uppercase {node.teacherUnlocked
													? 'border-emerald-600 bg-emerald-600 text-white'
													: 'border-slate-200 bg-white text-institutional-navy hover:border-institutional-navy'}"
												onclick={() => toggleUnlockNode(pathAdmin!.selectedSlug!, node.id)}
											>
												{node.teacherUnlocked ? 'Freigeschaltet ✓' : 'Station freischalten'}
											</button>
										</div>
									{/each}
								</div>
							</div>

							{#if pathAdmin && (pathAdmin.branches?.length ?? 0) > 0}
								<div class="space-y-3">
									<div class="flex items-center justify-between">
										<h4 class="font-sans text-[10px] font-black tracking-widest text-slate-400 uppercase">
											Bedingte Verzweigungen &amp; Stütz-Knoten
										</h4>
									</div>
									<div class="space-y-2">
										{#each pathAdmin.branches ?? [] as br (br.edgeKey)}
											<div class="flex items-center justify-between gap-3 border border-slate-200 bg-white p-3 text-xs">
												<div>
													<div class="flex items-center gap-2">
														<span class="font-bold text-institutional-navy">{br.fromLabel} → {br.toLabel}</span>
														<span class="rounded px-1.5 py-0.5 font-mono text-[9px] font-bold uppercase {br.matched ? 'bg-amber-100 text-amber-800' : 'bg-slate-100 text-slate-500'}">
															{br.matched ? 'Ausgelöst' : 'Nicht ausgelöst'}
														</span>
													</div>
													{#if br.detail}
														<p class="mt-0.5 text-xs text-slate-500">{br.detail}</p>
													{/if}
												</div>
												<button
													type="button"
													disabled={pathBusy}
													class="shrink-0 border border-slate-300 bg-white px-2.5 py-1 font-mono text-[9px] font-bold text-institutional-navy uppercase hover:bg-slate-50"
													onclick={() => resetBranchCondition(br.pathSlug, br.edgeKey)}
												>
													Neu bewerten
												</button>
											</div>
										{/each}
									</div>
								</div>
							{/if}
						{/if}
					</div>
				{/if}
			</div>

			<div class="space-y-8">
				<!-- Stats Card -->
				<div class="rounded-none bg-institutional-navy p-10 text-white shadow-sm">
					<h3
						class="mb-10 font-sans text-[10px] font-black tracking-[4px] text-institutional-gold uppercase"
					>
						Kennzahlen
					</h3>
					<div class="grid grid-cols-1 gap-8">
						<div class="rounded-none border-l-4 border-white/10 bg-white/5 p-6">
							<p
								class="mb-2 font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase"
							>
								Gesamtpunktzahl
							</p>
							<p class="text-5xl font-black text-institutional-gold">{student.totalPoints}</p>
						</div>
						<div class="rounded-none border-l-4 border-white/10 bg-white/5 p-6">
							<p
								class="mb-2 font-mono text-[10px] font-bold tracking-widest text-slate-400 uppercase"
							>
								Erledigte Aufgaben
							</p>
							<p class="text-5xl font-black text-white">{student.completedTasksCount}</p>
						</div>
					</div>
					<a
						href={`/admin/students/${username}/submissions`}
						class="mt-10 flex w-full items-center justify-center gap-3 rounded-none bg-institutional-gold py-5 text-center text-[11px] font-bold tracking-[3px] text-white uppercase shadow-lg transition-all hover:opacity-90"
					>
						Abgaben verifizieren
						{#if student.pendingSubmissionsCount > 0}
							<span
								class="flex h-6 min-w-[1.5rem] items-center justify-center bg-white px-2 font-mono text-[10px] font-black text-institutional-gold"
							>
								{student.pendingSubmissionsCount}
							</span>
						{/if}
					</a>
				</div>

				<!-- Password Reset Card -->
				<div class="rounded-none border border-slate-200 bg-white p-10 shadow-sm">
					<h3
						class="mb-6 font-sans text-[10px] font-black tracking-widest text-slate-400 uppercase"
					>
						Sicherheits-Optionen
					</h3>
					{#if success}
						<div
							class="mb-6 rounded-none border border-green-100 bg-green-50 p-4 font-mono text-[10px] tracking-widest text-green-700 uppercase"
						>
							{success}
						</div>
					{/if}
					<div class="space-y-6">
						<div>
							<label
								for="pwd"
								class="mb-2 block font-mono text-[9px] font-bold tracking-widest text-slate-400 uppercase"
								>Neues Systempasswort</label
							>
							<input
								bind:value={newPassword}
								type="password"
								id="pwd"
								placeholder="••••••••"
								class="w-full rounded-none border-slate-200 bg-slate-50 px-4 py-3 font-mono text-sm outline-none focus:border-institutional-navy"
							/>
						</div>
						<button
							onclick={handleResetPassword}
							class="w-full rounded-none bg-institutional-navy py-4 text-[10px] font-bold tracking-widest text-white uppercase shadow-sm transition-all hover:opacity-90"
							>Passwort überschreiben</button
						>
					</div>
				</div>
			</div>
		</div>
	{/if}
</div>
