import { api, type CourseSummary } from '$lib/api';

const KEY = 'codepath:course';

/** Für die Lehrkraft: welcher Kurs gerade bearbeitet wird (Freigaben, Akte, Rangliste). */
class CourseSelection {
	courses = $state<CourseSummary[]>([]);
	selectedId = $state<number | null>(null);

	get selected(): CourseSummary | null {
		return this.courses.find((c) => c.id === this.selectedId) ?? null;
	}

	async load() {
		this.courses = await api<CourseSummary[]>('/api/admin/courses');
		let stored: number | null = null;
		try {
			stored = Number(localStorage.getItem(KEY)) || null;
		} catch {
			// kein Speicher
		}
		const active = this.courses.filter((c) => c.status === 'ACTIVE');
		this.selectedId =
			active.find((c) => c.id === stored)?.id ?? active[0]?.id ?? this.courses[0]?.id ?? null;
	}

	select(id: number) {
		this.selectedId = id;
		try {
			localStorage.setItem(KEY, String(id));
		} catch {
			// kein Speicher
		}
	}

	query(prefix = '?') {
		return this.selectedId ? `${prefix}courseId=${this.selectedId}` : '';
	}
}

export const courseSelection = new CourseSelection();
