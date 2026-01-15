# Chat Project

Kotlin + Spring Boot 기반의 실시간 분산 채팅 시스템

## 기술 스택

- **Language:** Kotlin 2.0
- **Framework:** Spring Boot 3.3.4
- **Database:** PostgreSQL / H2
- **Cache & Pub/Sub:** Redis
- **Real-time:** WebSocket

---

## 프로젝트 구조

```
chat-project/
├── chat-application/     # 메인 애플리케이션 (조립)
├── chat-domain/          # 도메인 모델, DTO, 서비스 인터페이스
├── chat-persistence/     # 데이터 접근 (JPA, Redis, WebSocket)
```

---

## 분산 채팅 아키텍처

### 왜 분산 시스템이 필요한가?

서버가 여러 대일 때 문제가 생긴다:

```
서버A에 접속: 유저1, 유저2
서버B에 접속: 유저3, 유저4
```

유저1이 메시지를 보내면 서버A만 안다. 서버B에 있는 유저3, 유저4는 어떻게 받을까?

**해결책: Redis Pub/Sub**

---

### 메시지 전달 흐름

```
1. 서버A에 유저1 접속 → 방123 입장 → 서버A가 "chat.room.123" 구독

2. 서버B에 유저2가 방123에서 "안녕" 전송
   → 서버B가 Redis에 publish (broadcastToRoom)

3. 서버A가 onMessage로 수신
   → localMessageHandler.invoke()
   → 유저1에게 "안녕" 전달
```

### 아키텍처 다이어그램

```
┌─────────────────┐                      ┌─────────────────┐
│     서버A       │                      │     서버B       │
│                 │                      │                 │
│  유저1 (방123)  │                      │  유저2 (방123)  │
│       │        │                      │       │        │
│       │        │    ┌─────────┐       │       │        │
│       │        │◀───│  Redis  │◀──────│  "안녕" 전송   │
│       ▼        │    │ Pub/Sub │       │  (broadcast)   │
│  onMessage()   │    └─────────┘       │                │
│       │        │                      │                │
│       ▼        │                      │                │
│  유저1에게     │                      │                │
│  "안녕" 전달   │                      │                │
└─────────────────┘                      └─────────────────┘
```

### 핵심 컴포넌트

| 컴포넌트 | 역할 |
|----------|------|
| `RedisMessageBroker` | Redis Pub/Sub 관리 (구독, 발행, 수신) |
| `WebSocketSessionManager` | WebSocket 연결 관리, 로컬 유저에게 메시지 전달 |
| `MessageSequenceService` | 메시지 순서 보장을 위한 시퀀스 번호 생성 |

### 핵심 메서드

| 단계 | 메서드 | 역할 |
|------|--------|------|
| 구독 | `subscribeToRoom()` | Redis 채널 리스닝 시작 |
| 발행 | `broadcastToRoom()` | 모든 서버에 메시지 전파 |
| 수신 | `onMessage()` | Redis에서 메시지 받음 |
| 전달 | `localMessageHandler.invoke()` | 내 서버 유저들에게 실제 전송 |

---

## RedisTemplate 사용법

### 자료구조별 메서드

| 메서드 | Redis 자료구조 | 용도 |
|--------|---------------|------|
| `opsForValue()` | String | 단순 값 저장, 카운터 |
| `opsForSet()` | Set | 중복 없는 집합 |
| `opsForList()` | List | 순서 있는 목록 |
| `opsForHash()` | Hash | 필드별 저장 |
| `opsForZSet()` | Sorted Set | 점수로 정렬 |

### 프로젝트에서 사용하는 패턴

**1. 시퀀스 번호 생성 (opsForValue)**
```kotlin
redisTemplate.opsForValue().increment(key)  // 원자적 증가: 1, 2, 3...
```

**2. 서버별 구독 방 관리 (opsForSet)**
```kotlin
redisTemplate.opsForSet().add(key, roomId)           // 방 추가
redisTemplate.opsForSet().isMember(key, roomId)      // 구독 중인지 확인
redisTemplate.opsForSet().members(key)               // 전체 조회
```

**3. 메시지 발행 (Pub/Sub)**
```kotlin
redisTemplate.convertAndSend("chat.room.123", message)
```

---

## WebSocket 세션 관리

### WebSocketSession이란?

클라이언트와 서버 사이의 **열린 연결(파이프)**

```
유저1 (크롬 탭1)  ────WebSocketSession────┐
                                         │
유저1 (크롬 탭2)  ────WebSocketSession────┼───▶  서버
                                         │
유저1 (모바일앱)  ────WebSocketSession────┘
```

한 유저가 여러 기기/탭으로 접속하면 세션이 여러 개 생김.

### HTTP vs WebSocket

| HTTP | WebSocket |
|------|-----------|
| 요청-응답 후 연결 끊김 | 연결 유지 |
| 클라이언트만 요청 가능 | 서버도 먼저 메시지 전송 가능 |
| 새 메시지 확인하려면 폴링 필요 | 실시간 푸시 가능 |

---

## 메시지 순서 보장

### 문제

네트워크 상황에 따라 메시지 순서가 뒤바뀔 수 있다:
```
유저A: "안녕" (먼저 보냄) → DB 저장 순서: 2번
유저B: "뭐해?" (나중에 보냄) → DB 저장 순서: 1번
```

### 해결: Redis INCR

```kotlin
fun getNextSequence(chatRoomId: String): Long {
    return redisTemplate.opsForValue().increment("chat:sequence:$chatRoomId") ?: 1L
}
```

- `INCR`은 **원자적(atomic)** - 동시에 100명이 요청해도 절대 같은 번호 안 줌
- 메시지마다 고유한 `sequenceNumber` 부여
- 클라이언트는 `sequenceNumber` 순서로 정렬해서 표시

---

## 중복 메시지 방지

### 문제

네트워크 문제로 같은 메시지가 두 번 올 수 있다.

### 해결: 메시지 ID 추적

```kotlin
private val processedMessages = ConcurrentHashMap<String, Long>()

override fun onMessage(message: Message, pattern: ByteArray?) {
    // 이미 처리한 메시지면 무시
    if (processedMessages.containsKey(distributedMessage.id)) {
        return
    }

    // 처리 후 기록
    processedMessages[distributedMessage.id] = System.currentTimeMillis()
}
```

---

## 실행 방법

```bash
# Redis 실행 (Docker)
docker run -d -p 6379:6379 redis

# 애플리케이션 실행
./gradlew :chat-application:bootRun
```

---

## 추가 문서

- [CLAUDE.md](./CLAUDE.md) - 클래스별 상세 설명, Spring/JPA/Redis 개념 정리
