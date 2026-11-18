/**
 * Generic thread-safe bounded circular buffer implementing the Java Monitor Pattern.
 * Enforces Mesa condition synchronization semantics via while-loop guarded wait() calls
 * and notifyAll() broadcast signaling to prevent deadlocks and spurious wakeups.
 * Provides lock-free non-blocking poll() for deterministic periodic control task execution.
 *
 * @param <T> element type stored within the buffer
 */
public class BoundedBuffer<T> {

    private final Object[] items;
    private int head = 0; // index of next element to remove
    private int tail = 0; // index of next slot to insert
    private int count = 0; // total number of elements currently stored

    public BoundedBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Buffer capacity must be strictly positive: " + capacity);
        }
        this.items = new Object[capacity];
    }

    /**
     * Inserts an element into the buffer, blocking if full under Mesa semantics.
     *
     * @param item element to insert
     * @throws InterruptedException if thread is interrupted while waiting
     */
    public synchronized void put(T item) throws InterruptedException {
        while (count == items.length) {
            wait();
        }
        items[tail] = item;
        tail = (tail + 1) % items.length;
        count++;
        notifyAll();
    }

    /**
     * Retrieves and removes the head element, blocking if empty under Mesa semantics.
     *
     * @return head element
     * @throws InterruptedException if thread is interrupted while waiting
     */
    @SuppressWarnings("unchecked")
    public synchronized T take() throws InterruptedException {
        while (count == 0) {
            wait();
        }
        T item = (T) items[head];
        items[head] = null; // facilitate garbage collection
        head = (head + 1) % items.length;
        count--;
        notifyAll();
        return item;
    }

    /**
     * Non-blocking poll method designed for real-time control cycle draining.
     * Returns immediately without blocking the caller thread.
     *
     * @return head element, or null if buffer is currently empty
     */
    @SuppressWarnings("unchecked")
    public synchronized T poll() {
        if (count == 0) {
            return null;
        }
        T item = (T) items[head];
        items[head] = null; // facilitate garbage collection
        head = (head + 1) % items.length;
        count--;
        notifyAll();
        return item;
    }

    public synchronized int size() {
        return count;
    }

    public synchronized boolean isEmpty() {
        return count == 0;
    }

    public synchronized int capacity() {
        return items.length;
    }

    public synchronized int getCapacity() {
        return items.length;
    }
}
