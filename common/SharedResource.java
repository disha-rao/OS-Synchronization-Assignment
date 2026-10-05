/**
 * SharedResource  (used by both Readers-Writers programs)
 * ---------------------------------------------------------------
 * This is THE shared resource (think: a database record / file).
 * It is just one integer counter.
 *
 * NOTICE: this class has NO synchronization of its own on purpose!
 *   All protection must come from the semaphore / monitor used by the caller.
 *   slowIncrement() is deliberately written as read -> sleep -> write.
 *   If two writers ever entered together (broken mutual exclusion) they would
 *   both read the same old value and one update would be LOST (race condition).
 *   At the end of each run we compare the final value with the expected value
 *   to PROVE that mutual exclusion worked.
 */
public class SharedResource {

    /** The shared data. Intentionally not volatile/synchronized. */
    private int value = 0;

    /** Reader operation: just look at the value. */
    public int read() {
        return value;
    }

    /**
     * Writer operation: value = value + 1, but slowed down with a sleep
     * between the read and the write to make a race condition very likely
     * IF synchronization were missing.
     *
     * @param sleepMs how long the "write" takes (simulated work)
     * @return the new value
     */
    public int slowIncrement(long sleepMs) throws InterruptedException {
        int old = value;          // step 1: read
        Thread.sleep(sleepMs);    // step 2: simulate slow work (window for a race)
        value = old + 1;          // step 3: write back
        return value;
    }
}
