/*
 * Copyright 2016-2017 David Karnok
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package hu.akarnokd.rxjava2.operators;

import java.util.*;
import java.util.concurrent.*;

import org.reactivestreams.Publisher;

import hu.akarnokd.rxjava2.util.BiFunctionSecondIdentity;
import io.reactivex.*;
import io.reactivex.annotations.*;
import io.reactivex.functions.*;
import io.reactivex.internal.functions.*;
import io.reactivex.schedulers.Schedulers;

/**
 * Additional operators in the form of {@link FlowableTransformer},
 * use {@link Flowable#compose(FlowableTransformer)}
 * to apply the operators to an existing sequence.
 * 
 * @since 0.7.2
 */
public final class FlowableTransformers {
    /** Utility class. */
    private FlowableTransformers() {
        throw new IllegalStateException("No instances!");
    }

    /**
     * Relays values until the other Publisher signals false and resumes if the other
     * Publisher signals true again, like closing and opening a valve and not losing
     * any items from the main source.
     * <p>Properties:
     * <ul>
     * <li>The operator starts with an open valve.</li>
     * <li>If the other Publisher completes, the sequence terminates with an {@code IllegalStateException}.</li>
     * <li>The operator doesn't run on any particular {@link io.reactivex.Scheduler Scheduler}.</li>
     * <li>The operator is a pass-through for backpressure and uses an internal unbounded buffer
     * of size {@link Flowable#bufferSize()} to hold onto values if the valve is closed.</li>
     * </ul>
     * @param <T> the value type of the main source
     * @param other the other source
     * @return the new FlowableTransformer instance
     * @throws NullPointerException if {@code other} is null
     * 
     * @since 0.7.2
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    @BackpressureSupport(BackpressureKind.PASS_THROUGH)
    public static <T> FlowableTransformer<T, T> valve(Publisher<Boolean> other) {
        return valve(other, true, Flowable.bufferSize());
    }

    /**
     * Relays values until the other Publisher signals false and resumes if the other
     * Publisher signals true again, like closing and opening a valve and not losing
     * any items from the main source and starts with the specivied valve state.
     * <p>Properties:
     * <ul>
     * <li>If the other Publisher completes, the sequence terminates with an {@code IllegalStateException}.</li>
     * <li>The operator doesn't run on any particular {@link io.reactivex.Scheduler Scheduler}.</li>
     * <li>The operator is a pass-through for backpressure and uses an internal unbounded buffer
     * of size {@link Flowable#bufferSize()} to hold onto values if the valve is closed.</li>
     * </ul>
     * @param <T> the value type of the main source
     * @param other the other source
     * @param defaultOpen should the valve start as open?
     * @return the new FlowableTransformer instance
     * @throws NullPointerException if {@code other} is null
     * 
     * @since 0.7.2
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    @BackpressureSupport(BackpressureKind.PASS_THROUGH)
    public static <T> FlowableTransformer<T, T> valve(Publisher<Boolean> other, boolean defaultOpen) {
        return valve(other, defaultOpen, Flowable.bufferSize());
    }

    /**
     * Relays values until the other Publisher signals false and resumes if the other
     * Publisher signals true again, like closing and opening a valve and not losing
     * any items from the main source and starts with the specivied valve state and the specified
     * buffer size hint.
     * <p>Properties:
     * <ul>
     * <li>If the other Publisher completes, the sequence terminates with an {@code IllegalStateException}.</li>
     * <li>The operator doesn't run on any particular {@link io.reactivex.Scheduler Scheduler}.</li>
     * </ul>
     * @param <T> the value type of the main source
     * @param other the other source
     * @param defaultOpen should the valve start as open?
     * @param bufferSize the buffer size hint (the chunk size of the underlying unbounded buffer)
     * @return the new FlowableTransformer instance
     * @throws IllegalArgumentException if bufferSize &lt;= 0
     * @throws NullPointerException if {@code other} is null
     * @since 0.7.2
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    @BackpressureSupport(BackpressureKind.PASS_THROUGH)
    public static <T> FlowableTransformer<T, T> valve(Publisher<Boolean> other, boolean defaultOpen, int bufferSize) {
        ObjectHelper.requireNonNull(other, "other is null");
        ObjectHelper.verifyPositive(bufferSize, "bufferSize");
        return new FlowableValve<T>(null, other, defaultOpen, bufferSize);
    }

    /**
     * Buffers elements into a List while the given predicate returns true; if the
     * predicate returns false for an item, a new buffer is created with the specified item.
     * @param <T> the source value type
     * @param predicate the predicate receiving the current value and if returns false,
     *                  a new buffer is created with the specified item
     * @return the new FlowableTransformer instance
     *
     * @since 0.8.0
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    @BackpressureSupport(BackpressureKind.FULL)
    public static <T> FlowableTransformer<T, List<T>> bufferWhile(Predicate<? super T> predicate) {
        return bufferWhile(predicate, Functions.<T>createArrayList(16));
    }

    /**
     * Buffers elements into a custom collection while the given predicate returns true; if the
     * predicate returns false for an item, a new collection is created with the specified item.
     * @param <T> the source value type
     * @param <C> the collection type
     * @param predicate the predicate receiving the current value and if returns false,
     *                  a new collection is created with the specified item
     * @param bufferSupplier the callable that returns a fresh collection
     * @return the new FlowableTransformer instance
     *
     * @since 0.8.0
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    @BackpressureSupport(BackpressureKind.FULL)
    public static <T, C extends Collection<? super T>> FlowableTransformer<T, C> bufferWhile(Predicate<? super T> predicate, Callable<C> bufferSupplier) {
        return new FlowableBufferPredicate<T, C>(null, predicate, FlowableBufferPredicate.Mode.BEFORE, bufferSupplier);
    }

    /**
     * Buffers elements into a List until the given predicate returns true at which
     * point a new empty buffer is started.
     * @param <T> the source value type
     * @param predicate the predicate receiving the current item and if returns true,
     *                  the current buffer is emitted and a fresh empty buffer is created
     * @return the new FlowableTransformer instance
     *
     * @since 0.8.0
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    @BackpressureSupport(BackpressureKind.FULL)
    public static <T> FlowableTransformer<T, List<T>> bufferUntil(Predicate<? super T> predicate) {
        return bufferUntil(predicate, Functions.<T>createArrayList(16));
    }


    /**
     * Buffers elements into a custom collection until the given predicate returns true at which
     * point a new empty custom collection is started.
     * @param <T> the source value type
     * @param <C> the collection type
     * @param predicate the predicate receiving the current item and if returns true,
     *                  the current collection is emitted and a fresh empty collection is created
     * @param bufferSupplier the callable that returns a fresh collection
     * @return the new Flowable instance
     *
     * @since 0.8.0
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    @BackpressureSupport(BackpressureKind.FULL)
    public static <T, C extends Collection<? super T>> FlowableTransformer<T, C> bufferUntil(Predicate<? super T> predicate, Callable<C> bufferSupplier) {
        return new FlowableBufferPredicate<T, C>(null, predicate, FlowableBufferPredicate.Mode.AFTER, bufferSupplier);
    }

    /**
     * Buffers elements into a List until the given predicate returns true at which
     * point a new empty buffer is started; the particular item will be dropped.
     * @param <T> the source value type
     * @param predicate the predicate receiving the current item and if returns true,
     *                  the current buffer is emitted and a fresh empty buffer is created
     * @return the new FlowableTransformer instance
     *
     * @since 0.14.3
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    @BackpressureSupport(BackpressureKind.FULL)
    public static <T> FlowableTransformer<T, List<T>> bufferSplit(Predicate<? super T> predicate) {
        return bufferSplit(predicate, Functions.<T>createArrayList(16));
    }


    /**
     * Buffers elements into a custom collection until the given predicate returns true at which
     * point a new empty custom collection is started; the particular item will be dropped.
     * @param <T> the source value type
     * @param <C> the collection type
     * @param predicate the predicate receiving the current item and if returns true,
     *                  the current collection is emitted and a fresh empty collection is created
     * @param bufferSupplier the callable that returns a fresh collection
     * @return the new Flowable instance
     *
     * @since 0.14.3
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    @BackpressureSupport(BackpressureKind.FULL)
    public static <T, C extends Collection<? super T>> FlowableTransformer<T, C> bufferSplit(Predicate<? super T> predicate, Callable<C> bufferSupplier) {
        return new FlowableBufferPredicate<T, C>(null, predicate, FlowableBufferPredicate.Mode.SPLIT, bufferSupplier);
    }

    /**
     * Inserts a time delay between emissions from the upstream source.
     * <dl>
     *  <dt><b>Backpressure:</b></dt>
     *  <dd>The operator itself doesn't interfere with backpressure and uses an unbounded
     *  internal buffer to store elements that need delay.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>The operator uses the computation {@link Scheduler}.</dd>
     * </dl>
     * @param <T> the value type
     * @param betweenDelay the (minimum) delay time between elements
     * @param unit the time unit of the initial delay and the between delay values
     * @return the new FlowableTransformer instance
     * 
     * @since 0.9.0
     */
    @BackpressureSupport(BackpressureKind.PASS_THROUGH)
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public static <T> FlowableTransformer<T, T> spanout(long betweenDelay, TimeUnit unit) {
        return spanout(0L, betweenDelay, unit, Schedulers.computation(), false);
    }


