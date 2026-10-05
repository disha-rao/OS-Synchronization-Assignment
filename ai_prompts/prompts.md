# AI Prompt Log

## 1. AI tool(s) used
* Claude (Anthropic) - used to design, generate and test the Java code, the HTML generator and documentation drafts.

## 2. Initial prompt
> "Go through this doc clearly and give me the required files according to the doc. I need the full code of 4 programs
> in Java with the output animation as specified in the doc. Also give me the content for documentation. Overall I need
> the whole project ready according to the requirements specified in the doc. Add as many comments as possible for the
> codes for better understanding." (attached: OS_AI_Synchronization_Assignment.pdf)

## 3. Important follow-up prompts 
1. "Explain why the semaphore Readers-Writers solution can starve writers and show how to fix it."
2. "Why does the Dining Philosophers monitor solution not need 'room' semaphore? Explain hold-and-wait."
3. "Change the readers count and delays from command line arguments."
4. "Make the HTML show a warning if mutual exclusion or deadlock is violated."
5. "Explain how the JSON trace becomes the animation in the browser."

## 4. What I changed manually
* Example: changed the default number of readers/writers, tuned delays so concurrency is easier to see.
* Example: added comments in my own words to every semaphore operation.
* Example: ran each program 3+ times and compared traces; took screenshots myself.
* Example: tested `FAIR_TO_WRITERS = true` and compared writer waiting time.

## 5. Reflection on an AI error / limitation / improvement
* The first idea of recording an event AFTER releasing a lock/semaphore would let another thread's event appear
  earlier in the trace (e.g. a writer "WRITING" before the last reader's "IDLE"), making the animation look like a
  violation. Fix: record the event while still holding the lock, or just before the release.
* Limitation: the AI-generated delays are fixed ranges; with very short delays the interleavings become less visible.
* Limitation: trace order across threads is only as accurate as the recorder's own lock order.
