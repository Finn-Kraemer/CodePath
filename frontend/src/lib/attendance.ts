/** Anzeige der Anwesenheits-Status (Backend: attendance.AttendanceStatus). */
export type AttendanceStatus = 'PRESENT' | 'LATE' | 'EXCUSED' | 'ABSENT';

export const STATUS_ORDER: AttendanceStatus[] = ['PRESENT', 'LATE', 'EXCUSED', 'ABSENT'];

export const STATUS: Record<
	AttendanceStatus,
	{ label: string; short: string; active: string; cell: string }
> = {
	PRESENT: {
		label: 'anwesend',
		short: '✓',
		active: 'border-emerald-600 bg-emerald-600 text-white',
		cell: 'text-emerald-700'
	},
	LATE: {
		label: 'verspätet',
		short: 'V',
		active: 'border-amber-500 bg-amber-500 text-white',
		cell: 'text-amber-700'
	},
	EXCUSED: {
		label: 'entschuldigt',
		short: 'E',
		active: 'border-sky-600 bg-sky-600 text-white',
		cell: 'text-sky-700'
	},
	ABSENT: {
		label: 'fehlt',
		short: '✗',
		active: 'border-rose-600 bg-rose-600 text-white',
		cell: 'font-bold text-rose-700'
	}
};
