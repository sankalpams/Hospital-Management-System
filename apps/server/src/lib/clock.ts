let override: Date | null = null;

export const clock = {
  now(): Date {
    return override ? new Date(override.getTime()) : new Date();
  },
};

/** Test hook: freeze the clock at the given instant. */
export function setNow(d: Date): void {
  override = new Date(d.getTime());
}

export function resetClock(): void {
  override = null;
}
