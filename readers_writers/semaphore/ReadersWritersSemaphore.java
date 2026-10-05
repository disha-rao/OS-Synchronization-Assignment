import java.util.Random;
import java.util.concurrent.Semaphore;

/**
 * =====================================================================
 *  PROGRAM 1 : READERS-WRITERS PROBLEM  -  SEMAPHORE BASED
 * =====================================================================
 *
 * PROBLEM:
 *   Many threads share ONE resource (SharedResource - an integer).
 *     - READERS only look at it  -> many readers may read AT THE SAME TIME.
 *     - WRITERS modify it        -> a writer needs EXCLUSIVE access
 *                                   (no reader and no other writer).
 *
 * CRITICAL SECTION  : the code that touches 'resource' (read() / slowIncrement()).
 * SHARED RESOURCE   : the object 'resource'  (+ the variable 'readCount').
 *
 * SEMAPHORES USED (and their INITIAL VALUES):
 *   mutex        = Semaphore(1)  -> protects the variable 'readCount'
 *                                   (a binary semaphore = a lock).
 *   resourceLock = Semaphore(1)  -> "room key" of the resource.
 *                                   Held by ONE writer, or by the GROUP of readers
 *                                   (the first reader locks it, the last reader unlocks it).
 *   turnstile    = Semaphore(1)  -> (only if FAIR_TO_WRITERS = true) a gate that stops
 *                                   new readers once a writer is waiting.
 *
 * OPERATIONS:
 *   acquire() = wait()/P()   -> decrement; if the value would go below 0 the thread BLOCKS.
 *   release() = signal()/V() -> increment; wakes up one blocked thread.
 *
 * ALGORITHM (classic "first reader locks, last reader unlocks"):
 *
 *   READER                              WRITER
 *   ------                              ------
 *   mutex.acquire()                     resourceLock.acquire()
 *   readCount++                         ... WRITE (exclusive) ...
 *   if readCount == 1                   resourceLock.release()
 *        resourceLock.acquire()
 *   mutex.release()
 *   ... READ (shared) ...
 *   mutex.acquire()
 *   readCount--
 *   if readCount == 0
 *        resourceLock.release()
 *   mutex.release()
 *
 * STARVATION NOTE:
 *   With the classic version (FAIR_TO_WRITERS = false) readers have priority:
 *   if readers keep arriving, readCount never drops to 0 and a WRITER can wait
 *   forever (writer starvation).  Setting FAIR_TO_WRITERS = true adds the
 *   'turnstile' semaphore so that a waiting writer blocks NEW readers, which
 *   removes writer starvation.
 *
 * DEADLOCK NOTE:
 *   No deadlock: every thread acquires the semaphores in the same order
 *   (turnstile -> mutex -> resourceLock) and never waits for something held by
 *   a thread that is waiting for it.
 *
 * HTML:
 *   Every state change is recorded by TraceRecorder and at the end
 *   HtmlGenerator writes generated_html/readers_writers_semaphore.html
 *
 * RUN:  java ReadersWritersSemaphore [readers] [writers] [rounds] [seed] [outputHtml]
 */
public class ReadersWritersSemaphore {

    // ---------------- configuration switches ----------------
    /** false = classic readers-priority (writer may starve), true = writer-friendly. */
    static final boolean FAIR_TO_WRITERS = false;

    // ---------------- semaphores ----------------
    /** Protects 'readCount'. Initial value 1 (binary semaphore / mutex). */
    static final Semaphore mutex = new Semaphore(1);
    /** Guards the shared resource itself. 'true' = FIFO fairness among waiting threads. */
    static final Semaphore resourceLock = new Semaphore(1, true);
    /** Gate used only for the writer-friendly variant. */
    static final Semaphore turnstile = new Semaphore(1, true);

    // ---------------- shared data ----------------
    /** Number of readers currently inside the critical section (guarded by 'mutex'). */
    static int readCount = 0;
    /** THE shared resource. */
    static final SharedResource resource = new SharedResource();
    /** Records every event for console output and for the HTML simulation. */
    static final TraceRecorder trace = new TraceRecorder();

    // =====================================================================
    //  READER THREAD
    // =====================================================================
    static class Reader extends Thread {
        private final String name;      // e.g. "R1"
        private final int rounds;       // how many times this reader reads
        private final Random rnd;       // per-thread random delays (seeded => reproducible logic)

        Reader(int id, int rounds, long seed) {
            this.name = "R" + id;
            this.rounds = rounds;
            this.rnd = new Random(seed + id);
        }

