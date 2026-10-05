import java.util.Random;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * =====================================================================
 *  PROGRAM 4 : DINING PHILOSOPHERS (4 PHILOSOPHERS) - MONITOR BASED
 * =====================================================================
 *
 * IDEA (Dijkstra's monitor solution):
 *   Instead of locking forks one by one, a philosopher asks the MONITOR for
 *   BOTH forks at once.  The monitor lets him eat only if NEITHER neighbour
 *   is eating.  Because the two forks are obtained ATOMICALLY (all-or-nothing)
 *   a philosopher never holds ONE fork while waiting for the other
 *   -> the "hold and wait" Coffman condition is broken -> NO DEADLOCK.
 *
 * MONITOR = ReentrantLock + Condition variables (Java's equivalent of a monitor
 *           with named condition variables).
 *
 * MONITOR STATE (protected by 'lock'):
 *   state[i]   - THINKING / HUNGRY / EATING for philosopher i
 *   self[i]    - condition variable on which philosopher i sleeps while he
 *                is HUNGRY but cannot eat yet
 *
 * MONITOR PROCEDURES:
 *   pickUp(i)  - state[i]=HUNGRY; test(i); if still not EATING -> self[i].await()
 *   putDown(i) - state[i]=THINKING; test(left neighbour); test(right neighbour)
 *   test(i)    - if i is HUNGRY and both neighbours are not EATING:
 *                   state[i]=EATING; self[i].signal()   (grant forks to i)
 *
 * FORKS: fork i is between philosopher (i-1) and philosopher i.
 *        Philosopher i uses fork i (left) and fork (i+1)%4 (right).
 *        When test() lets philosopher i eat we record that he owns both forks;
 *        putDown() records that both forks are free again.
 *
 * CRITICAL SECTION / SHARED RESOURCE: the forks (eating phase). The monitor lock
 *   protects the state[] array, and the rule "no two neighbours eat together"
 *   guarantees that a fork is never used by two philosophers.
 *
 * STARVATION CONSIDERATION:
 *   This solution is deadlock-free but NOT starvation-free in theory: if the two
 *   neighbours of philosopher i keep taking turns eating, at least one of them is
 *   always EATING and i could wait forever.  In practice (random delays) it
 *   rarely happens; the program prints meals per philosopher to verify this.
 *   A fix would be to give priority to the philosopher who has waited longest
 *   (e.g. a queue or ticket number).
 *
 * NOTE ON THE "RELEASING" STATE:
 *   Everything inside the monitor is instantaneous, so RELEASING appears in the
 *   log and visual but with zero duration in the timeline (that is expected).
 *
 * RUN:  java DiningPhilosophersMonitor [meals] [seed] [outputHtml]
 */
public class DiningPhilosophersMonitor {

    static final int N = 4;                                   // number of philosophers
    static final TraceRecorder trace = new TraceRecorder();   // events for console + HTML
    static final int[] mealsEaten = new int[N];               // meals per philosopher

    // =====================================================================
    //  THE MONITOR
    // =====================================================================
    static class TableMonitor {
        /** Possible philosopher states inside the monitor. */
        enum S { THINKING, HUNGRY, EATING }

        private final ReentrantLock lock = new ReentrantLock(true);  // the monitor lock
        private final S[] state = new S[N];                          // state of every philosopher
        private final Condition[] self = new Condition[N];           // one condition variable each

        TableMonitor() {
            for (int i = 0; i < N; i++) {
                state[i] = S.THINKING;
                self[i] = lock.newCondition();
            }
        }

        private int leftOf(int i)  { return (i + N - 1) % N; }   // neighbour on the left side
        private int rightOf(int i) { return (i + 1) % N; }       // neighbour on the right side

        /**
         * test(i): called ONLY while holding the monitor lock.
         * If philosopher i is hungry and no neighbour is eating, let him eat.
         */
        private void test(int i) {
            if (state[i] == S.HUNGRY
                    && state[leftOf(i)] != S.EATING
                    && state[rightOf(i)] != S.EATING) {

                state[i] = S.EATING;                       // grant BOTH forks atomically
                int fl = i, fr = (i + 1) % N;              // fork indices used by philosopher i
                String name = "P" + i;

                trace.state(name, "ACQUIRING", "monitor grants both forks F" + fl + " and F" + fr + " at once");
                trace.fork(fl, i, name + " owns left fork F" + fl);
                trace.fork(fr, i, name + " owns right fork F" + fr);
                trace.state(name, "EATING", "has both forks F" + fl + " and F" + fr + " and eats");

                self[i].signal();                          // wake philosopher i if he is sleeping
            }
        }

        /** Philosopher i wants to eat. Blocks until both forks are granted. */
        void pickUp(int i) throws InterruptedException {
            lock.lock();                                   // ENTER monitor
            try {
                state[i] = S.HUNGRY;
                trace.state("P" + i, "HUNGRY", "is hungry, asks the monitor for both forks");
                test(i);                                   // maybe we can eat immediately
                while (state[i] != S.EATING) {             // otherwise wait on our condition variable
                    self[i].await();                       // releases the lock while sleeping
                }
            } finally {
                lock.unlock();                             // LEAVE monitor
            }
        }

        /** Philosopher i finished eating: give back the forks and help the neighbours. */
        void putDown(int i) {
            lock.lock();
            try {
                String name = "P" + i;
                int fl = i, fr = (i + 1) % N;
                trace.state(name, "RELEASING", "finished eating, putting forks down");
                state[i] = S.THINKING;
                trace.fork(fl, -1, name + " released left fork F" + fl);
                trace.fork(fr, -1, name + " released right fork F" + fr);
                trace.state(name, "THINKING", "back to thinking");
                test(leftOf(i));                           // maybe the left neighbour can eat now
                test(rightOf(i));                          // maybe the right neighbour can eat now
            } finally {
                lock.unlock();
            }
        }
    }

    // =====================================================================
    //  PHILOSOPHER THREAD
    // =====================================================================
    static class Philosopher extends Thread {
        private final int id;
        private final int meals;
        private final TableMonitor table;
        private final Random rnd;

        Philosopher(int id, int meals, TableMonitor table, long seed) {
            this.id = id;
            this.meals = meals;
            this.table = table;
            this.rnd = new Random(seed + id);
        }

        @Override
        public void run() {
            try {
                trace.state("P" + id, "THINKING", "sits down and starts thinking");
                for (int m = 1; m <= meals; m++) {
                    Thread.sleep(200 + rnd.nextInt(600));   // THINK (non-critical)
                    table.pickUp(id);                        // ENTRY: may block until forks are granted
                    Thread.sleep(400 + rnd.nextInt(400));   // EAT (critical section, forks are ours)
                    mealsEaten[id]++;
                    table.putDown(id);                       // EXIT: release forks, wake neighbours
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
        int meals  = args.length > 0 ? Integer.parseInt(args[0]) : 3;
        long seed  = args.length > 1 ? Long.parseLong(args[1]) : 42L;
        String out = args.length > 2 ? args[2] : "generated_html/dining_philosophers_monitor.html";

        System.out.println("=== Dining Philosophers (MONITOR) : philosophers=" + N
                + " meals each=" + meals + " seed=" + seed + " ===");

        TableMonitor table = new TableMonitor();
        Philosopher[] p = new Philosopher[N];
        for (int i = 0; i < N; i++) p[i] = new Philosopher(i, meals, table, seed);

        for (Philosopher t : p) t.start();   // run concurrently
        for (Philosopher t : p) t.join();    // wait for all => proves no deadlock

        System.out.println("\nProgram finished => no deadlock occurred.");
        for (int i = 0; i < N; i++)
            System.out.println("  P" + i + " ate " + mealsEaten[i] + " time(s)"
                    + (mealsEaten[i] == meals ? "  (no starvation)" : "  (STARVED?)"));

        String config = "{\"philosophers\":" + N + ",\"meals\":" + meals + "}";
        HtmlGenerator.write(out, "DP",
                "Dining Philosophers (4) - Monitor Solution",
                "Monitor = ReentrantLock + condition variable per philosopher; both forks granted atomically",
                trace.toJson(), config);
    }
}
