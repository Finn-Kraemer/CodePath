import { expect, test, type Page } from '@playwright/test';
import { courseWithModules, registerStudent } from './helpers';

/** Klickt in jedem genannten Kriterium die gewünschte Stufe (Zahl, Stern oder Ja/Nein). */
async function answer(page: Page, values: Record<string, string>) {
	for (const [id, value] of Object.entries(values)) {
		const criterion = page.locator(`[data-criterion="${id}"]`);
		if (value.startsWith('★')) {
			await criterion.getByRole('button', { name: new RegExp(`^${value.length} von`) }).click();
		} else {
			await criterion.getByRole('button', { name: value, exact: true }).click();
		}
	}
}

test('Selbsteinschätzung, Unterrichtsbewertung und Bewertung durch die Lehrkraft', async ({
	browser
}) => {
	const courseName = `E2E Bewertung ${Date.now()}`;
	const { teacher, joinCode } = await courseWithModules(browser, courseName, []);
	const student = await registerStudent(browser, joinCode, `bew${Date.now() % 100000}`);

	// Lehrkraft legt eine Runde aus den mitgelieferten Vorlagen an und öffnet sie
	await teacher.goto('/admin/feedback/rounds/new');
	await teacher.getByLabel('Kurs', { exact: true }).selectOption({ label: courseName });
	await teacher.getByLabel('Titel').fill('Halbjahres-Feedback');
	await expect(teacher.getByText('Arbeitet konzentriert.')).toBeVisible();
	await teacher.getByRole('button', { name: 'Runde anlegen' }).click();
	await expect(teacher.getByText('Runde angelegt')).toBeVisible();
	await teacher.getByRole('button', { name: 'Öffnen' }).click();
	await expect(teacher.getByText('Offen – die Klasse kann ausfüllen')).toBeVisible();

	// Schüler: Selbsteinschätzung
	await student.goto('/feedback');
	await student.getByRole('link', { name: /Halbjahres-Feedback/ }).click();
	await student.getByRole('button', { name: 'Abschicken' }).click();
	await expect(student.getByRole('alert')).toContainText('Bitte noch beantworten');
	await answer(student, {
		konzentration: '4',
		selbststaendig: '3',
		zusammenarbeit: '5',
		verstaendnis: '4',
		zufriedenheit: '★★★★'
	});
	await student.locator('[data-criterion="gelungen"] textarea').fill('Python-Aufgaben');
	await student.getByRole('button', { name: 'Abschicken' }).click();
	await expect(student.getByRole('status')).toContainText('Gespeichert');

	// Unterrichtsbewertung (anonym)
	await student.getByRole('tab', { name: 'Unterricht' }).click();
	await expect(student.getByText('Anonym:')).toBeVisible();
	await answer(student, {
		erklaerungen: '5',
		tempo: 'genau richtig',
		aufgaben: '4',
		empfehlung: 'Ja',
		gesamt: '★★★★★'
	});
	await student.locator('[data-criterion="hilfreich"]').getByLabel('Quiz-Runden').check();
	await student.getByRole('button', { name: 'Abschicken' }).click();
	await expect(student.getByRole('status')).toContainText('Gespeichert');

	await student.getByRole('tab', { name: 'Rückmeldung der Lehrkraft' }).click();
	await expect(student.getByText('noch nicht bewertet')).toBeVisible();

	// Lehrkraft bewertet und sieht die Selbsteinschätzung daneben
	await teacher.getByRole('tab', { name: 'Schüler bewerten' }).click();
	await expect(teacher.getByText('Selbsteinschätzung:').first()).toBeVisible();
	await answer(teacher, {
		konzentration: '5',
		selbststaendig: '4',
		zusammenarbeit: '5',
		verstaendnis: '3'
	});
	await teacher.locator('[data-criterion="tipps"] textarea').fill('Mehr eigene Projekte!');
	await teacher.getByRole('button', { name: 'Speichern', exact: true }).click();
	await expect(teacher.getByRole('status')).toContainText('gespeichert');

	await teacher.getByRole('tab', { name: 'Auswertung' }).click();
	await expect(teacher.getByText('Die Erklärungen waren verständlich.')).toBeVisible();
	await expect(teacher.getByText('1 ausgefüllt · anonym')).toBeVisible();

	// Schüler sieht die Rückmeldung (Sichtbarkeit: nach der Selbsteinschätzung)
	await student.reload();
	await student.getByRole('tab', { name: 'Rückmeldung der Lehrkraft' }).click();
	await expect(student.getByText('Mehr eigene Projekte!')).toBeVisible();
	await expect(student.getByText('Deine Einschätzung:').first()).toBeVisible();

	// PDF des eigenen Bogens und ZIP der ganzen Klasse
	const [pdf] = await Promise.all([
		student.waitForEvent('download'),
		student.getByTestId('evaluation-pdf').click()
	]);
	expect(pdf.suggestedFilename()).toMatch(/^Bewertung_Halbjahres-Feedback_.+\.pdf$/);
	const [zip] = await Promise.all([
		teacher.waitForEvent('download'),
		teacher.getByTestId('bundle-evaluation').getByRole('link', { name: 'ZIP' }).click()
	]);
	expect(zip.suggestedFilename()).toBe('Bewertungen_Halbjahres-Feedback.zip');
	const [all] = await Promise.all([
		teacher.waitForEvent('download'),
		teacher.getByTestId('bundle-evaluation').getByRole('link', { name: 'Eine PDF' }).click()
	]);
	expect(all.suggestedFilename()).toBe('Bewertungen_Halbjahres-Feedback.pdf');
	const printTab = teacher.getByTestId('bundle-evaluation').getByRole('link', { name: 'Drucken' });
	await expect(printTab).toHaveAttribute('href', /format=pdf&inline=true/);
	await expect(printTab).toHaveAttribute('target', '_blank');

	// Lehrkraft blendet ihre Bewertung aus
	await teacher.getByRole('tab', { name: 'Einstellungen' }).click();
	await teacher.getByLabel(/Nicht sichtbar/).check();
	await teacher.getByRole('button', { name: 'Speichern', exact: true }).click();
	await expect(teacher.getByRole('status')).toContainText('Gespeichert');
	await student.reload();
	await student.getByRole('tab', { name: 'Rückmeldung der Lehrkraft' }).click();
	await expect(student.getByText('nicht für dich freigegeben')).toBeVisible();
	await expect(student.getByText('Mehr eigene Projekte!')).toHaveCount(0);
});

