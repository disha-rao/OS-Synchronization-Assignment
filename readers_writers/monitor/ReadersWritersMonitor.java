import java.util.Random;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * =====================================================================
 *  PROGRAM 2 : READERS-WRITERS PROBLEM  -  MONITOR BASED
 * =====================================================================
 *
 * WHAT IS A MONITOR?
 *   A monitor = shared data + the procedures that use it + ONE lock that
 *   guarantees that only ONE thread is executing inside the monitor at a time
 *   + CONDITION VARIABLES on which threads wait until a condition is true.
 *
 *   Java has a native monitor (synchronized / wait / notify), but to have
 *   separate, named condition variables we use the equivalent abstraction:
 *       ReentrantLock  -> the monitor lock (mutual exclusion)
 *       Condition      -> condition variables (await() = wait, signal() = notify)
 *
 * MONITOR STATE (all protected by 'lock'):
 *   activeReaders  - how many readers are reading right now
 *   writing        - true while a writer is writing
 *   waitingWriters - how many writers are waiting (used to give writers priority)
 *
 * CONDITION VARIABLES:
 *   okToRead  - readers wait here while (writing || waitingWriters > 0)
 *   okToWrite - writers wait here while (writing || activeReaders > 0)
 *
 * MONITOR PROCEDURES:
 *   startRead()  / endRead()   - called by readers before / after reading
 *   startWrite() / endWrite()  - called by writers before / after writing
 *
 * IMPORTANT: await() is always called inside a  while(condition)  loop, never
 *   inside an 'if'.  After waking up the condition must be tested again
 *   (another thread may have changed the state; spurious wake-ups are possible).
 *
 * CRITICAL SECTION: the actual read()/slowIncrement() which happens OUTSIDE the
 *   monitor procedures but is only reachable after startRead()/startWrite()
 *   returned (so the monitor decides who may enter).
 *
 * POLICY: WRITER PRIORITY
 *   New readers wait if a writer is waiting.  Consequence:
 *     + writers cannot starve,
 *     - readers could starve if writers arrive continuously.
 *   (The semaphore version of Program 1 has the opposite default - reader priority.)
 *
 * DEADLOCK: impossible - there is only one lock, no thread waits while holding
 *   a resource another thread needs, and every state change signals waiters.
 *
 * RUN:  java ReadersWritersMonitor [readers] [writers] [rounds] [seed] [outputHtml]
 */
public class ReadersWritersMonitor {

    /** Records events for console + HTML. */
    static final TraceRecorder trace = new TraceRecorder();
    /** THE shared resource. */
    static final SharedResource resource = new SharedResource();

    // =====================================================================
    //  THE MONITOR
    // =====================================================================
    static class ReadWriteMonitor {
        private final ReentrantLock lock = new ReentrantLock(true);   // monitor lock (fair = FIFO)
        private final Condition okToRead  = lock.newCondition();      // condition variable for readers
        private final Condition okToWrite = lock.newCondition();      // condition variable for writers

        private int activeReaders = 0;      // readers currently reading
        private boolean writing = false;    // is a writer currently writing?
        private int waitingWriters = 0;     // writers that want to write

        /** Called by a reader BEFORE reading. Blocks until reading is allowed. */
        void startRead(String name) throws InterruptedException {
            lock.lock();                                    // ENTER monitor
            try {
                trace.state(name, "WAITING", "wants to read");
                // wait while a writer is active OR a writer is waiting (writer priority)
                while (writing || waitingWriters > 0) {
                    okToRead.await();                       // releases the lock while waiting
                }
                activeReaders++;                            // now allowed to read
                trace.state(name, "READING", "entered critical section, readers inside = " + activeReaders,
                        resource.read());
            } finally {
                lock.unlock();                              // LEAVE monitor
            }
        }

        /** Called by a reader AFTER reading. */
        void endRead(String name, int seen) {
            lock.lock();
            try {
                activeReaders--;
                trace.state(name, "IDLE", "finished reading value " + seen + ", readers left inside = " + activeReaders);
                if (activeReaders == 0) {
                    okToWrite.signal();                     // last reader out -> wake ONE writer
                }
            } finally {
                lock.unlock();
            }
        }