    /**
     * Inserts a time delay between emissions from the upstream source.
     * <dl>
     *  <dt><b>Backpressure:</b></dt>
     *  <dd>The operator itself doesn't interfere with backpressure and uses an unbounded
     *  internal buffer to store elements that need delay.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>The operator uses a custom {@link Scheduler} you provide.</dd>
     * </dl>
     * @param <T> the value type
     * @param betweenDelay the (minimum) delay time between elements
     * @param unit the time unit of the initial delay and the between delay values
     * @param scheduler the scheduler to delay and emit the values on
     * @return the new FlowableTransformer instance
     * 
     * @since 0.9.0
     */
    @BackpressureSupport(BackpressureKind.PASS_THROUGH)
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public static <T> FlowableTransformer<T, T> spanout(long betweenDelay, TimeUnit unit, Scheduler scheduler) {
        return spanout(0L, betweenDelay, unit, scheduler, false);
    }


    /**
     * Inserts a time delay between emissions from the upstream source, including an initial delay.
     * <dl>
     *  <dt><b>Backpressure:</b></dt>
     *  <dd>The operator itself doesn't interfere with backpressure and uses an unbounded
     *  internal buffer to store elements that need delay.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>The operator uses the computation {@link Scheduler}.</dd>
     * </dl>
     * @param <T> the value type
     * @param initialDelay the initial delay
     * @param betweenDelay the (minimum) delay time between elements
     * @param unit the time unit of the initial delay and the between delay values
     * @return the new FlowableTransformer instance
     * 
     * @since 0.9.0
     */
    @BackpressureSupport(BackpressureKind.PASS_THROUGH)
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public static <T> FlowableTransformer<T, T> spanout(long initialDelay, long betweenDelay, TimeUnit unit) {
        return spanout(initialDelay, betweenDelay, unit, Schedulers.computation(), false);
    }

    /**
     * Inserts a time delay between emissions from the upstream source, including an initial delay.
     * <dl>
     *  <dt><b>Backpressure:</b></dt>
     *  <dd>The operator itself doesn't interfere with backpressure and uses an unbounded
     *  internal buffer to store elements that need delay.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>The operator uses a custom {@link Scheduler} you provide.</dd>
     * </dl>
     * @param <T> the value type
     * @param initialDelay the initial delay
     * @param betweenDelay the (minimum) delay time between elements
     * @param unit the time unit of the initial delay and the between delay values
     * @param scheduler the scheduler to delay and emit the values on
     * @return the new FlowableTransformer instance
     * 
     * @since 0.9.0
     */
    @BackpressureSupport(BackpressureKind.PASS_THROUGH)
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public static <T> FlowableTransformer<T, T> spanout(long initialDelay, long betweenDelay, TimeUnit unit, Scheduler scheduler) {
        return spanout(initialDelay, betweenDelay, unit, scheduler, false);
    }

    /**
     * Inserts a time delay between emissions from the upstream source, including an initial delay.
     * <dl>
     *  <dt><b>Backpressure:</b></dt>
     *  <dd>The operator itself doesn't interfere with backpressure and uses an unbounded
     *  internal buffer to store elements that need delay.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>The operator uses the computation {@link Scheduler}.</dd>
     * </dl>
     * @param <T> the value type
     * @param betweenDelay the (minimum) delay time between elements
     * @param unit the time unit of the initial delay and the between delay values
     * @param delayError delay the onError event from upstream
     * @return the new FlowableTransformer instance
     * 
     * @since 0.9.0
     */
    @BackpressureSupport(BackpressureKind.PASS_THROUGH)
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public static <T> FlowableTransformer<T, T> spanout(long betweenDelay, TimeUnit unit, boolean delayError) {
        return spanout(0L, betweenDelay, unit, Schedulers.computation(), delayError);
    }


