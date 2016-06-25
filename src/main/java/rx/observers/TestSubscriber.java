/**
 * Copyright 2014 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package rx.observers;

import java.util.*;
import java.util.concurrent.*;

import rx.*;
import rx.Observer;
import rx.annotations.Experimental;
import rx.exceptions.CompositeException;

/**
 * A {@code TestSubscriber} is a variety of {@link Subscriber} that you can use for unit testing, to perform
 * assertions, inspect received events, or wrap a mocked {@code Subscriber}.
 * @param <T> the value type
 */
public class TestSubscriber<T> extends Subscriber<T> {

    private final Observer<T> delegate;
    
    private final List<T> values;
    
    private final List<Throwable> errors;
    
    /** The number of onCompleted() calls. */
    private int completions;
    
    private final CountDownLatch latch = new CountDownLatch(1);
    
    /** Written after an onNext value has been added to the {@link #values} list. */
    private volatile int valueCount;
    
    private volatile Thread lastSeenThread;
    /** The shared no-op observer. */
    private static final Observer<Object> INERT = new Observer<Object>() {

        @Override
        public void onCompleted() {
            // do nothing
        }

        @Override
        public void onError(Throwable e) {
            // do nothing
        }

        @Override
        public void onNext(Object t) {
            // do nothing
        }

    };

    /**
     * Constructs a TestSubscriber with the initial request to be requested from upstream.
     *
     * @param initialRequest the initial request value, negative value will revert to the default unbounded behavior
     * @since 1.1.0
     */
    @SuppressWarnings("unchecked")
    public TestSubscriber(long initialRequest) {
        this((Observer<T>)INERT, initialRequest);
    }
    
    /**
     * Constructs a TestSubscriber with the initial request to be requested from upstream
     * and a delegate Observer to wrap.
     *
     * @param initialRequest the initial request value, negative value will revert to the default unbounded behavior
     * @param delegate the Observer instance to wrap
     * @throws NullPointerException if delegate is null
     * @since 1.1.0
     */
    public TestSubscriber(Observer<T> delegate, long initialRequest) {
        if (delegate == null) {
            throw new NullPointerException();
        }
        this.delegate = delegate;
        if (initialRequest >= 0L) {
            this.request(initialRequest);
        }
        
        this.values = new ArrayList<T>();
        this.errors = new ArrayList<Throwable>();
    }

    /**
     * Constructs a TestSubscriber which requests Long.MAX_VALUE and delegates events to
     * the given Subscriber.
     * @param delegate the subscriber to delegate to.
     * @throws NullPointerException if delegate is null
     * @since 1.1.0
     */
    public TestSubscriber(Subscriber<T> delegate) {
        this(delegate, -1);
    }

    /**
     * Constructs a TestSubscriber which requests Long.MAX_VALUE and delegates events to
     * the given Observer.
     * @param delegate the observer to delegate to.
     * @throws NullPointerException if delegate is null
     * @since 1.1.0
     */
    public TestSubscriber(Observer<T> delegate) {
        this(delegate, -1);
    }

    /**
     * Constructs a TestSubscriber with an initial request of Long.MAX_VALUE and no delegation.
     */
    public TestSubscriber() {
        this(-1);
    }

    /**
     * Factory method to construct a TestSubscriber with an initial request of Long.MAX_VALUE and no delegation.
     * @param <T> the value type
     * @return the created TestSubscriber instance
     * @since 1.1.0
     */
    public static <T> TestSubscriber<T> create() {
        return new TestSubscriber<T>();
    }
    
    /**
     * Factory method to construct a TestSubscriber with the given initial request amount and no delegation.
     * @param <T> the value type
     * @param initialRequest the initial request amount, negative values revert to the default unbounded mode
     * @return the created TestSubscriber instance
     * @since 1.1.0
     */
    public static <T> TestSubscriber<T> create(long initialRequest) {
        return new TestSubscriber<T>(initialRequest);
    }
    
