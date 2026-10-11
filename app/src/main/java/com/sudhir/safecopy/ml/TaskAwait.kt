package com.sudhir.safecopy.ml

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Suspends until an ML Kit task finishes. Small enough to write here rather than add
 * kotlinx-coroutines-play-services. ML Kit tasks can't be cancelled; on cancellation the
 * result is simply ignored.
 */
internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { if (cont.isActive) cont.resume(it) }
    addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
