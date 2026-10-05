# Operating Systems Programming Assignment
## AI-Assisted Synchronization Algorithms with Program-Generated HTML Simulation
### Readers-Writers Problem and Dining Philosophers Problem (Java)

**Name:** <Disha S Rao>  **USN:** <NNM24IS076>  **Course:** Operating Systems  **GitHub:** <YOUR REPO URL>

## 1. Problem Statement and Synchronization Objective

When several threads (or processes) share data, the final result may depend on the exact order in which
they are scheduled. This is called a **race condition**. Synchronization primitives (semaphores, monitors)
force the threads to coordinate so that the shared data stays correct.

This assignment implements two classical problems, each with a semaphore solution and a monitor solution:

1. **Readers-Writers** - many readers may read at the same time, but a writer needs exclusive access.
2. **Dining Philosophers (4 philosophers)** - four philosophers share four forks; each needs two forks to eat.
   The solution must avoid **deadlock** and discuss **starvation**.

In addition, every program must generate an **HTML simulation from its own execution trace**.

Objectives: (a) mutual exclusion, (b) deadlock freedom, (c) starvation discussion, (d) real concurrency with
Java threads, (e) execution-based visualization.

## 2. Semaphore-Based Readers-Writers Solution

**File:** `readers_writers/semaphore/ReadersWritersSemaphore.java`

* **Shared resource:** `SharedResource` (an integer counter) and the variable `readCount`.
* **Critical section:** `resource.read()` (readers, shared) and `resource.slowIncrement()` (writers, exclusive).
* **Semaphores and initial values:**

| Semaphore | Initial value | Purpose |
|---|---|---|
| `mutex` | 1 | protects `readCount` |
| `resourceLock` | 1 | exclusive key of the resource; locked by one writer or by the whole group of readers |
| `turnstile` (optional) | 1 | stops new readers when a writer waits (only if `FAIR_TO_WRITERS = true`) |

* **Operations:** `acquire()` = wait/P (decrement, block if no permit), `release()` = signal/V (increment, wake a waiter).
* **Algorithm:** the *first* reader executes `resourceLock.acquire()` and the *last* reader executes
  `resourceLock.release()`. Readers in between enter freely. A writer simply does `acquire` ... write ... `release`.
* **Why it is correct:** a writer can only get `resourceLock` if no reader group holds it, and readers hold it
  as a group, therefore a writer never overlaps with readers or other writers.
* **Starvation:** this is the *readers-priority* version; continuous readers keep `readCount > 0` and a writer may wait forever.
  Setting `FAIR_TO_WRITERS = true` adds the turnstile, which removes this starvation.

## 3. Monitor-Based Readers-Writers Solution

**File:** `readers_writers/monitor/ReadersWritersMonitor.java`

A monitor groups shared data, the procedures operating on it, one lock (only one thread active inside) and
condition variables. Java is used with `ReentrantLock` + `Condition` as the monitor abstraction.

* **Monitor variables:** `activeReaders`, `writing`, `waitingWriters`.
* **Condition variables:** `okToRead` (readers wait while `writing || waitingWriters > 0`),
  `okToWrite` (writers wait while `writing || activeReaders > 0`).
* **Procedures:** `startRead`, `endRead`, `startWrite`, `endWrite`.
* `await()` is always inside a `while` loop (re-check the condition after waking up).
* **Policy:** writer priority; at the end of a write, the next waiting writer is signalled, otherwise all readers (`signalAll`).
* **Starvation:** writers cannot starve; readers could if writers arrive continuously.

## 4. Semaphore-Based Dining Philosophers Solution (4 philosophers)

**File:** `dining_philosophers/semaphore/DiningPhilosophersSemaphore.java`