        /** Called by a writer BEFORE writing. Blocks until exclusive access is possible. */
        void startWrite(String name) throws InterruptedException {
            lock.lock();
            try {
                waitingWriters++;                           // announce: a writer is waiting
                trace.state(name, "WAITING", "wants to write");
                while (writing || activeReaders > 0) {      // not allowed while anybody is inside
                    okToWrite.await();
                }
                waitingWriters--;
                writing = true;                             // we now own the resource exclusively
                trace.state(name, "WRITING", "entered critical section (exclusive access)", resource.read());
            } finally {
                lock.unlock();
            }
        }

        /** Called by a writer AFTER writing. */
        void endWrite(String name, int newValue) {
            lock.lock();
            try {
                writing = false;
                trace.state(name, "IDLE", "finished writing, new value = " + newValue, newValue);
                if (waitingWriters > 0) {
                    okToWrite.signal();                     // writer priority: next writer first
                } else {
                    okToRead.signalAll();                   // otherwise wake ALL waiting readers
                }
            } finally {
                lock.unlock();
            }
        }
    }

    /** The one monitor object shared by all threads. */
    static final ReadWriteMonitor monitor = new ReadWriteMonitor();

    // =====================================================================
    //  READER THREAD
    // =====================================================================
    static class Reader extends Thread {
        private final String name;
        private final int rounds;
        private final Random rnd;

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
                    Thread.sleep(100 + rnd.nextInt(500));        // non-critical work
                    monitor.startRead(name);                     // ENTRY (may block)
                    int seen = resource.read();                  // CRITICAL SECTION (shared)
                    Thread.sleep(300 + rnd.nextInt(300));        // time spent reading
                    monitor.endRead(name, seen);                 // EXIT
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
        private final String name;
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
                    Thread.sleep(200 + rnd.nextInt(700));        // non-critical work
                    monitor.startWrite(name);                    // ENTRY (may block)
                    int newValue = resource.slowIncrement(400 + rnd.nextInt(300)); // CRITICAL SECTION (exclusive)
                    monitor.endWrite(name, newValue);            // EXIT
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // =====================================================================
    //  MAIN
    // =====================================================================
    public static void main(String[] args) throws Exception {
        int readers = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int writers = args.length > 1 ? Integer.parseInt(args[1]) : 2;
        int rounds  = args.length > 2 ? Integer.parseInt(args[2]) : 3;
        long seed   = args.length > 3 ? Long.parseLong(args[3]) : 42L;
        String out  = args.length > 4 ? args[4] : "generated_html/readers_writers_monitor.html";

        System.out.println("=== Readers-Writers (MONITOR) : readers=" + readers
                + " writers=" + writers + " rounds=" + rounds + " seed=" + seed + " ===");

        Thread[] all = new Thread[readers + writers];
        for (int i = 0; i < readers; i++) all[i] = new Reader(i + 1, rounds, seed);
        for (int i = 0; i < writers; i++) all[readers + i] = new Writer(i + 1, rounds, seed);

        for (Thread t : all) t.start();     // run concurrently
        for (Thread t : all) t.join();      // wait for all

        int expected = writers * rounds;
        int actual = resource.read();
        System.out.println("\nFinal shared value = " + actual + " (expected " + expected + ") -> "
                + (actual == expected ? "CORRECT: no lost update, mutual exclusion held" : "ERROR: race condition!"));

        String config = "{\"readers\":" + readers + ",\"writers\":" + writers + ",\"rounds\":" + rounds + "}";
        HtmlGenerator.write(out, "RW",
                "Readers-Writers - Monitor Solution",
                "Monitor = ReentrantLock + Condition variables (okToRead, okToWrite), writer priority | "
                        + readers + " readers, " + writers + " writers",
                trace.toJson(), config);
    }
}