    /**
     * Inserts a time delay between emissions from the upstream source, including an initial delay.
     * <dl>
     *  <dt><b>Backpressure:</b></dt>
     *  <dd>The operator itself doesn't interfere with backpressure and uses an unbounded
     *  internal buffer to store elements that need delay.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>The operator uses a custom {@link Scheduler} you provide.</dd>
     * </dl>
     * @param <T> the value type
     * @param betweenDelay the (minimum) delay time between elements
     * @param unit the time unit of the initial delay and the between delay values
     * @param scheduler the scheduler to delay and emit the values on
     * @param delayError delay the onError event from upstream
     * @return the new FlowableTransformer instance
     * 
     * @since 0.9.0
     */
    @BackpressureSupport(BackpressureKind.PASS_THROUGH)
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public static <T> FlowableTransformer<T, T> spanout(long betweenDelay, TimeUnit unit, Scheduler scheduler, boolean delayError) {
        return spanout(0L, betweenDelay, unit, scheduler, delayError);
    }


    /**
     * Inserts a time delay between emissions from the upstream source, including an initial delay.
     * <dl>
     *  <dt><b>Backpressure:</b></dt>
     *  <dd>The operator itself doesn't interfere with backpressure and uses an unbounded
     *  internal buffer to store elements that need delay.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>The operator uses the computation {@link Scheduler}.</dd>
     * </dl>
     * @param <T> the value type
     * @param initialDelay the initial delay
     * @param betweenDelay the (minimum) delay time between elements
     * @param unit the time unit of the initial delay and the between delay values
     * @param delayError delay the onError event from upstream
     * @return the new FlowableTransformer instance
     * 
     * @since 0.9.0
     */
    @BackpressureSupport(BackpressureKind.PASS_THROUGH)
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public static <T> FlowableTransformer<T, T> spanout(long initialDelay, long betweenDelay, TimeUnit unit, boolean delayError) {
        return spanout(initialDelay, betweenDelay, unit, Schedulers.computation(), delayError);
    }

    /**
     * Inserts a time delay between emissions from the upstream source, including an initial delay.
     * <dl>
     *  <dt><b>Backpressure:</b></dt>
     *  <dd>The operator itself doesn't interfere with backpressure and uses an unbounded
     *  internal buffer to store elements that need delay.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>The operator uses a custom {@link Scheduler} you provide.</dd>
     * </dl>
     * @param <T> the value type
     * @param initialDelay the initial delay
     * @param betweenDelay the (minimum) delay time between elements
     * @param unit the time unit of the initial delay and the between delay values
     * @param scheduler the scheduler to delay and emit the values on
     * @param delayError delay the onError event from upstream
     * @return the new FlowableTransformer instance
     * 
     * @since 0.9.0
     */
    @BackpressureSupport(BackpressureKind.PASS_THROUGH)
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public static <T> FlowableTransformer<T, T> spanout(long initialDelay, long betweenDelay, TimeUnit unit, Scheduler scheduler, boolean delayError) {
        ObjectHelper.requireNonNull(unit, "unit is null");
        ObjectHelper.requireNonNull(scheduler, "scheduler is null");
        return new FlowableSpanout<T>(null, initialDelay, betweenDelay, unit, scheduler, delayError, Flowable.bufferSize());
    }

    /**
     * Allows mapping or filtering an upstream value through an emitter.
     * @param <T> the input value type
     * @param <R> the output value type
     * @param consumer the consumer that is called for each upstream value and should call one of the doXXX methods
     * on the BasicEmitter it receives (individual to each Subscriber).
     * @return the new FlowableTransformer instance
     * 
     * @since 0.10.0
     */
    @BackpressureSupport(BackpressureKind.PASS_THROUGH)
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T, R> FlowableTransformer<T, R> mapFilter(BiConsumer<? super T, ? super BasicEmitter<R>> consumer) {
        ObjectHelper.requireNonNull(consumer, "consumer is null");
        return new FlowableMapFilter<T, R>(null, consumer);
    }

    /**
     * Buffers the incoming values from upstream up to a maximum timeout if
     * the downstream can't keep up.
     * @param <T> the value type
     * @param timeout the maximum age of an element in the buffer
     * @param unit the time unit of the timeout
     * @return the new FlowableTransformer instance
     * @see #onBackpressureTimeout(int, long, TimeUnit, Scheduler, Consumer) for more options
     *
     * @since 0.13.0
     */
    @BackpressureSupport(BackpressureKind.UNBOUNDED_IN)
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public static <T> FlowableTransformer<T, T> onBackpressureTimeout(long timeout, TimeUnit unit) {
        return onBackpressureTimeout(timeout, unit, Schedulers.computation());
    }

    /**
     * Buffers the incoming values from upstream up to a maximum size or timeout if
     * the downstream can't keep up.
     * @param <T> the value type
     * @param timeout the maximum age of an element in the buffer
     * @param unit the time unit of the timeout
     * @param scheduler the scheduler to be used as time source and to trigger the timeout &amp; eviction
     * @param onEvict called when an element is evicted, maybe concurrently
     * @return the new FlowableTransformer instance
     *
     * @since 0.13.0
     */
    @BackpressureSupport(BackpressureKind.UNBOUNDED_IN)
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public static <T> FlowableTransformer<T, T> onBackpressureTimeout(long timeout, TimeUnit unit, Scheduler scheduler, Consumer<? super T> onEvict) {
        ObjectHelper.requireNonNull(unit, "unit is null");
        ObjectHelper.requireNonNull(scheduler, "scheduler is null");
        ObjectHelper.requireNonNull(onEvict, "onEvict is null");

        return new FlowableOnBackpressureTimeout<T>(null, Integer.MAX_VALUE, timeout, unit, scheduler, onEvict);
    }

    /**
     * Buffers the incoming values from upstream up to a maximum timeout if
     * the downstream can't keep up, running on a custom scheduler.
     * @param <T> the value type
     * @param timeout the maximum age of an element in the buffer
     * @param unit the time unit of the timeout
     * @param scheduler the scheduler to be used as time source and to trigger the timeout &amp; eviction
     * @return the new FlowableTransformer instance
     * @see #onBackpressureTimeout(int, long, TimeUnit, Scheduler, Consumer) for more options
     *
     * @since 0.13.0
     */
    @BackpressureSupport(BackpressureKind.UNBOUNDED_IN)
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public static <T> FlowableTransformer<T, T> onBackpressureTimeout(long timeout, TimeUnit unit, Scheduler scheduler) {
        return onBackpressureTimeout(Integer.MAX_VALUE, timeout, unit, scheduler, Functions.emptyConsumer());
    }