    /**
     * Factory method to construct a TestSubscriber which delegates events to the given Observer and
     * issues the given initial request amount.
     * @param <T> the value type
     * @param delegate the observer to delegate events to
     * @param initialRequest the initial request amount, negative values revert to the default unbounded mode
     * @return the created TestSubscriber instance
     * @throws NullPointerException if delegate is null
     * @since 1.1.0
     */
    public static <T> TestSubscriber<T> create(Observer<T> delegate, long initialRequest) {
        return new TestSubscriber<T>(delegate, initialRequest);
    }

    /**
     * Factory method to construct a TestSubscriber which delegates events to the given Subscriber and
     * an issues an initial request of Long.MAX_VALUE.
     * @param <T> the value type
     * @param delegate the subscriber to delegate events to
     * @return the created TestSubscriber instance
     * @throws NullPointerException if delegate is null
     * @since 1.1.0
     */
    public static <T> TestSubscriber<T> create(Subscriber<T> delegate) {
        return new TestSubscriber<T>(delegate);
    }
    
    /**
     * Factory method to construct a TestSubscriber which delegates events to the given Observer and
     * an issues an initial request of Long.MAX_VALUE.
     * @param <T> the value type
     * @param delegate the observer to delegate events to
     * @return the created TestSubscriber instance
     * @throws NullPointerException if delegate is null
     * @since 1.1.0
     */
    public static <T> TestSubscriber<T> create(Observer<T> delegate) {
        return new TestSubscriber<T>(delegate);
    }
    
    /**
     * Notifies the Subscriber that the {@code Observable} has finished sending push-based notifications.
     * <p>
     * The {@code Observable} will not call this method if it calls {@link #onError}.
     */
    @Override
    public void onCompleted() {
        try {
            completions++;
            lastSeenThread = Thread.currentThread();
            delegate.onCompleted();
        } finally {
            latch.countDown();
        }
    }

    /**
     * Returns the {@link Notification}s representing each time this {@link Subscriber} was notified of sequence
     * completion via {@link #onCompleted}, as a {@link List}.
     *
     * @return a list of Notifications representing calls to this Subscriber's {@link #onCompleted} method
     * 
     * @deprecated use {@link #getCompletions()} instead.
     */
    @Deprecated
    public List<Notification<T>> getOnCompletedEvents() {
        int c = completions;
        List<Notification<T>> result = new ArrayList<Notification<T>>(c != 0 ? c : 1);
        for (int i = 0; i < c; i++) {
            result.add(Notification.<T>createOnCompleted());
        }
        return result;
    }
    
    /**
     * Returns the number of times onCompleted was called on this TestSubscriber.
     * @return the number of times onCompleted was called on this TestSubscriber.
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @Experimental
    public final int getCompletions() {
        return completions;
    }

    /**
     * Notifies the Subscriber that the {@code Observable} has experienced an error condition.
     * <p>
     * If the {@code Observable} calls this method, it will not thereafter call {@link #onNext} or
     * {@link #onCompleted}.
     * 
     * @param e
     *          the exception encountered by the Observable
     */
    @Override
    public void onError(Throwable e) {
        try {
            lastSeenThread = Thread.currentThread();
            errors.add(e);
            delegate.onError(e);
        } finally {
            latch.countDown();
        }
    }

    /**
     * Returns the {@link Throwable}s this {@link Subscriber} was notified of via {@link #onError} as a
     * {@link List}.
     *
     * @return a list of the Throwables that were passed to this Subscriber's {@link #onError} method
     */
    public List<Throwable> getOnErrorEvents() {
        return errors;
    }

