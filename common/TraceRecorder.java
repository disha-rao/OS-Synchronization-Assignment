import java.util.ArrayList;
import java.util.List;

/**
 * TraceRecorder
 * ---------------------------------------------------------------
 * PURPOSE:
 *   Every synchronization program (semaphore / monitor) calls this class
 *   each time something interesting happens (a thread starts waiting,
 *   enters the critical section, picks up a fork, leaves, ...).
 *
 *   The recorded events are:
 *     1. printed on the console (so you get textual execution evidence), and
 *     2. converted into a JSON array which HtmlGenerator embeds inside the
 *        generated HTML file.  The HTML animation is therefore driven ONLY by
 *        real execution data -> it is NOT a hand-made animation.
 *
 * THREAD SAFETY:
 *   Many threads call record methods at the same time, so every public
 *   method is 'synchronized' (one thread at a time).  This guarantees that
 *   the order of events in the list is a valid global order of what happened.
 *
 * IMPORTANT DESIGN RULE (used by all 4 programs):
 *   An event is recorded while the thread still HOLDS the lock/semaphore that
 *   makes that event true (e.g. "READING" is recorded just after acquiring,
 *   "IDLE/left" is recorded just BEFORE releasing).  This keeps the trace
 *   consistent: no other thread can slip a conflicting event in between.
 */
public class TraceRecorder {

    /** Time at which the recorder was created; all event times are relative to it. */
    private final long startNanos = System.nanoTime();

    /** Each element is one event already formatted as a JSON object string. */
    private final List<String> events = new ArrayList<>();

    /** Last timestamp handed out; used so time never goes backwards. */
    private long lastTimestampMs = 0;

    /** Marker meaning "this event carries no numeric value". */
    public static final int NO_VALUE = Integer.MIN_VALUE;

    /** Milliseconds since the program started (monotonic, never decreasing). */
    private long nowMs() {
        long t = (System.nanoTime() - startNanos) / 1_000_000L;
        if (t < lastTimestampMs) t = lastTimestampMs;   // safety: keep order
        lastTimestampMs = t;
        return t;
    }

    /** Record a state change of a thread WITHOUT a numeric value. */
    public synchronized void state(String actor, String state, String message) {
        state(actor, state, message, NO_VALUE);
    }

    /**
     * Record a state change of a thread (reader/writer/philosopher).
     *
     * @param actor   name of the thread, e.g. "R1", "W2", "P3"
     * @param state   new state, e.g. "WAITING", "READING", "EATING"
     * @param message human readable explanation shown in the log
     * @param value   current value of the shared resource (or NO_VALUE)
     */
    public synchronized void state(String actor, String state, String message, int value) {
        long t = nowMs();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"t\":").append(t)
          .append(",\"type\":\"state\"")
          .append(",\"actor\":\"").append(escape(actor)).append("\"")
          .append(",\"state\":\"").append(escape(state)).append("\"")
          .append(",\"msg\":\"").append(escape(message)).append("\"");
        if (value != NO_VALUE) sb.append(",\"value\":").append(value);
        sb.append("}");
        events.add(sb.toString());
        System.out.printf("[%6d ms] %-3s %-10s %s%n", t, actor, state, message);
    }

    /**
     * Record fork (chopstick) ownership change - used by Dining Philosophers.
     *
     * @param forkId id of the fork (0..3)
     * @param owner  philosopher id that now holds the fork, or -1 if the fork is free
     */
    public synchronized void fork(int forkId, int owner, String message) {
        long t = nowMs();
        events.add("{\"t\":" + t + ",\"type\":\"fork\",\"fork\":" + forkId
                + ",\"owner\":" + owner + ",\"msg\":\"" + escape(message) + "\"}");
        System.out.printf("[%6d ms] Fork F%d -> %s  (%s)%n", t, forkId,
                owner < 0 ? "FREE" : "P" + owner, message);
    }

    /** Return all events as one JSON array string: [ {...}, {...}, ... ] */
    public synchronized String toJson() {
        return "[" + String.join(",", events) + "]";
    }

    /** Number of recorded events. */
    public synchronized int size() { return events.size(); }

    /** Minimal JSON string escaping (quotes and backslashes). */
    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "'");
    }
}
