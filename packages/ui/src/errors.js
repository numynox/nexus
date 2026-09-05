/**
 * Turn anything thrown into something worth showing a person.
 *
 * Supabase rejects with a plain object — `{ message, details, hint, code }` —
 * not an `Error`, so the usual
 * `e instanceof Error ? e.message : String(e)` renders it as the literal string
 * "[object Object]", and `... : "Sign in failed."` throws the reason away
 * entirely. Both were happening before this existed.
 *
 * Lives here rather than in each app because the shared shell needs it too:
 * the sign-in panel is the first place a database error is ever seen.
 *
 * @param {unknown} error
 * @returns {string}
 */
export function describeError(error) {
  if (error === null || error === undefined) return "Something went wrong.";
  if (typeof error === "string") return error.trim() || "Something went wrong.";

  if (error instanceof Error && error.message) return error.message;

  if (typeof error === "object") {
    const candidate = /** @type {Record<string, unknown>} */ (error);

    // message: PostgrestError and AuthError. error_description/error: GoTrue.
    const message = [
      candidate.message,
      candidate.error_description,
      candidate.error,
      candidate.msg,
    ].find((value) => typeof value === "string" && value.trim() !== "");

    if (message) {
      // details and hint are where Postgres says what to do about it.
      const extras = [candidate.details, candidate.hint].filter(
        (value) => typeof value === "string" && value.trim() !== "",
      );

      return [message, ...extras].join(" — ");
    }

    try {
      const serialised = JSON.stringify(error);
      if (serialised && serialised !== "{}") return serialised;
    } catch {
      // Circular or otherwise unserialisable; fall through.
    }
  }

  return String(error);
}
