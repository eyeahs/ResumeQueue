# ResumeQueue

https://github.com/MFlisar/RXBus에서 Fork함

Observable의 chain에 
    .lift(RxResumeQueue.<Long>create(resumeStateProvider))
    
를 추가하면

1) resumeStateProvider#isResumeState == true 이면 downstream으로 계속 진행

2) resumeStateProvider#isResumeState == false 이면 
  life가 받는 upstream이 발행한 항목들을 RxValue의 queue에 저장.
  이후 ResumeStateProvider#onResumeChanged() 콜백을 받 으 면 queue에 저장된 항목들이 downstream으로 순차적 발행.
  
Observable의 작업이 onResume~onPause사이에서만 계속 진행되고, 그 외의 Life cycle에서는 대기되길 원할 때 적용 가능.
