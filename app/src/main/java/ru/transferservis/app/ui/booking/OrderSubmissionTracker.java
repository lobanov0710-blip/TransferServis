package ru.transferservis.app.ui.booking;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/** Shared by booking ViewModels so an older response cannot replace newer order access. */
final class OrderSubmissionTracker {

    private final AtomicLong submissionSequence = new AtomicLong();
    private final AtomicLong latestSavedSubmission = new AtomicLong();

    interface ConditionalSave {
        boolean save(BooleanSupplier accepted);
    }

    long beginSubmission() {
        return submissionSequence.incrementAndGet();
    }

    // Called on the process-wide completion FIFO; the store evaluates accepted under its lock.
    boolean saveIfEligible(long submission, ConditionalSave persist) {
        boolean saved = persist.save(() -> submission >= latestSavedSubmission.get());
        if (saved) {
            latestSavedSubmission.accumulateAndGet(submission, Math::max);
        }
        return saved;
    }
}