    /**
     * Provides the Subscriber with a new item to observe.
     * <p>
     * The {@code Observable} may call this method 0 or more times.
     * <p>
     * The {@code Observable} will not call this method again after it calls either {@link #onCompleted} or
     * {@link #onError}.
     * 
     * @param t
     *          the item emitted by the Observable
     */
    @Override
    public void onNext(T t) {
        lastSeenThread = Thread.currentThread();
        values.add(t);
        valueCount = values.size();
        delegate.onNext(t);
    }
    
    /**
     * Returns the committed number of onNext elements that are safe to be
     * read from {@link #getOnNextEvents()} other threads.
     * @return the committed number of onNext elements
     */
    public final int getValueCount() {
        return valueCount;
    }
    
    /**
     * Allows calling the protected {@link #request(long)} from unit tests.
     *
     * @param n the maximum number of items you want the Observable to emit to the Subscriber at this time, or
     *           {@code Long.MAX_VALUE} if you want the Observable to emit items at its own pace
     */
    public void requestMore(long n) {
        request(n);
    }

    /**
     * Returns the sequence of items observed by this {@link Subscriber}, as an ordered {@link List}.
     *
     * @return a list of items observed by this Subscriber, in the order in which they were observed
     */
    public List<T> getOnNextEvents() {
        return values;
    }

    /**
     * Asserts that a particular sequence of items was received by this {@link Subscriber} in order.
     *
     * @param items
     *          the sequence of items expected to have been observed
     * @throws AssertionError
     *          if the sequence of items observed does not exactly match {@code items}
     */
    public void assertReceivedOnNext(List<T> items) {
        if (values.size() != items.size()) {
            assertionError("Number of items does not match. Provided: " + items.size() + "  Actual: " + values.size()
            + ".\n"
            + "Provided values: " + items
            + "\n"
            + "Actual values: " + values
            + "\n");
        }

        for (int i = 0; i < items.size(); i++) {
            T expected = items.get(i);
            T actual = values.get(i);
            if (expected == null) {
                // check for null equality
                if (actual != null) {
                    assertionError("Value at index: " + i + " expected to be [null] but was: [" + actual + "]\n");
                }
            } else if (!expected.equals(actual)) {
                assertionError("Value at index: " + i 
                        + " expected to be [" + expected + "] (" + expected.getClass().getSimpleName() 
                        + ") but was: [" + actual + "] (" + (actual != null ? actual.getClass().getSimpleName() : "null") + ")\n");

            }
        }
    }

    /**
     * Wait until the current committed value count is less than the expected amount
     * by sleeping 1 unit at most timeout times and return true if at least
     * the required amount of onNext values have been received.
     * @param expected the expected number of onNext events
     * @param timeout the time to wait for the events
     * @param unit the time unit of waiting
     * @return true if the expected number of onNext events happened
     * @throws InterruptedException if the sleep is interrupted
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @Experimental
    public final boolean awaitValueCount(int expected, long timeout, TimeUnit unit) throws InterruptedException {
        while (timeout != 0 && valueCount < expected) {
            unit.sleep(1);
            timeout--;
        }
        return valueCount >= expected;
    }
    
    /**
     * Asserts that a single terminal event occurred, either {@link #onCompleted} or {@link #onError}.
     *
     * @throws AssertionError
     *          if not exactly one terminal event notification was received
     */
    public void assertTerminalEvent() {
        if (errors.size() > 1) {
            assertionError("Too many onError events: " + errors.size());
        }

        if (completions > 1) {
            assertionError("Too many onCompleted events: " + completions);
        }

        if (completions == 1 && errors.size() == 1) {
            assertionError("Received both an onError and onCompleted. Should be one or the other.");
        }

        if (completions == 0 && errors.isEmpty()) {
            assertionError("No terminal events received.");
        }
    }

    /**
     * Asserts that this {@code Subscriber} is unsubscribed.
     *
     * @throws AssertionError
     *          if this {@code Subscriber} is not unsubscribed
     */
    public void assertUnsubscribed() {
        if (!isUnsubscribed()) {
            assertionError("Not unsubscribed.");
        }
    }