    /**
     * Buffers the incoming values from upstream up to a maximum size or timeout if
     * the downstream can't keep up.
     * @param <T> the value type
     * @param maxSize the maximum number of elements in the buffer, beyond that,
     *                the oldest element is evicted
     * @param timeout the maximum age of an element in the buffer
     * @param unit the time unit of the timeout
     * @param scheduler the scheduler to be used as time source and to trigger the timeout &amp; eviction
     * @param onEvict called when an element is evicted, maybe concurrently
     * @return the new FlowableTransformer instance
     *
     * @since 0.13.0
     */
    @BackpressureSupport(BackpressureKind.UNBOUNDED_IN)
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public static <T> FlowableTransformer<T, T> onBackpressureTimeout(int maxSize, long timeout, TimeUnit unit, Scheduler scheduler, Consumer<? super T> onEvict) {
        ObjectHelper.verifyPositive(maxSize, "maxSize");
        ObjectHelper.requireNonNull(unit, "unit is null");
        ObjectHelper.requireNonNull(scheduler, "scheduler is null");
        ObjectHelper.requireNonNull(onEvict, "onEvict is null");

        return new FlowableOnBackpressureTimeout<T>(null, maxSize, timeout, unit, scheduler, onEvict);
    }

    /**
     * Relays every Nth item from upstream.
     * <dl>
     *  <dt><b>Backpressure:</b></dt>
     *  <dd>The operator requests keep {@code times} what the downstream requests and skips @code keep-1} items.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>The operator doesn't run on any particular {@link Scheduler}.</dd>
     * </dl>
     * @param <T> the value type
     * @param keep the period of items to keep, i.e., this minus one items will be dropped
     * before emitting an item directly
     * @return the new FlowableTransformer instance
     *
     * @since 0.14.2
     */
    @BackpressureSupport(BackpressureKind.SPECIAL)
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> FlowableTransformer<T, T> every(long keep) {
        ObjectHelper.verifyPositive(keep, "keep");
        return new FlowableEvery<T>(null, keep);
    }

    /**
     * Cache the very last value of the flow and relay/replay it to Subscribers.
     * <p>
     * The operator subscribes to the upstream when the first downstream Subscriber
     * arrives. Once connected, the upstream can't be stopped from the
     * downstream even if all Subscribers cancel.
     * <p>
     * A difference from {@code replay(1)} is that {@code replay()} is likely
     * holding onto 2 references due to continuity requirements whereas this
     * operator is guaranteed to hold only the very last item.
     * @param <T> the value type emitted
     * @return the new FlowableTransformer instance
     * 
     * @since 0.15.0
     */
    @BackpressureSupport(BackpressureKind.UNBOUNDED_IN)
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T> FlowableTransformer<T, T> cacheLast() {
        return new FlowableCacheLast<T>(null);
    }

    /**
     * Emit the last item when the upstream completes or the
     * the latest received if the specified timeout elapses since
     * the last received item.
     * @param <T> the value type
     * @param timeout the timeout value
     * @param unit the timeout time unit
     * @return the new Flowable type
     * 
     * @since 0.15.0
     */
    @BackpressureSupport(BackpressureKind.UNBOUNDED_IN)
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public static <T> FlowableTransformer<T, T> timeoutLast(long timeout, TimeUnit unit) {
        return timeoutLast(timeout, unit, Schedulers.computation());
    }

    /**
     * Emit the last item when the upstream completes or the
     * the latest received if the specified timeout elapses since
     * the last received item.
     * @param <T> the value type
     * @param timeout the timeout value
     * @param unit the timeout time unit
     * @param scheduler the scheduler to run the timeout and possible emit the last/latest
     * @return the new Flowable type
     * 
     * @since 0.15.0
     */
    @BackpressureSupport(BackpressureKind.UNBOUNDED_IN)
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public static <T> FlowableTransformer<T, T> timeoutLast(long timeout, TimeUnit unit, Scheduler scheduler) {
        ObjectHelper.requireNonNull(unit, "unit is null");
        ObjectHelper.requireNonNull(scheduler, "scheduler is null");
        return new FlowableTimeoutLast<T>(null, timeout, unit, scheduler, false);
    }

    /**
     * Emit the last item when the upstream completes or the
     * the latest received if the specified timeout elapses
     * since the start of the sequence.
     * @param <T> the value type
     * @param timeout the timeout value
     * @param unit the timeout time unit
     * @return the new Flowable type
     * 
     * @since 0.15.0
     */
    @BackpressureSupport(BackpressureKind.UNBOUNDED_IN)
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public static <T> FlowableTransformer<T, T> timeoutLastAbsolute(long timeout, TimeUnit unit) {
        return timeoutLastAbsolute(timeout, unit, Schedulers.computation());
    }

    /**
     * Emit the last item when the upstream completes or the
     * the latest received if the specified timeout elapses
     * since the start of the sequence.
     * @param <T> the value type
     * @param timeout the timeout value
     * @param unit the timeout time unit
     * @param scheduler the scheduler to run the timeout and possible emit the last/latest
     * @return the new Flowable type
     * 
     * @since 0.15.0
     */
    @BackpressureSupport(BackpressureKind.UNBOUNDED_IN)
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public static <T> FlowableTransformer<T, T> timeoutLastAbsolute(long timeout, TimeUnit unit, Scheduler scheduler) {
        ObjectHelper.requireNonNull(unit, "unit is null");
        ObjectHelper.requireNonNull(scheduler, "scheduler is null");
        return new FlowableTimeoutLast<T>(null, timeout, unit, scheduler, true);
    }

    /**
     * Debounces the upstream by taking an item and dropping subsequent items until
     * the specified amount of time elapses after the last item, after which the
     * process repeats.
     * <p>
     * Note that the operator uses the {@code computation} {@link Scheduler} for
     * the source of time but doesn't use it to emit non-dropped items or terminal events.
     * The operator uses calculation with the current time to decide if an upstream
     * item may pass or not.
     * @param <T> the value type
     * @param timeout the timeout
     * @param unit the unit of measure of the timeout parameter
     * @return the new FlowableTransformer instance
     *
     * @since 0.15.0
     */
    @BackpressureSupport(BackpressureKind.PASS_THROUGH)
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    public static <T> FlowableTransformer<T, T> debounceFirst(long timeout, TimeUnit unit) {
        return debounceFirst(timeout, unit, Schedulers.computation());
    }

    /**
     * Debounces the upstream by taking an item and dropping subsequent items until
     * the specified amount of time elapses after the last item, after which the
     * process repeats.
     * <p>
     * Note that the operator uses the {@code computation} {@link Scheduler} for
     * the source of time but doesn't use it to emit non-dropped items or terminal events.
     * The operator uses calculation with the current time to decide if an upstream
     * item may pass or not.
     * @param <T> the value type
     * @param timeout the timeout
     * @param unit the unit of measure of the timeout parameter
     * @param scheduler the scheduler used for getting the current time when
     * evaluating upstream items
     * @return the new FlowableTransformer instance
     *
     * @since 0.15.0
     */
    @BackpressureSupport(BackpressureKind.PASS_THROUGH)
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public static <T> FlowableTransformer<T, T> debounceFirst(long timeout, TimeUnit unit, Scheduler scheduler) {
        ObjectHelper.requireNonNull(unit, "unit is null");
        ObjectHelper.requireNonNull(scheduler, "scheduler is null");
        return new FlowableDebounceFirst<T>(null, timeout, unit, scheduler);
    }