* Philosopher *i* uses fork *i* (left) and fork *(i+1) mod 4* (right). Shared resources = the 4 forks.
* **Semaphores:** `fork[0..3]` initial value 1 each; `room` initial value **3** (N-1).
* **Deadlock:** if all four took their left fork at once, every one would wait for the right fork forever
  (circular wait). Because `room` lets at most 3 philosophers compete, at least one philosopher can always get both forks,
  so the circular wait cannot be completed (Coffman's *circular wait* condition is broken).
* **Starvation:** fair semaphores (FIFO queues) are used so no philosopher is overtaken forever; the program counts
  meals per philosopher and prints whether anybody starved.
* **States:** THINKING -> HUNGRY -> ACQUIRING -> EATING -> RELEASING -> THINKING.

## 5. Monitor-Based Dining Philosophers Solution (4 philosophers)

**File:** `dining_philosophers/monitor/DiningPhilosophersMonitor.java`

* **State array:** `state[i]` in {THINKING, HUNGRY, EATING}; one condition variable `self[i]` per philosopher.
* `pickUp(i)`: set HUNGRY, call `test(i)`, wait on `self[i]` until EATING.
* `test(i)`: if *i* is HUNGRY and neither neighbour is EATING -> set EATING and signal `self[i]`.
* `putDown(i)`: set THINKING and call `test` for both neighbours.
* **Deadlock:** both forks are given **atomically**, so a philosopher never holds one fork while waiting for another
  (*hold-and-wait* is broken).
* **Starvation:** possible in theory (two neighbours eating alternately can block philosopher *i*); a fix is a
  waiting queue or ticket numbers. In our runs every philosopher ate all meals.

## 6. How the Programs Record Events and Generate the HTML

1. Threads call `trace.state(actor, state, message[, value])` and `trace.fork(forkId, owner, message)`
   at each synchronization point. `TraceRecorder` is `synchronized`, adds a millisecond timestamp, prints to the
   console and stores each event as a JSON object.
2. **Ordering rule:** an event is recorded while the thread still holds the lock/semaphore (e.g. "READING" right after
   acquiring, "IDLE" right *before* releasing). Otherwise another thread's event could appear before it and the trace would
   look wrong.
3. When all threads have joined, `HtmlGenerator.write(...)` inserts the JSON array into an HTML template
   (`const TRACE = [...]`).
4. In the browser, JavaScript rebuilds the state by applying events `0..i` and redraws (readers/writers cards and
   resource box, or the round table with 4 philosophers and 4 forks). It provides Play/Pause/Step/Reset, speed,
   a scrub bar, a timeline per thread and a clickable log. It also checks the invariants (mutual exclusion / deadlock).
5. Because all data comes from the execution, running the program again produces a different (but valid) animation.

Pipeline: `Execute Program -> Perform Synchronization -> Capture State Changes -> Generate HTML -> View in Browser`.

## 7. Execution Traces (sample - replace/add with your own runs)

**Readers-Writers (monitor), 3 readers, 2 writers, 1 round, seed 7:**

```
[   302 ms] R3  WAITING    wants to read
[   303 ms] R3  READING    entered critical section, readers inside = 1
[   387 ms] W2  WAITING    wants to write
[   549 ms] R1  WAITING    wants to read          <- waits: a writer is already waiting (writer priority)
[   788 ms] R3  IDLE       finished reading value 0, readers left inside = 0
[   790 ms] W2  WRITING    entered critical section (exclusive access)
[  1291 ms] W2  IDLE       finished writing, new value = 1
[  1294 ms] W1  WRITING    entered critical section (exclusive access)
[  1855 ms] W1  IDLE       finished writing, new value = 2
[  1856 ms] R1  READING    entered critical section, readers inside = 1
[  1857 ms] R2  READING    entered critical section, readers inside = 2   <- readers share the resource
```

State sequence: `{R3 reading}` -> `{W2 waiting, R3 reading}` -> `{W2 writing}` -> `{W1 writing}` -> `{R1,R2 reading}`.
Final check printed by the program: `Final shared value = 2 (expected 2) -> CORRECT`.

**Readers-Writers (semaphore), default run (excerpt):**

```
[  4450 ms] R2  IDLE       finished reading value 4, readers left inside = 0   (last reader -> resourceLock.release)
[  4450 ms] W2  WRITING    entered critical section (exclusive access)
[  5037 ms] W2  IDLE       finished writing, new value = 5
[  5038 ms] W1  WRITING    entered critical section (exclusive access)
Final shared value = 6 (expected 6) -> CORRECT: no lost update, mutual exclusion held
```

**Dining Philosophers (semaphore), 1 meal each, seed 7:**

```
[   656 ms] P1  ACQUIRING  entered the room, trying to take forks F1 and F2
[   668 ms] Fork F1 -> P1
[   712 ms] P0  ACQUIRING  entered the room (2nd philosopher in room), forks F0 and F1
[   713 ms] Fork F0 -> P0
[   797 ms] Fork F2 -> P1
[   799 ms] P3  ACQUIRING  entered the room (3rd philosopher in room)
[   800 ms] P1  EATING     has both forks F1 and F2
[   867 ms] P2  HUNGRY     waits OUTSIDE the room: room semaphore = 0   <- this is what prevents the deadlock
[  1414 ms] Fork F1 -> FREE, Fork F2 -> FREE (P1 puts the forks down), then P0 takes F1 and EATS
[  1416 ms] P2  ACQUIRING  enters the room as soon as P1 left
```

**Dining Philosophers (monitor), 1 meal each, seed 7:**

```
[   645 ms] P1  ACQUIRING  monitor grants both forks F1 and F2 at once
[   676 ms] P1  EATING
[   707 ms] P0  HUNGRY     asks for forks; neighbour P1 is eating -> waits on its condition variable
[   784 ms] P3  ACQUIRING  monitor grants F3 and F0 (P3's neighbours P2 and P0 are not eating)
[  1238 ms] P1  THINKING   after releasing; monitor calls test(P0) and test(P2)
```

## 8. Race Conditions, Mutual Exclusion, Deadlock and Starvation

* **Race condition:** `SharedResource.slowIncrement` does read -> sleep -> write. If two writers (or reader+writer)
  overlapped, both would read the same old value and an update would be lost. Our programs compare the final value with
  `writers x rounds`; the equality shows that no update was lost.
* **Mutual exclusion:** `resourceLock` / monitor flags `writing` and `activeReaders` ensure at most one writer and no reader
  together with a writer. In Dining Philosophers each fork semaphore (or the monitor state array) ensures a fork has one user.
  The HTML also verifies this and would show a red "violated" banner.
* **Deadlock:** Four conditions (mutual exclusion, hold-and-wait, no preemption, circular wait). Semaphore DP breaks
  circular wait (`room = 3`); monitor DP breaks hold-and-wait (atomic grant). Both programs terminate, which proves no deadlock in the runs.
  Readers-Writers cannot deadlock because only one lock ordering is used.
* **Starvation:** RW-semaphore (readers priority) can starve writers; RW-monitor (writer priority) can starve readers;
  DP-semaphore uses fair semaphores; DP-monitor can theoretically starve a philosopher. Meals per philosopher are printed to check.

## 9. Semaphore vs Monitor Comparison

| Aspect | Semaphore | Monitor |
|---|---|---|
| Abstraction level | Low level (counter + wait/signal) | High level (data + procedures + lock + conditions) |
| Mutual exclusion | Programmer must pair acquire/release correctly | Automatic: one thread inside at a time |
| Waiting for a condition | Encoded in counters/extra semaphores (harder to read) | Explicit `while(cond) await()` |
| Error risk | Forgetting a `release` / wrong order -> deadlock | Forgetting `signal` -> lost wake-up; `if` instead of `while` |
| Our RW code | 2-3 semaphores + `readCount` | 3 variables + 2 condition variables |
| Our DP code | `fork[]` + `room` | `state[]` + `self[]` conditions |
| Readability | Compact but subtle | Clearer intent |

## 10. Screenshots

Insert here (your own screenshots, also in `screenshots/`):
1. Terminal output of each of the 4 programs.
2. HTML simulation of each program (the project already contains a sample screenshot for each).
3. A screenshot after you change a parameter (e.g. 6 readers, 3 writers).

## 11. AI Usage Summary

* **Tool(s):** Claude (Anthropic) <add others>.
* **Used for:** designing the architecture, generating the Java code, the HTML generator, and drafting documentation.
* **My verification:** compiled, ran every program multiple times, checked the final-value test, inspected generated HTML, <add your own>.
* **Error/limitation found:** see `ai_prompts/prompts.md` section 5.
* Full prompt log: `ai_prompts/prompts.md`.

## 12. References
See `references/references.md` (Silberschatz et al.; Tanenbaum; Downey; Dijkstra; Hoare; Oracle Java docs; MDN).

## 13. Academic Integrity Declaration

"I declare that I have personally tested and understood the submitted programs. I have disclosed the AI tools and
external resources used in developing this assignment. I can explain the synchronization logic, generated HTML
simulation, and modifications made to the code. I have not knowingly submitted another student's work or
unacknowledged third-party material as my own."

Signature / Name / USN / Date: ______________________