    /**
     * Asserts that this {@code Subscriber} has received no {@code onError} notifications.
     * 
     * @throws AssertionError
     *          if this {@code Subscriber} has received one or more {@code onError} notifications
     */
    public void assertNoErrors() {
        List<Throwable> onErrorEvents = getOnErrorEvents();
        if (!onErrorEvents.isEmpty()) {
            assertionError("Unexpected onError events");
        }
    }

    
    /**
     * Blocks until this {@link Subscriber} receives a notification that the {@code Observable} is complete
     * (either an {@code onCompleted} or {@code onError} notification).
     *
     * @throws RuntimeException
     *          if the Subscriber is interrupted before the Observable is able to complete
     */
    public void awaitTerminalEvent() {
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted", e);
        }
    }

    /**
     * Blocks until this {@link Subscriber} receives a notification that the {@code Observable} is complete
     * (either an {@code onCompleted} or {@code onError} notification), or until a timeout expires.
     *
     * @param timeout
     *          the duration of the timeout
     * @param unit
     *          the units in which {@code timeout} is expressed
     * @throws RuntimeException
     *          if the Subscriber is interrupted before the Observable is able to complete
     */
    public void awaitTerminalEvent(long timeout, TimeUnit unit) {
        try {
            latch.await(timeout, unit);
        } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted", e);
        }
    }

    /**
     * Blocks until this {@link Subscriber} receives a notification that the {@code Observable} is complete
     * (either an {@code onCompleted} or {@code onError} notification), or until a timeout expires; if the
     * Subscriber is interrupted before either of these events take place, this method unsubscribes the
     * Subscriber from the Observable). If timeout expires then the Subscriber is unsubscribed from the Observable.
     *
     * @param timeout
     *          the duration of the timeout
     * @param unit
     *          the units in which {@code timeout} is expressed
     */
    public void awaitTerminalEventAndUnsubscribeOnTimeout(long timeout, TimeUnit unit) {
        try {
            boolean result = latch.await(timeout, unit);
            if (!result) {
                // timeout occurred
                unsubscribe();
            }
        } catch (InterruptedException e) {
            unsubscribe();
        }
    }

    /**
     * Returns the last thread that was in use when an item or notification was received by this
     * {@link Subscriber}.
     *
     * @return the {@code Thread} on which this Subscriber last received an item or notification from the
     *         Observable it is subscribed to
     */
    public Thread getLastSeenThread() {
        return lastSeenThread;
    }
    
    /**
     * Asserts that there is exactly one completion event.
     *
     * @throws AssertionError if there were zero, or more than one, onCompleted events
     * @since 1.1.0
     */
    public void assertCompleted() {
        int s = completions;
        if (s == 0) {
            assertionError("Not completed!");
        } else
        if (s > 1) {
            assertionError("Completed multiple times: " + s);
        }
    }

    /**
     * Asserts that there is no completion event.
     *
     * @throws AssertionError if there were one or more than one onCompleted events
     * @since 1.1.0
     */
    public void assertNotCompleted() {
        int s = completions;
        if (s == 1) {
            assertionError("Completed!");
        } else
        if (s > 1) {
            assertionError("Completed multiple times: " + s);
        }
    }

    /**
     * Asserts that there is exactly one error event which is a subclass of the given class.
     *
     * @param clazz the class to check the error against.
     * @throws AssertionError if there were zero, or more than one, onError events, or if the single onError
     *                        event did not carry an error of a subclass of the given class
     * @since 1.1.0
     */
    public void assertError(Class<? extends Throwable> clazz) {
        List<Throwable> err = errors;
        if (err.isEmpty()) {
            assertionError("No errors");
        } else
        if (err.size() > 1) {
            AssertionError ae = new AssertionError("Multiple errors: " + err.size());
            ae.initCause(new CompositeException(err));
            throw ae;
        } else
        if (!clazz.isInstance(err.get(0))) {
            AssertionError ae = new AssertionError("Exceptions differ; expected: " + clazz + ", actual: " + err.get(0));
            ae.initCause(err.get(0));
            throw ae;
        }
    }

    /**
     * Asserts that there is a single onError event with the exact exception.
     *
     * @param throwable the throwable to check
     * @throws AssertionError if there were zero, or more than one, onError events, or if the single onError
     *                        event did not carry an error that matches the specified throwable
     * @since 1.1.0
     */
    public void assertError(Throwable throwable) {
        List<Throwable> err = errors;
        if (err.isEmpty()) {
            assertionError("No errors");
        } else
        if (err.size() > 1) {
            assertionError("Multiple errors");
        } else
        if (!throwable.equals(err.get(0))) {
            assertionError("Exceptions differ; expected: " + throwable + ", actual: " + err.get(0));
        }
    }

    /**
     * Asserts that there are no onError and onCompleted events.
     *
     * @throws AssertionError if there was either an onError or onCompleted event
     * @since 1.1.0
     */
    public void assertNoTerminalEvent() {
        List<Throwable> err = errors;
        int s = completions;
        if (!err.isEmpty() || s > 0) {
            if (err.isEmpty()) {
                assertionError("Found " + err.size() + " errors and " + s + " completion events instead of none");
            } else
            if (err.size() == 1) {
                assertionError("Found " + err.size() + " errors and " + s + " completion events instead of none");
            } else {
                assertionError("Found " + err.size() + " errors and " + s + " completion events instead of none");
            }
        }
    }

    /**
     * Asserts that there are no onNext events received.
     *
     * @throws AssertionError if there were any onNext events
     * @since 1.1.0
     */
    public void assertNoValues() {
        int s = values.size();
        if (s != 0) {
            assertionError("No onNext events expected yet some received: " + s);
        }
    }

    /**
     * Asserts that the given number of onNext events are received.
     *
     * @param count the expected number of onNext events
     * @throws AssertionError if there were more or fewer onNext events than specified by {@code count}
     * @since 1.1.0
     */
    public void assertValueCount(int count) {
        int s = values.size();
        if (s != count) {
            assertionError("Number of onNext events differ; expected: " + count + ", actual: " + s);
        }
    }
    
    /**
     * Asserts that the received onNext events, in order, are the specified items.
     *
     * @param values the items to check
     * @throws AssertionError if the items emitted do not exactly match those specified by {@code values}
     * @since 1.1.0
     */
    public void assertValues(T... values) {
        assertReceivedOnNext(Arrays.asList(values));
    }

    /**
     * Asserts that there is only a single received onNext event and that it marks the emission of a specific item.
     *
     * @param value the item to check
     * @throws AssertionError if the Observable does not emit only the single item specified by {@code value}
     * @since 1.1.0
     */
    public void assertValue(T value) {
        assertReceivedOnNext(Collections.singletonList(value));
    }
    
    /**
     * Combines an assertion error message with the current completion and error state of this
     * TestSubscriber, giving more information when some assertXXX check fails.
     * @param message the message to use for the error
     */
    final void assertionError(String message) {
        StringBuilder b = new StringBuilder(message.length() + 32);
        
        b.append(message)
        .append(" (");
        
        int c = completions;
        b.append(c)
        .append(" completion");
        if (c != 1) {
            b.append('s');
        }
        b.append(')');
        
        if (!errors.isEmpty()) {
            int size = errors.size();
            b.append(" (+")
            .append(size)
            .append(" error");
            if (size != 1) {
                b.append('s');
            }
            b.append(')');
        }
        
        AssertionError ae = new AssertionError(b.toString());
        if (!errors.isEmpty()) {
            if (errors.size() == 1) {
                ae.initCause(errors.get(0));
            } else {
                ae.initCause(new CompositeException(errors));
            }
        }
        throw ae;
    }
}
