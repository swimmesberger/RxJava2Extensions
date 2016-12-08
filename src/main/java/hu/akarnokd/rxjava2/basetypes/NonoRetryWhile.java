/*
 * Copyright 2016 David Karnok
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

package hu.akarnokd.rxjava2.basetypes;

import java.util.concurrent.atomic.AtomicReference;

import org.reactivestreams.*;

import io.reactivex.exceptions.*;
import io.reactivex.functions.Predicate;
import io.reactivex.internal.subscriptions.SubscriptionHelper;

/**
 * Retry while the predicate returns true.
 */
final class NonoRetryWhile extends Nono {

    final Nono source;

    final Predicate<? super Throwable> predicate;

    NonoRetryWhile(Nono source, Predicate<? super Throwable> predicate) {
        this.source = source;
        this.predicate = predicate;
    }

    @Override
    protected void subscribeActual(Subscriber<? super Void> s) {
        source.subscribe(new RetryUntilSubscriber(s, predicate, source));
    }

    static final class RetryUntilSubscriber extends BasicNonoIntQueueSubscription
    implements Subscriber<Void> {

        private static final long serialVersionUID = -3208438978515192633L;

        protected final Subscriber<? super Void> actual;

        final Nono source;

        final Predicate<? super Throwable> predicate;

        final AtomicReference<Subscription> s;

        volatile boolean active;

        boolean once;

        RetryUntilSubscriber(Subscriber<? super Void> actual,
                Predicate<? super Throwable> predicate, Nono source) {
            this.actual = actual;
            this.predicate = predicate;
            this.source = source;
            this.s = new AtomicReference<Subscription>();
        }

        @Override
        public void cancel() {
            SubscriptionHelper.cancel(s);
        }

        @Override
        public void onSubscribe(Subscription s) {
            SubscriptionHelper.replace(this.s, s);
            if (!once) {
                once = true;
                actual.onSubscribe(this);
            }
        }

        @Override
        public void onNext(Void t) {
            // never called
        }

        @Override
        public void onError(Throwable t) {
            boolean b;

            try {
                b = predicate.test(t);
            } catch (Throwable ex) {
                Exceptions.throwIfFatal(ex);
                actual.onError(new CompositeException(t, ex));
                return;
            }

            if (!b) {
                actual.onError(t);
            } else {
                active = false;
                if (getAndIncrement() == 0) {
                    do {
                        if (SubscriptionHelper.isCancelled(s.get())) {
                            return;
                        }

                        if (!active) {
                            active = true;
                            source.subscribe(this);
                        }
                    } while (decrementAndGet() != 0);
                }
            }
        }

        @Override
        public void onComplete() {
            actual.onComplete();
        }
    }
}
