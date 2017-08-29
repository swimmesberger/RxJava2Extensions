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

import java.util.concurrent.atomic.*;

import org.reactivestreams.*;

import io.reactivex.*;
import io.reactivex.exceptions.Exceptions;
import io.reactivex.functions.Predicate;
import io.reactivex.internal.functions.ObjectHelper;
import io.reactivex.internal.fuseable.ConditionalSubscriber;
import io.reactivex.internal.subscriptions.*;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.processors.UnicastProcessor;

/**
 * Emit into the same window while the predicate returns true or
 * emit into the same window until the predicate returns true.
 *
 * @param <T> the source value type
 * @author Martin Nowak
 * @since 0.17.7
 */
final class FlowableWindowPredicate<T> extends Flowable<Flowable<T>> implements FlowableTransformer<T, Flowable<T>> {

    enum Mode {
        /** The item triggering the new buffer will be part of the new buffer. */
        BEFORE,
        /** The item triggering the new buffer will be part of the old buffer. */
        AFTER,
        /** The item won't be part of any buffers. */
        SPLIT
    }

    final Publisher<T> source;

    final Predicate<? super T> predicate;

    final Mode mode;

    final int bufferSize;

    FlowableWindowPredicate(Publisher<T> source, Predicate<? super T> predicate, Mode mode,
            int bufferSize) {
        this.source = source;
        this.predicate = predicate;
        this.mode = mode;
        this.bufferSize = bufferSize;
    }

    @Override
    protected void subscribeActual(Subscriber<? super Flowable<T>> s) {
        source.subscribe(new WindowPredicateSubscriber<T>(s, predicate, mode, bufferSize));
    }

    @Override
    public Publisher<Flowable<T>> apply(Flowable<T> upstream) {
        return new FlowableWindowPredicate<T>(upstream, predicate, mode, bufferSize);
    }

    static final class WindowPredicateSubscriber<T>
    extends AtomicInteger
    implements ConditionalSubscriber<T>, Subscription, Runnable {

        final Subscriber<? super Flowable<T>> actual;

        final Predicate<? super T> predicate;

        final Mode mode;

        final int bufferSize;

        final AtomicBoolean cancelled;

        Subscription s;

        UnicastProcessor<T> window;

        T pending; // look-ahead element for Mode.BEFORE

        WindowPredicateSubscriber(Subscriber<? super Flowable<T>> actual,
                Predicate<? super T> predicate, Mode mode,
                int bufferSize) {
            super(1);
            this.actual = actual;
            this.predicate = predicate;
            this.mode = mode;
            this.bufferSize = bufferSize;
            this.cancelled = new AtomicBoolean();
        }

        @Override
        public void onSubscribe(Subscription s) {
            if (SubscriptionHelper.validate(this.s, s)) {
                this.s = s;
                actual.onSubscribe(this);
            }
        }

        @Override
        public void onNext(T t) {
            if (!tryOnNext(t)) {
                s.request(1);
            }
        }

        @Override
        public boolean tryOnNext(T t) {
            boolean emittedWindow = false;
            UnicastProcessor<T> w = window;
            if (w == null) {
                // ignore additional items after last window is completed
                if (cancelled.get()) {
                    return true;
                }
                // emit next window
                w = newWindow();
                emittedWindow = true;
                // emit pending element that triggered the predicate
                if (mode == Mode.BEFORE && pending != null) {
                    w.onNext(pending);
                    pending = null;
                }
            }

            boolean b;

            try {
                // negate predicate for windowWhile
                b = predicate.test(t) ^ mode == Mode.BEFORE;
            } catch (Throwable ex) {
                Exceptions.throwIfFatal(ex);
                s.cancel();
                actual.onError(ex);
                w.onError(ex);
                window = null;
                return true;
            }

            if (b) {
                // element goes into current window
                if (mode == Mode.AFTER) {
                    w.onNext(t);
                }
                // finish current window
                w.onComplete();
                window = null;
                // element goes into the next requested window
                if (mode == Mode.BEFORE) {
                    pending = t;
                }
            } else {
                w.onNext(t);
            }
            return emittedWindow;
        }

        @Override
        public void onError(Throwable t) {
            Processor<T, T> w = window;
            if (w != null) {
                window = null;
                w.onError(t);
            }

            actual.onError(t);
        }

        @Override
        public void onComplete() {
            Processor<T, T> w = window;
            if (w != null) {
                window = null;
                w.onComplete();
            }

            actual.onComplete();
        }

        @Override
        public void request(long n) {
            s.request(n);
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                run();
            }
        }

        @Override
        public void run() {
            if (decrementAndGet() == 0) {
                s.cancel();
            }
        }

        private UnicastProcessor<T> newWindow() {
            getAndIncrement();
            UnicastProcessor<T> w = UnicastProcessor.<T>create(bufferSize, this);
            window = w;
            actual.onNext(w);
            return w;
        }
    }
}