    /**
     * Combination of switchMap and flatMap where there is a limit on the number of
     * concurrent sources to be flattened into a single sequence and if the operator is at
     * the given maximum active count, a newer source Publisher will switch out the oldest
     * active source Publisher being merged.
     * @param <T> the source value type
     * @param <R> the result value type
     * @param mapper the function that maps an upstream value into a Publisher to be merged/switched
     * @param maxActive the maximum number of active inner Publishers
     * @return the new FlowableTransformer instance
     * 
     * @since 0.15.0
     */
    @BackpressureSupport(BackpressureKind.FULL)
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T, R> FlowableTransformer<T, R> switchFlatMap(Function<? super T, ? extends Publisher<? extends R>> mapper, int maxActive) {
        return switchFlatMap(mapper, maxActive, Flowable.bufferSize());
    }

    /**
     * Combination of switchMap and flatMap where there is a limit on the number of
     * concurrent sources to be flattened into a single sequence and if the operator is at
     * the given maximum active count, a newer source Publisher will switch out the oldest
     * active source Publisher being merged.
     * @param <T> the source value type
     * @param <R> the result value type
     * @param mapper the function that maps an upstream value into a Publisher to be merged/switched
     * @param maxActive the maximum number of active inner Publishers
     * @param bufferSize the number of items to prefetch from each inner source
     * @return the new FlowableTransformer instance
     * 
     * @since 0.15.0
     */
    @BackpressureSupport(BackpressureKind.FULL)
    @SchedulerSupport(SchedulerSupport.NONE)
    public static <T, R> FlowableTransformer<T, R> switchFlatMap(Function<? super T, ? extends Publisher<? extends R>> mapper, int maxActive, int bufferSize) {
        ObjectHelper.requireNonNull(mapper, "mapper is null");
        ObjectHelper.verifyPositive(maxActive, "maxActive");
        ObjectHelper.verifyPositive(bufferSize, "bufferSize");
        return new FlowableSwitchFlatMap<T, R>(null, mapper, maxActive, bufferSize);
    }

    /**
     * Maps the upstream values into Publisher and merges at most 32 of them at once,
     * optimized for mainly synchronous sources.
     * @param <T> the input value type
     * @param <R> the result value type
     * @param mapper the function mapping from a value into a Publisher
     * @return the new FlowableTransformer instance
     *
     * @since 0.16.0
     */
    public static <T, R> FlowableTransformer<T, R> flatMapSync(Function<? super T, ? extends Publisher<? extends R>> mapper) {
        return flatMapSync(mapper, 32, Flowable.bufferSize(), true);
    }

    /**
     * Maps the upstream values into Publisher and merges at most maxConcurrency of them at once,
     * optimized for mainly synchronous sources.
     * @param <T> the input value type
     * @param <R> the result value type
     * @param mapper the function mapping from a value into a Publisher
     * @param depthFirst if true, the inner sources are drained as much as possible
     *                   if false, the inner sources are consumed in a round-robin fashion
     * @return the new FlowableTransformer instance
     *
     * @since 0.16.0
     */
    public static <T, R> FlowableTransformer<T, R> flatMapSync(Function<? super T, ? extends Publisher<? extends R>> mapper, boolean depthFirst) {
        return flatMapSync(mapper, 32, Flowable.bufferSize(), depthFirst);
    }

    /**
     * Maps the upstream values into Publisher and merges at most maxConcurrency of them at once,
     * optimized for mainly synchronous sources.
     * @param <T> the input value type
     * @param <R> the result value type
     * @param mapper the function mapping from a value into a Publisher
     * @param maxConcurrency the maximum number of sources merged at once
     * @param bufferSize the prefetch on each inner source
     * @param depthFirst if true, the inner sources are drained as much as possible
     *                   if false, the inner sources are consumed in a round-robin fashion
     * @return the new FlowableTransformer instance
     *
     * @since 0.16.0
     */
    public static <T, R> FlowableTransformer<T, R> flatMapSync(Function<? super T, ? extends Publisher<? extends R>> mapper, int maxConcurrency, int bufferSize, boolean depthFirst) {
        return new FlowableFlatMapSync<T, R>(null, mapper, maxConcurrency, bufferSize, depthFirst);
    }

    /**
     * Maps the upstream values into Publisher and merges at most 32 of them at once,
     * collects and emits the items on the specified scheduler.
     * <p>This operator can be considered as a fusion between a flatMapSync
     * and observeOn.
     * @param <T> the input value type
     * @param <R> the output value type
     * @param mapper the function mapping from a value into a Publisher
     * @param scheduler the Scheduler to use to collect and emit merged items
     * @return the new FlowableTransformer instance
     *
     * @since 0.16.0
     */
    public static <T, R> FlowableTransformer<T, R> flatMapAsync(Function<? super T, ? extends Publisher<? extends R>> mapper, Scheduler scheduler) {
        return flatMapAsync(mapper, scheduler, 32, Flowable.bufferSize(), true);
    }

    /**
     * Maps the upstream values into Publisher and merges at most 32 of them at once,
     * collects and emits the items on the specified scheduler.
     * <p>This operator can be considered as a fusion between a flatMapSync
     * and observeOn.
     * @param <T> the input value type
     * @param <R> the output value type
     * @param mapper the function mapping from a value into a Publisher
     * @param scheduler the Scheduler to use to collect and emit merged items
     * @param depthFirst if true, the inner sources are drained as much as possible
     *                   if false, the inner sources are consumed in a round-robin fashion
     * @return the new FlowableTransformer instance
     *
     * @since 0.16.0
     */
    public static <T, R> FlowableTransformer<T, R> flatMapAsync(Function<? super T, ? extends Publisher<? extends R>> mapper, Scheduler scheduler, boolean depthFirst) {
        return flatMapAsync(mapper, scheduler, 32, Flowable.bufferSize(), depthFirst);
    }

    /**
     * Maps the upstream values into Publisher and merges at most 32 of them at once,
     * collects and emits the items on the specified scheduler.
     * <p>This operator can be considered as a fusion between a flatMapSync
     * and observeOn.
     * @param <T> the input value type
     * @param <R> the output value type
     * @param mapper the function mapping from a value into a Publisher
     * @param scheduler the Scheduler to use to collect and emit merged items
     * @param maxConcurrency the maximum number of sources merged at once
     * @param bufferSize the prefetch on each inner source
     * @param depthFirst if true, the inner sources are drained as much as possible
     *                   if false, the inner sources are consumed in a round-robin fashion
     * @return the new FlowableTransformer instance
     *
     * @since 0.16.0
     */
    public static <T, R> FlowableTransformer<T, R> flatMapAsync(Function<? super T, ? extends Publisher<? extends R>> mapper, Scheduler scheduler, int maxConcurrency, int bufferSize, boolean depthFirst) {
        return new FlowableFlatMapAsync<T, R>(null, mapper, maxConcurrency, bufferSize, depthFirst, scheduler);
    }