test('Teilnahmebestätigungen und Mitnehm-Pakete eines Kurses als ZIP oder eine PDF', async ({
	browser
}) => {
	const courseName = `E2E Druck ${Date.now()}`;
	const { teacher, joinCode } = await courseWithModules(browser, courseName, []);
	await registerStudent(browser, joinCode, `druck${Date.now() % 100000}`);
	await teacher.goto('/admin/courses');
	const course = teacher.locator('article', { hasText: courseName });
	const [pdf] = await Promise.all([
		teacher.waitForEvent('download'),
		course.getByTestId('bundle-certificates').getByRole('link', { name: 'Eine PDF' }).click()
	]);
	expect(pdf.suggestedFilename()).toMatch(/^Teilnahmebestaetigungen_Kurs_\d+\.pdf$/);
	await course.getByTestId('bundle-take-home').getByLabel('beidseitig').check();
	const [zip] = await Promise.all([
		teacher.waitForEvent('download'),
		course.getByTestId('bundle-take-home').getByRole('link', { name: 'ZIP' }).click()
	]);
	expect(zip.suggestedFilename()).toMatch(/^Mitnehm-Pakete_Kurs_\d+\.zip$/);
	await expect(
		course.getByTestId('bundle-take-home').getByRole('link', { name: 'Eine PDF' })
	).toHaveAttribute('href', /format=pdf&duplex=true/);
});
