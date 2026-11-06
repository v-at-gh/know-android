package com.example.quizapp.data.qap

/**
 * Custom exception thrown when parsing a malformed or invalid QAP manifest.
 *
 * @property message Detailed description of the parsing error.
 * @property cause The underlying exception/cause, if any.
 */
class QapManifestException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
