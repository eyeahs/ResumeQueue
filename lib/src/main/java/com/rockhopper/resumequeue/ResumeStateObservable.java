package com.rockhopper.resumequeue;

import rx.Observable;
import rx.Subscriber;

/**
 * Created by hyunwoopark on 2016. 9. 30..
 */

public class ResumeStateObservable {
	public static Observable<Boolean> create(final ResumeStateProvider provider) {
		return Observable.create(new Observable.OnSubscribe<Boolean>() {
			@Override
			public void call(final Subscriber<? super Boolean> subscriber) {
				ResumeStateListener resumedListener = new ResumeStateListener() {
					@Override
					public void onResumedChanged(boolean resumed) {
						if (subscriber.isUnsubscribed()) {
							provider.removeResumeStateListener(this);
						} else {
							subscriber.onNext(resumed);
						}
					}
				};
				provider.addResumeStateListener(resumedListener, false);
			}
		}).onBackpressureBuffer();
	}

	public interface ResumeStateListener {
		void onResumedChanged(boolean resumed);
	}
}