        @Override
        public void run() {
            try {
                trace.state(name, "IDLE", "reader created, doing other work");
                for (int i = 1; i <= rounds; i++) {

                    // ---- NON-critical section: think / do other work ----
                    Thread.sleep(100 + rnd.nextInt(500));

                    // ---- ENTRY SECTION ----
                    trace.state(name, "WAITING", "wants to read (round " + i + ")");

                    if (FAIR_TO_WRITERS) {          // pass through the gate (blocked if a writer is waiting)
                        turnstile.acquire();
                        turnstile.release();
                    }

                    mutex.acquire();                // lock 'readCount'
                    readCount++;                    // one more reader
                    if (readCount == 1) {
                        resourceLock.acquire();     // FIRST reader locks the resource for all readers
                    }
                    // we now hold access: record it while still holding 'mutex' (keeps trace ordered)
                    trace.state(name, "READING", "entered critical section, readers inside = " + readCount,
                            resource.read());
                    mutex.release();                // let other readers enter too (shared access)

                    // ---- CRITICAL SECTION (shared reading) ----
                    int seen = resource.read();
                    Thread.sleep(300 + rnd.nextInt(300));   // simulate time spent reading

                    // ---- EXIT SECTION ----
                    mutex.acquire();
                    readCount--;                    // this reader leaves
                    // record BEFORE releasing so the next writer's event comes after this one
                    trace.state(name, "IDLE", "finished reading value " + seen + ", readers left inside = " + readCount);
                    if (readCount == 0) {
                        resourceLock.release();     // LAST reader unlocks the resource for writers
                    }
                    mutex.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // =====================================================================
    //  WRITER THREAD
    // =====================================================================
    static class Writer extends Thread {
        private final String name;      // e.g. "W1"
        private final int rounds;
        private final Random rnd;

        Writer(int id, int rounds, long seed) {
            this.name = "W" + id;
            this.rounds = rounds;
            this.rnd = new Random(seed + 1000 + id);
        }

        @Override
        public void run() {
            try {
                trace.state(name, "IDLE", "writer created, doing other work");
                for (int i = 1; i <= rounds; i++) {

                    // ---- NON-critical section ----
                    Thread.sleep(200 + rnd.nextInt(700));

                    // ---- ENTRY SECTION ----
                    trace.state(name, "WAITING", "wants to write (round " + i + ")");

                    if (FAIR_TO_WRITERS) turnstile.acquire();   // close the gate: no NEW readers
                    resourceLock.acquire();                      // wait until no reader/writer is inside
                    if (FAIR_TO_WRITERS) turnstile.release();   // re-open the gate

                    // ---- CRITICAL SECTION (exclusive writing) ----
                    trace.state(name, "WRITING", "entered critical section (exclusive access)", resource.read());
                    int newValue = resource.slowIncrement(400 + rnd.nextInt(300));

                    // ---- EXIT SECTION ----
                    trace.state(name, "IDLE", "finished writing, new value = " + newValue, newValue);
                    resourceLock.release();                      // let readers / next writer in
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // =====================================================================
    //  MAIN: create threads, run, verify, generate HTML
    // =====================================================================
    public static void main(String[] args) throws Exception {
        // read optional command line arguments (defaults in brackets)
        int readers = args.length > 0 ? Integer.parseInt(args[0]) : 4;      // [4]
        int writers = args.length > 1 ? Integer.parseInt(args[1]) : 2;      // [2]
        int rounds  = args.length > 2 ? Integer.parseInt(args[2]) : 3;      // [3]
        long seed   = args.length > 3 ? Long.parseLong(args[3]) : 42L;      // [42]
        String out  = args.length > 4 ? args[4] : "generated_html/readers_writers_semaphore.html";

        System.out.println("=== Readers-Writers (SEMAPHORE) : readers=" + readers
                + " writers=" + writers + " rounds=" + rounds + " seed=" + seed
                + " fairToWriters=" + FAIR_TO_WRITERS + " ===");

        // create all threads
        Thread[] all = new Thread[readers + writers];
        for (int i = 0; i < readers; i++) all[i] = new Reader(i + 1, rounds, seed);
        for (int i = 0; i < writers; i++) all[readers + i] = new Writer(i + 1, rounds, seed);

        // start them all (they now run CONCURRENTLY)
        for (Thread t : all) t.start();
        // wait until every thread has finished
        for (Thread t : all) t.join();

        // ---- verification: no lost updates => mutual exclusion among writers worked ----
        int expected = writers * rounds;
        int actual = resource.read();
        System.out.println("\nFinal shared value = " + actual + " (expected " + expected + ") -> "
                + (actual == expected ? "CORRECT: no lost update, mutual exclusion held" : "ERROR: race condition!"));

        // ---- generate the HTML simulation from the recorded events ----
        String config = "{\"readers\":" + readers + ",\"writers\":" + writers + ",\"rounds\":" + rounds + "}";
        HtmlGenerator.write(out, "RW",
                "Readers-Writers - Semaphore Solution",
                "Semaphores: mutex(1), resourceLock(1)" + (FAIR_TO_WRITERS ? ", turnstile(1)" : "")
                        + " | " + readers + " readers, " + writers + " writers",
                trace.toJson(), config);
    }
}
