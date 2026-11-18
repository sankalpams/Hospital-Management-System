import { toApiError } from '../api/client';

/**
 * Splits an API failure into per-field messages (shown under inputs) and a general message
 * (shown in an alert). `fieldMap` maps server field paths to form field names when they differ.
 */
export function splitApiError(error, fieldMap = {}) {
  const apiError = toApiError(error);
  const fields = {};
  (apiError.fieldErrors ?? []).forEach(({ field, message }) => {
    const key = fieldMap[field] ?? field.replace(/\[\d+\]$/, '');
    if (!fields[key]) fields[key] = message;
  });
  const general = Object.keys(fields).length > 0 && apiError.code === 'VALIDATION_FAILED' ? null : apiError;
  return { fields, general };
}

/** Required-field helper for client-side pre-validation (the server validates again). */
export function requireFields(values, labels) {
  const errors = {};
  Object.entries(labels).forEach(([key, label]) => {
    const v = values[key];
    if (v === undefined || v === null || (typeof v === 'string' && v.trim() === '') || (Array.isArray(v) && v.length === 0)) {
      errors[key] = `${label} is required.`;
    }
  });
  return errors;
}

/**
 * Moves focus to the first field marked invalid (MASTER.md §6). Deferred one tick so it runs
 * after React has rendered the new error state.
 */
export function focusFirstInvalid() {
  window.setTimeout(() => {
    document.querySelector('[aria-invalid="true"]')?.focus();
  }, 0);
}

/**
 * An in-app path is safe to navigate to: starts with one "/" and is not protocol-relative ("//x")
 * or backslash-tricked ("/\x"). Guards redirects even though today's targets come from app state
 * and the server (react-router GHSA-wrjc-x8rr-h8h6).
 */
export function safeAppPath(path, fallback = '/') {
  return typeof path === 'string' && /^\/(?!\/)/.test(path) && !path.includes('\\') ? path : fallback;
}
