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

package hu.akarnokd.rxjava2.math;

import java.util.Comparator;

import org.reactivestreams.*;

import hu.akarnokd.rxjava2.util.DeferredScalarSubscriber;
import io.reactivex.internal.operators.flowable.FlowableSource;
import io.reactivex.internal.util.Exceptions;

final class FlowableMinMax<T> extends FlowableSource<T, T> {

    final Comparator<? super T> comparator;
    
    final int flag;
    
    public FlowableMinMax(Publisher<T> source, Comparator<? super T> comparator, int flag) {
        super(source);
        this.comparator = comparator;
        this.flag = flag;
    }

    @Override
    protected void subscribeActual(Subscriber<? super T> observer) {
        source.subscribe(new MinMaxSubscriber<T>(observer, comparator, flag));
    }
    
    static final class MinMaxSubscriber<T> extends DeferredScalarSubscriber<T, T> {

        /** */
        private static final long serialVersionUID = 600979972678601618L;
        
        final Comparator<? super T> comparator;
        
        final int flag;

        public MinMaxSubscriber(Subscriber<? super T> actual, Comparator<? super T> comparator, int flag) {
            super(actual);
            this.comparator = comparator; 
            this.flag = flag;
        }

        @Override
        public void onNext(T value) {
            try {
                if (hasValue) {
                    if (comparator.compare(this.value, value) * flag > 0) {
                        this.value = value;
                    }
                } else {
                    this.value = value;
                    hasValue = true;
                }
            } catch (Throwable ex) {
                Exceptions.throwIfFatal(ex);
                s.cancel();
                actual.onError(ex);
            }
        }
        
    }
}