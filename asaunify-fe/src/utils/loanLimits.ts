// Loan matrix bounds — mirrors LoanMatrixService.java on the backend, which
// stays authoritative. Duplicated here only so the form can reject an
// out-of-range amount before it reaches the API and persists a draft.
export const LOAN_LIMITS = {
  SILVER_MIN: 2_000_001,
  SILVER_MAX: 5_000_000,
  GOLD_MIN: 5_000_001,
  GOLD_MAX: 10_000_000,
  TOP_UP_MAX: 1_500_000,
} as const;
