package dev.vdtool.frontend

/**
 * Reduces a user-supplied upload filename to something safe to interpolate into a
 * `Content-Disposition: attachment; filename="..."` header. The header grammar is
 * extremely permissive about quoted-string content, so the original filename can
 * smuggle in `"`, `\r\n`, or path separators if we trust it. We strip to a
 * conservative allowlist instead of trying to escape.
 *
 *  - Replaces any character outside `[A-Za-z0-9._-]` with `_`.
 *  - Truncates to 100 characters (defensive; real filenames are far shorter).
 *  - Returns "render" if the result would be empty or only dots.
 */
internal fun sanitizeAttachmentFilename(raw: String): String {
  val cleaned = buildString(raw.length.coerceAtMost(MAX_LEN)) {
    for (ch in raw) {
      if (length >= MAX_LEN) break
      append(if (ch in ALLOWED) ch else '_')
    }
  }
  return if (cleaned.none { it.isLetterOrDigit() }) FALLBACK else cleaned
}

private const val MAX_LEN = 100
private const val FALLBACK = "render"
private val ALLOWED: Set<Char> =
  (('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('.', '_', '-')).toSet()