    /**
     * If the upstream turns out to be empty, it keeps switching to the alternative sources until
     * one of them is non-empty or there are no more alternatives remaining.
     * @param <T> the input and output value type
     * @param alternatives the array of alternative Publishers.
     * @return the new FlowableTransformer instance
     *
     * @since 0.16.0
     */
    public static <T> FlowableTransformer<T, T> switchIfEmptyArray(Publisher<? extends T>... alternatives) {
        return new FlowableSwitchIfEmptyManyArray<T>(null, alternatives);
    }

    /**
     * If the upstream turns out to be empty, it keeps switching to the alternative sources until
     * one of them is non-empty or there are no more alternatives remaining.
     * @param <T> the input and output value type
     * @param alternatives the Iterable of alternative Publishers.
     * @return the new FlowableTransformer instance
     *
     * @since 0.16.0
     */
    public static <T> FlowableTransformer<T, T> switchIfEmpty(Iterable<? extends Publisher<? extends T>> alternatives) {
        return new FlowableSwitchIfEmptyMany<T>(null, alternatives);
    }

    /**
     * Emits elements from the source and then expands them into another layer of Publishers, emitting
     * those items recursively until all Publishers become empty in a depth-first manner.
     * @param <T> the value type
     * @param expander the function that converts an element into a Publisher to be expanded
     * @return the new FlwoableTransformer instance
     *
     * @since 0.16.1
     */
    public static <T> FlowableTransformer<T, T> expand(Function<? super T, ? extends Publisher<? extends T>> expander) {
        return expand(expander, ExpandStrategy.DEPTH_FIRST, Flowable.bufferSize());
    }

    /**
     * Emits elements from the source and then expands them into another layer of Publishers, emitting
     * those items recursively until all Publishers become empty with the specified strategy.
     * @param <T> the value type
     * @param expander the function that converts an element into a Publisher to be expanded
     * @param strategy the expansion strategy; depth-first will recursively expand the first item until there is no
     *                 more expansion possible, then the second items, and so on;
     *                 breadth-first will first expand the main source, then runs the expaned
     *                 Publishers in sequence, then the 3rd level, and so on.
     * @return the new FlwoableTransformer instance
     *
     * @since 0.16.1
     */
    public static <T> FlowableTransformer<T, T> expand(Function<? super T, ? extends Publisher<? extends T>> expander, ExpandStrategy strategy) {
        return expand(expander, strategy, Flowable.bufferSize());
    }

    /**
     * Emits elements from the source and then expands them into another layer of Publishers, emitting
     * those items recursively until all Publishers become empty with the specified strategy.
     * @param <T> the value type
     * @param expander the function that converts an element into a Publisher to be expanded
     * @param strategy the expansion strategy; depth-first will recursively expand the first item until there is no
     *                 more expansion possible, then the second items, and so on;
     *                 breadth-first will first expand the main source, then runs the expaned
     *                 Publishers in sequence, then the 3rd level, and so on.
     * @param capacityHint the capacity hint for the breadth-first expansion
     * @return the new FlwoableTransformer instance
     *
     * @since 0.16.1
     */
    public static <T> FlowableTransformer<T, T> expand(Function<? super T, ? extends Publisher<? extends T>> expander, ExpandStrategy strategy, int capacityHint) {
        ObjectHelper.requireNonNull(expander, "expander is null");
        ObjectHelper.requireNonNull(strategy, "strategy is null");
        ObjectHelper.verifyPositive(capacityHint, "capacityHint");
        return new FlowableExpand<T>(null, expander, strategy, capacityHint);
    }

    /**
     * Maps each upstream value into a single value provided by a generated Publisher for that
     * input value to be emitted to downstream.
     * <p>Only the first item emitted by the inner Publisher's are considered. If
     * the inner Publisher is empty, no resulting item is generated for that input value.
     * <p>The inner Publishers are consumed in order and one at a time.
     * @param <T> the input value type
     * @param <R> the result value type
     * @param mapper the function that receives the upstream value and returns a Publisher
     * that should emit a single value to be emitted.
     * @return the new FlowableTransformer instance
     * @since 0.16.2
     */
    public static <T, R> FlowableTransformer<T, R> mapAsync(Function<? super T, ? extends Publisher<? extends R>> mapper) {
        return mapAsync(mapper, BiFunctionSecondIdentity.<T, R>instance(), Flowable.bufferSize());
    }

    /**
     * Maps each upstream value into a single value provided by a generated Publisher for that
     * input value to be emitted to downstream.
     * <p>Only the first item emitted by the inner Publisher's are considered. If
     * the inner Publisher is empty, no resulting item is generated for that input value.
     * <p>The inner Publishers are consumed in order and one at a time.
     * @param <T> the input value type
     * @param <R> the result value type
     * @param mapper the function that receives the upstream value and returns a Publisher
     * @param bufferSize the internal buffer size and prefetch amount to buffer items from
     * upstream until their turn comes up
     * that should emit a single value to be emitted.
     * @return the new FlowableTransformer instance
     * @since 0.16.2
     */
    public static <T, R> FlowableTransformer<T, R> mapAsync(Function<? super T, ? extends Publisher<? extends R>> mapper, int bufferSize) {
        return mapAsync(mapper, BiFunctionSecondIdentity.<T, R>instance(), bufferSize);
    }

    /**
     * Maps each upstream value into a single value provided by a generated Publisher for that
     * input value and combines the original and generated single value into a final result item
     * to be emitted to downstream.
     * <p>Only the first item emitted by the inner Publisher's are considered. If
     * the inner Publisher is empty, no resulting item is generated for that input value.
     * <p>The inner Publishers are consumed in order and one at a time.
     * @param <T> the input value type
     * @param <U> the intermediate value type
     * @param <R> the result value type
     * @param mapper the function that receives the upstream value and returns a Publisher
     * that should emit a single value to be emitted.
     * @param combiner the bi-function that receives the original upstream value and the
     * single value emitted by the Publisher and returns a result value to be emitted to
     * downstream.
     * @return the new FlowableTransformer instance
     * @since 0.16.2
     */
    public static <T, U, R> FlowableTransformer<T, R> mapAsync(Function<? super T, ? extends Publisher<? extends U>> mapper, BiFunction<? super T, ? super U, ? extends R> combiner) {
        return mapAsync(mapper, combiner, Flowable.bufferSize());
    }

