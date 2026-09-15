// Shared input validation for auth forms.
//
// The backend is authoritative — every rule here is also enforced server-side
// by Jakarta Bean Validation on the request DTOs. These checks exist only to
// give immediate feedback and to avoid pointless round trips; nothing here is
// a security control on its own.

// Deliberately pragmatic rather than RFC-exhaustive: one @, something either
// side, no whitespace, and a dot-separated domain. Matching the full RFC 5322
// grammar in a regex rejects almost nothing extra and accepts some genuinely
// broken input. Never tries to check that the mailbox actually exists.
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

/** Canonical form — mirrors EmailNormalizer.java on the backend. */
export function normalizeEmail(email: string): string {
  return email.trim().toLowerCase();
}

export function isValidEmail(email: string): boolean {
  const normalized = normalizeEmail(email);
  return normalized.length > 0
    && normalized.length <= 254
    && EMAIL_PATTERN.test(normalized);
}

/** Returns an error message, or null when the address is acceptable. */
export function validateEmail(email: string): string | null {
  if (!email.trim()) {
    return "Please enter your email address";
  }
  if (!isValidEmail(email)) {
    return "Please enter a valid email address";
  }
  return null;
}

// Mirrors the @Size/@Pattern rules on CreateUserRequest and
// ResetPasswordRequest. Kept in step with them by hand — the backend rejects
// anything that slips through.
export function validatePassword(password: string): string | null {
  if (password.length < 6 || password.length > 128) {
    return "Password must be between 6 and 128 characters";
  }
  if (!/[a-z]/.test(password)) {
    return "Password must include a lowercase letter";
  }
  if (!/[A-Z]/.test(password)) {
    return "Password must include an uppercase letter";
  }
  if (!/\d/.test(password)) {
    return "Password must include a number";
  }
  if (!/[^A-Za-z0-9]/.test(password)) {
    return "Password must include a special character";
  }
  return null;
}

export function validateResetCode(code: string): string | null {
  if (!/^\d{6}$/.test(code.trim())) {
    return "Enter the 6-digit code from your email";
  }
  return null;
}
