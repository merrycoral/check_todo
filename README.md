# 루틴 체크 (check_todo)

영양제 먹기, 약 먹기, 카드 금액 납부하기처럼 **반복되는 할 일**을 등록하고 수행 여부를 체크하는 Android 앱입니다. (Kotlin · Jetpack Compose · Room)

## 기능

- 매일 / 매주(요일 선택) / 매월(일자 선택) 반복 할 일 등록
- 카테고리: 건강, 금융, 업무, 운동, 뷰티 기본 제공 + 직접 추가
- 지정한 시각에 푸시 알림. 알림을 꾹 눌러(또는 아래로 펼쳐) **수행 / 건너뛰기 / 1시간 후 다시알림** 선택
- "오늘" 탭에서 오늘 할 일을 체크하고 카테고리별로 필터링
- 재부팅·앱 업데이트·시간 변경 후 알람 자동 재등록

## 실행

Android Studio(Koala 이상)에서 이 폴더를 열고 실행하세요. (minSdk 26, targetSdk 35)

```
./gradlew assembleDebug
```

## 권한

- `POST_NOTIFICATIONS` (Android 13+): 앱 실행 시 요청
- `SCHEDULE_EXACT_ALARM` (Android 12+): 정해진 시각에 정확히 알림을 띄우기 위해 필요. 허용되지 않으면 앱 상단에 안내 배너가 뜨며, 설정에서 '알람 및 리마인더'를 허용하면 됩니다.

## 동작 메모

- 매월 29~31일을 지정했는데 그 날이 없는 달은 말일에 알림이 옵니다.
- 알림이 뜬 회차에 이미 수행/건너뛰기 기록이 있으면 알림을 다시 띄우지 않습니다.
- "1시간 후 다시알림"은 원래 회차(날짜)의 기록 기준으로 유지됩니다.

## 구조

```
app/src/main/java/com/merrycoral/checktodo/
├── data/       Room 엔티티·DAO·Repository, 반복 주기 계산(Recurrence.kt)
├── schedule/   AlarmManager 스케줄러, 알림, BroadcastReceiver들
└── ui/         Compose 화면(오늘 / 할 일 관리 / 편집)과 ViewModel
```