    /**
     * Maps each upstream value into a single value provided by a generated Publisher for that
     * input value and combines the original and generated single value into a final result item
     * to be emitted to downstream.
     * <p>Only the first item emitted by the inner Publisher's are considered. If
     * the inner Publisher is empty, no resulting item is generated for that input value.
     * <p>The inner Publishers are consumed in order and one at a time.
     * @param <T> the input value type
     * @param <U> the intermediate value type
     * @param <R> the result value type
     * @param mapper the function that receives the upstream value and returns a Publisher
     * that should emit a single value to be emitted.
     * @param combiner the bi-function that receives the original upstream value and the
     * single value emitted by the Publisher and returns a result value to be emitted to
     * downstream.
     * @param bufferSize the internal buffer size and prefetch amount to buffer items from
     * upstream until their turn comes up
     * @return the new FlowableTransformer instance
     * @since 0.16.2
     */
    public static <T, U, R> FlowableTransformer<T, R> mapAsync(Function<? super T, ? extends Publisher<? extends U>> mapper, BiFunction<? super T, ? super U, ? extends R> combiner, int bufferSize) {
        ObjectHelper.requireNonNull("mapper", "mapper is null");
        ObjectHelper.requireNonNull("combiner", "combiner is null");
        ObjectHelper.verifyPositive(bufferSize, "bufferSize");
        return new FlowableMapAsync<T, U, R>(null, mapper, combiner, bufferSize);
    }

    /**
     * Maps each upstream value into a single {@code true} or {@code false} value provided by a generated Publisher for that
     * input value and emits the input value if the inner Publisher returned {@code true}.
     * <p>Only the first item emitted by the inner Publisher's are considered. If
     * the inner Publisher is empty, no resulting item is generated for that input value.
     * <p>The inner Publishers are consumed in order and one at a time.
     * @param <T> the input and output value type
     * @param asyncPredicate the function that receives the upstream value and returns
     * a Publisher that should emit a single truee to indicate the original value should pass.
     * @return the new FlowableTransformer instance
     * @since 0.16.2
     */
    public static <T> FlowableTransformer<T, T> filterAsync(Function<? super T, ? extends Publisher<Boolean>> asyncPredicate) {
        return filterAsync(asyncPredicate, Flowable.bufferSize());
    }

    /**
     * Maps each upstream value into a single {@code true} or {@code false} value provided by a generated Publisher for that
     * input value and emits the input value if the inner Publisher returned {@code true}.
     * <p>Only the first item emitted by the inner Publisher's are considered. If
     * the inner Publisher is empty, no resulting item is generated for that input value.
     * <p>The inner Publishers are consumed in order and one at a time.
     * @param <T> the input and output value type
     * @param asyncPredicate the function that receives the upstream value and returns
     * a Publisher that should emit a single truee to indicate the original value should pass.
     * @param bufferSize the internal buffer size and prefetch amount to buffer items from
     * upstream until their turn comes up
     * @return the new FlowableTransformer instance
     * @since 0.16.2
     */
    public static <T> FlowableTransformer<T, T> filterAsync(Function<? super T, ? extends Publisher<Boolean>> asyncPredicate, int bufferSize) {
        ObjectHelper.requireNonNull("combiner", "combiner is null");
        ObjectHelper.verifyPositive(bufferSize, "bufferSize");
        return new FlowableFilterAsync<T>(null, asyncPredicate, bufferSize);
    }


    /**
     * Connects to the upstream ConnectableFlowable if the number of subscribed
     * subscriber reaches the specified count and disconnect if all subscribers have unsubscribed.
     * <p>
     * When applying this transformer via {@link Flowable#compose(FlowableTransformer)}
     * and the upstream is not a {@code ConnectableFlowable}, an {@code IllegalArgumentException}
     * is thrown.
     * @param <T> the value type
     * @param subscriberCount the number of subscribers required to connect to the upstream
     * @return the new FlowableTransformer instance
     * @since 0.17.0
     */
    public static <T> FlowableTransformer<T, T> refCount(int subscriberCount) {
        ObjectHelper.verifyPositive(subscriberCount, "subscriberCount");
        return refCount(subscriberCount, 0, TimeUnit.NANOSECONDS, Schedulers.computation());
    }


    /**
     * Connects to the upstream ConnectableFlowable if the number of subscribed
     * subscriber reaches 1 and disconnect after the specified
     * timeout if all subscribers have unsubscribed.
     * <p>
     * When applying this transformer via {@link Flowable#compose(FlowableTransformer)}
     * and the upstream is not a {@code ConnectableFlowable}, an {@code IllegalArgumentException}
     * is thrown.
     * @param <T> the value type
     * @param timeout the time to wait before disconnecting after all subscribers unsubscribed
     * @param unit the time unit of the timeout
     * @return the new FlowableTransformer instance
     * @since 0.17.0
     */
    public static <T> FlowableTransformer<T, T> refCount(long timeout, TimeUnit unit) {
        return refCount(1, timeout, unit, Schedulers.computation());
    }


    /**
     * Connects to the upstream ConnectableFlowable if the number of subscribed
     * subscriber reaches 1 and disconnect after the specified
     * timeout if all subscribers have unsubscribed.
     * <p>
     * When applying this transformer via {@link Flowable#compose(FlowableTransformer)}
     * and the upstream is not a {@code ConnectableFlowable}, an {@code IllegalArgumentException}
     * is thrown.
     * @param <T> the value type
     * @param timeout the time to wait before disconnecting after all subscribers unsubscribed
     * @param unit the time unit of the timeout
     * @param scheduler the target scheduler to wait on before disconnecting
     * @return the new FlowableTransformer instance
     * @since 0.17.0
     */
    public static <T> FlowableTransformer<T, T> refCount(long timeout, TimeUnit unit, Scheduler scheduler) {
        return refCount(1, timeout, unit, scheduler);
    }


    /**
     * Connects to the upstream ConnectableFlowable if the number of subscribed
     * subscriber reaches the specified count and disconnect after the specified
     * timeout if all subscribers have unsubscribed.
     * <p>
     * When applying this transformer via {@link Flowable#compose(FlowableTransformer)}
     * and the upstream is not a {@code ConnectableFlowable}, an {@code IllegalArgumentException}
     * is thrown.
     * @param <T> the value type
     * @param subscriberCount the number of subscribers required to connect to the upstream
     * @param timeout the time to wait before disconnecting after all subscribers unsubscribed
     * @param unit the time unit of the timeout
     * @return the new FlowableTransformer instance
     * @since 0.17.0
     */
    public static <T> FlowableTransformer<T, T> refCount(int subscriberCount, long timeout, TimeUnit unit) {
        return refCount(subscriberCount, timeout, unit, Schedulers.computation());
    }

