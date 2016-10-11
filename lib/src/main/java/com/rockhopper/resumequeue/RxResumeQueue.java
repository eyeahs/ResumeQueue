package com.rockhopper.resumequeue;

import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import rx.Observable;
import rx.Observable.Operator;
import rx.Producer;
import rx.Subscriber;
import rx.Subscription;
import rx.exceptions.MissingBackpressureException;
import rx.internal.operators.BackpressureUtils;
import rx.internal.operators.NotificationLite;
import rx.internal.util.ExceptionsUtils;
import rx.internal.util.atomic.SpscAtomicArrayQueue;
import rx.plugins.RxJavaHooks;

// http://akarnokd.blogspot.kr/2016/03/subscribeon-and-observeon.html
// From https://gist.github.com/akarnokd/1c54e5a4f64f9b1e46bdcf62b4222f08
public final class RxResumeQueue {

	public static <T> RxValve<T> create(final ResumeStateProvider provider) {
		return new RxValve<>(Observable.create(new Observable.OnSubscribe<Boolean>() {
			@Override
			public void call(final Subscriber<? super Boolean> subscriber) {
				if (!subscriber.isUnsubscribed()) {
					provider.addResumeStateListener(new ResumeStateProvider.ResumeStateListener() {
						@Override
						public void onResumedChanged(Iterator iterator, boolean resumed) {
							if (subscriber.isUnsubscribed() && iterator != null) {
								iterator.remove();
							} else {
								subscriber.onNext(resumed);
							}
						}
					});
				}
			}
		}).onBackpressureBuffer(), provider.isResumeState());
	}

	private static class RxValve<T> implements Operator<T, T> {
		final Observable<Boolean> other;
		final int prefetch;
		final boolean defaultState;

		RxValve(Observable<Boolean> other, boolean defaultState) {
			this(other, 1000, defaultState);
		}

		RxValve(Observable<Boolean> other, int prefetch, boolean defaultState) {
			this.other = other;
			this.prefetch = prefetch;
			this.defaultState = defaultState;
		}

		@Override
		public Subscriber<? super T> call(Subscriber<? super T> child) {
			final ValveSubscriber<T> valveSubscriber = new ValveSubscriber<>(child, prefetch, defaultState);
			final OtherSubscriber otherSubscriber = new OtherSubscriber(valveSubscriber);

			valveSubscriber.otherSubscription = otherSubscriber;

			child.add(valveSubscriber);
			child.add(otherSubscriber);
			child.setProducer(new Producer() {
				@Override
				public void request(long n) {
					valveSubscriber.requestInner(n);
				}
			});

			other.subscribe(otherSubscriber);

			return valveSubscriber;
		}

		private static final class ValveSubscriber<T> extends Subscriber<T> {
			final Subscriber<? super T> actual;
			final int limit;
			final AtomicLong requested;
			final AtomicInteger wip;
			final AtomicReference<Throwable> error;
			final Queue<Object> queue;
			final NotificationLite<T> nl;
			volatile boolean valveOpen;
			volatile boolean done;
			Subscription otherSubscription;
			long emission;

			ValveSubscriber(Subscriber<? super T> actual, int prefetch, boolean defaultState) {
				this.actual = actual;
				this.limit = prefetch - (prefetch >> 2);
				this.requested = new AtomicLong();
				this.wip = new AtomicInteger();
				this.error = new AtomicReference<>();
				this.queue = new SpscAtomicArrayQueue<>(prefetch);
				this.nl = NotificationLite.instance();
				this.valveOpen = defaultState;
				request(prefetch);
			}

			@Override
			public void onNext(T t) {
				if (!queue.offer(nl.next(t))) {
					onError(new MissingBackpressureException());
				} else {
					drain();
				}
			}

			@Override
			public void onError(Throwable e) {
				if (ExceptionsUtils.addThrowable(error, e)) {
					otherSubscription.unsubscribe();
					done = true;
					drain();
				} else {
					RxJavaHooks.onError(e);
				}
			}

			@Override
			public void onCompleted() {
				done = true;
				drain();
			}

			void otherSignal(boolean state) {
				valveOpen = state;
				if (state) {
					drain();
				}
			}

			void otherError(Throwable e) {
				if (ExceptionsUtils.addThrowable(error, e)) {
					unsubscribe();
					done = true;
					drain();
				} else {
					RxJavaHooks.onError(e);
				}
			}

			void otherCompleted() {
				if (ExceptionsUtils.addThrowable(error, new IllegalStateException("Other completed unexpectedly"))) {
					unsubscribe();
					done = true;
					drain();
				}
			}

			void requestInner(long n) {
				if (n > 0) {
					BackpressureUtils.getAndAddRequest(requested, n);
					drain();
				} else if (n < 0) {
					throw new IllegalArgumentException("n >= 0 required but it was " + n);
				}
			}

			void drain() {
				if (wip.getAndIncrement() != 0) {
					return;
				}

				int missed = 1;

				for (; ; ) {

					if (valveOpen) {
						long r = requested.get();
						long e = emission;

						while (e != r && valveOpen) {
							if (actual.isUnsubscribed()) {
								return;
							}

							if (error.get() != null) {
								unsubscribe();
								otherSubscription.unsubscribe();
								queue.clear();
								Throwable ex = ExceptionsUtils.terminate(error);
								actual.onError(ex);
								return;
							}

							boolean d = done;
							Object o = queue.poll();
							boolean empty = o == null;

							if (d && empty) {
								otherSubscription.unsubscribe();
								actual.onCompleted();
								return;
							}

							if (empty) {
								break;
							}

							actual.onNext(nl.getValue(o));

							e++;
							if (e == limit) {
								r = BackpressureUtils.produced(requested, e);
								request(e);
								e = 0L;
							}
						}

						if (e == r) {
							if (actual.isUnsubscribed()) {
								return;
							}

							if (error.get() != null) {
								unsubscribe();
								otherSubscription.unsubscribe();
								queue.clear();
								Throwable ex = ExceptionsUtils.terminate(error);
								actual.onError(ex);
								return;
							}

							if (done && queue.isEmpty()) {
								otherSubscription.unsubscribe();
								actual.onCompleted();
								return;
							}
						}

						emission = e;
					} else {
						if (actual.isUnsubscribed()) {
							return;
						}
					}

					missed = wip.addAndGet(-missed);
					if (missed == 0) {
						break;
					}
				}
			}
		}

		private static final class OtherSubscriber extends Subscriber<Boolean> {
			final ValveSubscriber<?> valve;

			OtherSubscriber(ValveSubscriber<?> valve) {
				this.valve = valve;
			}

			@Override
			public void onNext(Boolean t) {
				valve.otherSignal(t);
			}

			@Override
			public void onError(Throwable e) {
				valve.otherError(e);
			}

			@Override
			public void onCompleted() {
				valve.otherCompleted();
			}
		}
	}
}