package com.rockhopper.resumequeue;

import java.util.HashMap;

import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import rx.subjects.SerializedSubject;

/**
 * Created by Prometheus on 22.04.2016.
 */
public class RxBus {

	//region SINGLETON
	private static RxBus INSTANCE = new RxBus();

	public static RxBus get() {
		return INSTANCE;
	}
	//endregion

	// for better speed, we use different maps => no wrapper key generation needed if you just want to use a default class based bus
	private HashMap<Class<?>, SerializedSubject> mSubjectsClasses = new HashMap<>();

	//region Send Event

	/**
	 * Sends an event to the bus
	 * ATTENTION: all observers that are observing the class of the event will retrieve it
	 * <p>
	 * @param  event  the event that should be broadcasted to the bus
	 */
	public synchronized <T> void sendEvent(T event) {
		if (event == null) {
			throw new RuntimeException("Null event");
		}

		SerializedSubject subject = getSubject(event.getClass(), false);
		// only send event, if subject exists => this means someone has at least once subscribed to it
		if (subject != null) {
			subject.onNext(event);
		}
	}
	//endregion

	//region Observe event

	/**
	 * Get an observable that observes all events of the the class the
	 * <p>
	 * @param  eventClass  the class of event you want to observe
	 * @return an Observable, that will observe all events of the @param key class
	 */
	public <T> Observable<T> observeEvent(Class<T> eventClass) {
		if (eventClass == null) {
			throw new RuntimeException("Null event");
		}

		return getSubject(eventClass, true);
	}
	//endregion

	private synchronized SerializedSubject getSubject(Class<?> key, boolean createIfMissing) {
		// 1) look if key already has a publisher subject, if so, return it
		if (mSubjectsClasses.containsKey(key))
			return mSubjectsClasses.get(key);
			// 2) else, create a new one and put it into the map
		else if (createIfMissing) {
			SerializedSubject<?, ?> subject = new SerializedSubject<>(PublishSubject.create());
			mSubjectsClasses.put(key, subject);
			return subject;
		} else
			return null;
	}

	public static class Builder<T, O> {
		private Class<T> mKeyClass;
		private int mValvePrefetch = 1000;
		private ResumeStateProvider mIsResumedProvider;
		private Action1<? super O> mActionNext;
		private Action1<Throwable> mActionError;
		private Action0 mActionOnComplete;
		private Observer<? super O> mSubscriptionObserver;
		private Subscriber<? super O> mSubscriber;

		private static final Observable.Transformer defaultSchedulers =
			new Observable.Transformer<Observable, Observable>() {
				@SuppressWarnings("unchecked")
				@Override
				public Observable call(Observable observable) {
					return observable
						.subscribeOn(Schedulers.io())
						.observeOn(AndroidSchedulers.mainThread());
				}
			};

		public static <T> Builder<T, T> create(Class<T> eventClass) {
			return new Builder<>(eventClass);
		}

		private Builder(Class<T> eventClass) {
			mKeyClass = eventClass;
			init();
		}

		private void init() {
			mIsResumedProvider = null;
			mActionNext = null;
			mActionError = null;
			mActionOnComplete = null;
			mSubscriptionObserver = null;
			mSubscriber = null;
		}

		public Builder<T, O> setResumeStateProvider(ResumeStateProvider isResumedProvider) {
			mIsResumedProvider = isResumedProvider;
			return this;
		}

		public Builder<T, O> withOnNext(Action1<O> actionNext) {
			mActionNext = actionNext;
			return this;
		}

		public Builder<T, O> withOnError(Action1<Throwable> actionError) {
			mActionError = actionError;
			return this;
		}

		public Builder<T, O> withOnComplete(Action0 actionComplete) {
			mActionOnComplete = actionComplete;
			return this;
		}

		public Builder<T, O> withObserver(Observer<O> subscriptionObserver) {
			mSubscriptionObserver = subscriptionObserver;
			return this;
		}

		public Builder<T, O> withSubscriber(Subscriber<? super O> subscriber) {
			mSubscriber = subscriber;
			return this;
		}

		@SuppressWarnings("unchecked")
		private Observable<O> buildObservable() {
			return RxBus.get()
				.observeEvent(mKeyClass)
				.onBackpressureBuffer()
				.lift(RxResumeQueue.create(mIsResumedProvider))
				.compose(defaultSchedulers);
		}

		public Subscription buildSubscription() {
			Observable<O> observable = buildObservable();

			if (mSubscriber == null && mSubscriptionObserver == null && mActionNext == null) {
				throw new RuntimeException(
					"Subscription can't be build, because no next action, subscriber nor observable was set!");
			}

			if (mSubscriber == null && mSubscriptionObserver == null) {
				Action1<? super O> actionNext = Wrapper.wrapQueueAction(mActionNext, mIsResumedProvider);

				if (mActionError != null && mActionOnComplete != null) {
					return observable.subscribe(actionNext, mActionError, mActionOnComplete);
				} else if (mActionError != null) {
					return observable.subscribe(actionNext, mActionError);
				}
				return observable.subscribe(actionNext);
			} else if (mSubscriber == null) {
				Observer<? super O> subscriptionObserver =
					Wrapper.wrapObserver(mSubscriptionObserver, mIsResumedProvider);
				return observable.subscribe(subscriptionObserver);
			} else if (mSubscriptionObserver == null) {
				Subscriber<? super O> subscriber = Wrapper.wrapSubscriber(mSubscriber, mIsResumedProvider);
				return observable.subscribe(subscriber);
			} else {
				throw new RuntimeException(
					"Subscription can't be build, because you have set more than one of following: nnext action, subscriber or observable!");
			}
		}
	}

	private static class Wrapper {
		static <T> boolean safetyQueueCheck(T event, ResumeStateProvider isResumedProvider) {
			if (isResumedProvider.isResumeState()) {
				return true;
			} else {
				get().sendEvent(event);
				return false;
			}
		}

		static <T> Action1<T> wrapQueueAction(final Action1<T> action, final ResumeStateProvider isResumedProvider) {
			return new Action1<T>() {
				@Override
				public void call(T t) {
					if (safetyQueueCheck(t, isResumedProvider)) {
						action.call(t);
					}
				}
			};
		}

		static <T> Observer<T> wrapObserver(final Observer<T> observer, final ResumeStateProvider isResumedProvider) {
			return new Observer<T>() {
				@Override
				public void onCompleted() {
					observer.onCompleted();
				}

				@Override
				public void onError(Throwable e) {
					observer.onError(e);
				}

				@Override
				public void onNext(T t) {
					if (safetyQueueCheck(t, isResumedProvider))
						observer.onNext(t);
				}
			};
		}

		static <T> Subscriber<T> wrapSubscriber(final Subscriber<T> subscriber,
			final ResumeStateProvider isResumedProvider) {
			return new Subscriber<T>() {
				@Override
				public void onCompleted() {
					subscriber.onCompleted();
				}

				@Override
				public void onError(Throwable e) {
					subscriber.onError(e);
				}

				@Override
				public void onNext(T t) {
					if (safetyQueueCheck(t, isResumedProvider))
						subscriber.onNext(t);
				}
			};
		}
	}
}