    /**
     * Connects to the upstream ConnectableFlowable if the number of subscribed
     * subscriber reaches the specified count and disconnect after the specified
     * timeout if all subscribers have unsubscribed.
     * <p>
     * When applying this transformer via {@link Flowable#compose(FlowableTransformer)}
     * and the upstream is not a {@code ConnectableFlowable}, an {@code IllegalArgumentException}
     * is thrown.
     * @param <T> the value type
     * @param subscriberCount the number of subscribers required to connect to the upstream
     * @param timeout the time to wait before disconnecting after all subscribers unsubscribed
     * @param unit the time unit of the timeout
     * @param scheduler the target scheduler to wait on before disconnecting
     * @return the new FlowableTransformer instance
     * @since 0.17.0
     */
    public static <T> FlowableTransformer<T, T> refCount(int subscriberCount, long timeout, TimeUnit unit, Scheduler scheduler) {
        ObjectHelper.requireNonNull(unit, "unit is null");
        ObjectHelper.requireNonNull(scheduler, "scheduler is null");
        return new FlowableRefCountTimeout<T>(null, subscriberCount, timeout, unit, scheduler);
    }

    /**
     * Coalesces items from upstream into a container via a consumer and emits the container if
     * there is a downstream demand, otherwise it keeps coalescing into the same container.
     * @param <T> the upstream value type
     * @param <R> the container and result type
     * @param containerSupplier the function called and should return a fresh container to coalesce into
     * @param coalescer the consumer receiving the current container and upstream item to handle
     * @return the new FlowableTransformer instance
     * @since 0.17.3
     */
    public static <T, R> FlowableTransformer<T, R> coalesce(Callable<R> containerSupplier, BiConsumer<R, T> coalescer) {
        return coalesce(containerSupplier, coalescer, Flowable.bufferSize());
    }

    /**
     * Coalesces items from upstream into a container via a consumer and emits the container if
     * there is a downstream demand, otherwise it keeps coalescing into the same container.
     * @param <T> the upstream value type
     * @param <R> the container and result type
     * @param containerSupplier the function called and should return a fresh container to coalesce into
     * @param coalescer the consumer receiving the current container and upstream item to handle
     * @param bufferSize the island size of the internal unbounded buffer
     * @return the new FlowableTransformer instance
     * @since 0.17.3
     */
    public static <T, R> FlowableTransformer<T, R> coalesce(Callable<R> containerSupplier, BiConsumer<R, T> coalescer, int bufferSize) {
        ObjectHelper.requireNonNull(containerSupplier, "containerSupplier is null");
        ObjectHelper.requireNonNull(coalescer, "coalescer is null");
        ObjectHelper.verifyPositive(bufferSize, "bufferSize");
        return new FlowableCoalesce<T, R>(null, containerSupplier, coalescer, bufferSize);
    }

    /**
     * Emits elements into a Flowable window while the given predicate returns true. If the
     * predicate returns false, a new Flowable window is emitted.
     * @param <T> the source value type
     * @param predicate the predicate receiving the current value and if returns false,
     *                  a new window is created with the specified item
     * @return the new FlowableTransformer instance
     *
     * @since 0.17.7
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    @BackpressureSupport(BackpressureKind.FULL)
    public static <T> FlowableTransformer<T, Flowable<T>> windowWhile(Predicate<? super T> predicate) {
        return windowWhile(predicate, Flowable.bufferSize());
    }

    /**
     * Emits elements into a Flowable window while the given predicate returns true. If the
     * predicate returns false, a new Flowable window is emitted.
     * @param <T> the source value type
     * @param predicate the predicate receiving the current value and if returns false,
     *                  a new window is created with the specified item
     * @param bufferSize the buffer size hint (the chunk size of the underlying unbounded buffer)
     * @return the new FlowableTransformer instance
     *
     * @since 0.17.7
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    @BackpressureSupport(BackpressureKind.FULL)
    public static <T> FlowableTransformer<T, Flowable<T>> windowWhile(final Predicate<? super T> predicate, int bufferSize) {
        return new FlowableWindowPredicate<T>(null, predicate, FlowableWindowPredicate.Mode.BEFORE, bufferSize);
    }

    /**
     * Emits elements into a Flowable window until the given predicate returns true at which
     * point a new Flowable window is emitted.
     * @param <T> the source value type
     * @param predicate the predicate receiving the current item and if returns true,
     *                  the current window is completed and a new window is emitted
     * @return the new FlowableTransformer instance
     *
     * @since 0.17.7
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    @BackpressureSupport(BackpressureKind.FULL)
    public static <T> FlowableTransformer<T, Flowable<T>> windowUntil(Predicate<? super T> predicate) {
        return windowUntil(predicate, Flowable.bufferSize());
    }


    /**
     * Emits elements into a Flowable window until the given predicate returns true at which
     * point a new Flowable window is emitted.
     * @param <T> the source value type
     * @param predicate the predicate receiving the current item and if returns true,
     *                  the current window is completed and a new window is emitted
     * @param bufferSize the buffer size hint (the chunk size of the underlying unbounded buffer)
     * @return the new Flowable instance
     *
     * @since 0.17.7
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    @BackpressureSupport(BackpressureKind.FULL)
    public static <T> FlowableTransformer<T, Flowable<T>> windowUntil(Predicate<? super T> predicate, int bufferSize) {
        return new FlowableWindowPredicate<T>(null, predicate, FlowableWindowPredicate.Mode.AFTER, bufferSize);
    }

    /**
     * Emits elements into a Flowable window until the given predicate returns true at which
     * point a new Flowable window is emitted; the particular item will be dropped.
     * @param <T> the source value type
     * @param predicate the predicate receiving the current item and if returns true,
     *                  the current window is completed and a new window is emitted
     * @return the new FlowableTransformer instance
     *
     * @since 0.17.7
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    @BackpressureSupport(BackpressureKind.FULL)
    public static <T> FlowableTransformer<T, Flowable<T>> windowSplit(Predicate<? super T> predicate) {
        return windowSplit(predicate, Flowable.bufferSize());
    }


    /**
     * Emits elements into a Flowable window until the given predicate returns true at which
     * point a new Flowable window is emitted; the particular item will be dropped.
     * @param <T> the source value type
     * @param predicate the predicate receiving the current item and if returns true,
     *                  the current window is completed and a new window is emitted
     * @param bufferSize the buffer size hint (the chunk size of the underlying unbounded buffer)
     * @return the new Flowable instance
     *
     * @since 0.17.7
     */
    @SchedulerSupport(SchedulerSupport.NONE)
    @BackpressureSupport(BackpressureKind.FULL)
    public static <T> FlowableTransformer<T, Flowable<T>> windowSplit(Predicate<? super T> predicate, int bufferSize) {
        return new FlowableWindowPredicate<T>(null, predicate, FlowableWindowPredicate.Mode.SPLIT, bufferSize);
    }
}
