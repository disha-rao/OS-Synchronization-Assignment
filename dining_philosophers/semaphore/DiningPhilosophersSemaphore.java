import java.util.Random;
import java.util.concurrent.Semaphore;

/**
 * =====================================================================
 *  PROGRAM 3 : DINING PHILOSOPHERS (4 PHILOSOPHERS) - SEMAPHORE BASED
 * =====================================================================
 *
 * PROBLEM:
 *   4 philosophers sit around a round table.  Between each pair of neighbours
 *   there is ONE fork (so 4 forks).  A philosopher alternates between
 *   THINKING and EATING, and needs BOTH the left and the right fork to eat.
 *
 *   Layout (fork i is the LEFT fork of philosopher i,
 *           fork (i+1) % 4 is the RIGHT fork of philosopher i):
 *
 *            P0
 *        F0      F1
 *     P3            P1
 *        F3      F2
 *            P2
 *
 * SHARED RESOURCES (critical resources): the 4 forks.
 *   A fork can be used by only one philosopher at a time -> mutual exclusion.
 *
 * SEMAPHORES USED (and INITIAL VALUES):
 *   fork[0..3] = Semaphore(1)   -> one binary semaphore per fork
 *                                  1 = fork available, 0 = fork in use.
 *   room       = Semaphore(3)   -> "table-entry ticket" counting semaphore.
 *                                  At most N-1 = 3 philosophers may try to pick up
 *                                  forks at the same time.
 *
 * WHY THE PROBLEM IS HARD - DEADLOCK:
 *   Naive solution: everybody picks up the left fork, then the right fork.
 *   If all 4 pick up their left fork at the same moment, every philosopher holds
 *   one fork and waits for the right one held by his neighbour -> CIRCULAR WAIT
 *   -> DEADLOCK (nobody ever eats).
 *
 * DEADLOCK PREVENTION USED HERE:  limit the number of competing philosophers to N-1.
 *   If only 3 philosophers are "at the table", then among 4 forks at least one
 *   philosopher can always get both forks (pigeonhole principle), so the circular
 *   wait can never be completed.  This breaks the "circular wait" Coffman condition.
 *
 * STARVATION CONSIDERATIONS:
 *   - All semaphores are created FAIR (second argument 'true'): waiting threads are
 *     served in FIFO order, so no philosopher can be overtaken forever.
 *   - The program counts meals per philosopher; the result is printed at the end so
 *     you can verify that every philosopher got to eat.
 *
 * STATES RECORDED FOR THE HTML:
 *   THINKING -> HUNGRY (waiting for table entry) -> ACQUIRING (picking up forks)
 *   -> EATING -> RELEASING (putting forks down) -> THINKING ...
 *
 * RUN:  java DiningPhilosophersSemaphore [meals] [seed] [outputHtml]
 */
public class DiningPhilosophersSemaphore {

    /** Number of philosophers (and forks). The assignment fixes this to 4. */
    static final int N = 4;

    /** One binary semaphore per fork. 'true' = fair (FIFO) ordering. */
    static final Semaphore[] fork = new Semaphore[N];

    /** Counting semaphore with N-1 permits: deadlock prevention. */
    static final Semaphore room = new Semaphore(N - 1, true);

    /** Event recorder for console + HTML. */
    static final TraceRecorder trace = new TraceRecorder();

    /** Meals eaten by each philosopher (each philosopher only writes its own cell). */
    static final int[] mealsEaten = new int[N];

    // =====================================================================
    //  PHILOSOPHER THREAD
    // =====================================================================
    static class Philosopher extends Thread {
        private final int id;           // 0..3
        private final String name;      // "P0".."P3"
        private final int meals;        // how many times he wants to eat
        private final Random rnd;

        Philosopher(int id, int meals, long seed) {
            this.id = id;
            this.name = "P" + id;
            this.meals = meals;
            this.rnd = new Random(seed + id);
        }

        @Override
        public void run() {
            int left = id;                 // index of the left fork
            int right = (id + 1) % N;      // index of the right fork
            try {
                trace.state(name, "THINKING", "sits down and starts thinking");
                for (int m = 1; m <= meals; m++) {

                    // ---------- THINK (non-critical) ----------
                    Thread.sleep(200 + rnd.nextInt(600));

                    // ---------- HUNGRY: wants to eat ----------
                    trace.state(name, "HUNGRY", "is hungry (meal " + m + "), waiting for a place at the table");
                    room.acquire();        // at most 3 philosophers pass; the 4th BLOCKS here (deadlock prevention)

                    // ---------- ACQUIRING FORKS ----------
                    trace.state(name, "ACQUIRING", "entered the room, trying to take forks F" + left + " and F" + right);

                    fork[left].acquire();                          // wait() on left fork
                    trace.fork(left, id, name + " picked up left fork F" + left);   // recorded while holding the fork

                    Thread.sleep(50 + rnd.nextInt(100));           // small pause: makes interleaving visible

                    fork[right].acquire();                         // wait() on right fork
                    trace.fork(right, id, name + " picked up right fork F" + right);

                    // ---------- EAT (critical section: uses both forks exclusively) ----------
                    trace.state(name, "EATING", "has both forks F" + left + " and F" + right + " and eats");
                    Thread.sleep(400 + rnd.nextInt(400));
                    mealsEaten[id]++;

                    // ---------- RELEASING FORKS ----------
                    trace.state(name, "RELEASING", "finished eating, putting forks down");
                    Thread.sleep(80);                              // short pause so RELEASING is visible

                    // record "fork is free" BEFORE signalling, so a neighbour's pick-up is logged after it
                    trace.fork(left, -1, name + " put down left fork F" + left);
                    fork[left].release();                          // signal() left fork
                    trace.fork(right, -1, name + " put down right fork F" + right);
                    fork[right].release();                         // signal() right fork

                    room.release();                                // leave the room: another philosopher may enter

                    trace.state(name, "THINKING", "back to thinking");
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
        int meals  = args.length > 0 ? Integer.parseInt(args[0]) : 3;       // meals per philosopher [3]
        long seed  = args.length > 1 ? Long.parseLong(args[1]) : 42L;       // random seed [42]
        String out = args.length > 2 ? args[2] : "generated_html/dining_philosophers_semaphore.html";

        System.out.println("=== Dining Philosophers (SEMAPHORE) : philosophers=" + N
                + " meals each=" + meals + " seed=" + seed + " ===");

        // initialise every fork semaphore with value 1 (fork is available)
        for (int i = 0; i < N; i++) fork[i] = new Semaphore(1, true);

        Philosopher[] p = new Philosopher[N];
        for (int i = 0; i < N; i++) p[i] = new Philosopher(i, meals, seed);

        for (Philosopher t : p) t.start();   // all philosophers run concurrently
        for (Philosopher t : p) t.join();    // wait until all are finished (=> no deadlock occurred)

        // ---------- starvation check ----------
        System.out.println("\nProgram finished => no deadlock occurred.");
        for (int i = 0; i < N; i++)
            System.out.println("  P" + i + " ate " + mealsEaten[i] + " time(s)"
                    + (mealsEaten[i] == meals ? "  (no starvation)" : "  (STARVED?)"));

        String config = "{\"philosophers\":" + N + ",\"meals\":" + meals + "}";
        HtmlGenerator.write(out, "DP",
                "Dining Philosophers (4) - Semaphore Solution",
                "Semaphores: fork[0..3] = 1 each, room = 3 (at most N-1 philosophers compete -> no deadlock)",
                trace.toJson(), config);
    }
}
