class PathFocusStore {
	active = $state(false);

	set(value: boolean) {
		this.active = value;
	}
}

export const pathFocus = new PathFocusStore();
