# Chat Project Documentation

## Project Overview

A real-time chat application built with **Kotlin**, **Spring Boot 3.3.4**, **JPA**, **Redis**, and **WebSocket**. The project follows a multi-module architecture for clean separation of concerns.

---

## Project Structure

```
chat-project/
├── chat-application/     # Main application entry point (assembler module)
├── chat-domain/          # Domain models, DTOs, and service interfaces
├── chat-persistence/     # Data access layer (JPA repositories, Redis, WebSocket)
├── build.gradle.kts      # Root build configuration
└── settings.gradle.kts   # Module definitions
```

---

## Module Details

### 1. chat-application

The entry point module that assembles all other modules.

#### `ChatApplication.kt`
The main Spring Boot application class.

```kotlin
@SpringBootApplication(scanBasePackages = ["com.chat.application"])
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = ["com.chat.persistence.repository"])
@EntityScan(basePackages = ["com.chat.domain.model"])
class ChatApplication
```

**Spring Concepts:**
| Annotation | Description |
|------------|-------------|
| `@SpringBootApplication` | Combines `@Configuration`, `@EnableAutoConfiguration`, and `@ComponentScan`. The `scanBasePackages` tells Spring which packages to scan for beans. |
| `@EnableJpaAuditing` | Enables automatic population of `@CreatedDate` and `@LastModifiedDate` fields in entities. |
| `@EnableJpaRepositories` | Enables Spring Data JPA repositories in specified packages. Without this, `@Repository` interfaces won't be recognized. |
| `@EntityScan` | Tells JPA where to find entity classes. Required when entities are in a different module from the application class. |

---

### 2. chat-domain

Contains domain models (JPA entities), DTOs, and service interfaces.

#### Models (JPA Entities)

##### `User.kt`
Represents a user in the system.

| Field | Type | Description |
|-------|------|-------------|
| `id` | Long | Primary key (auto-generated) |
| `username` | String | Unique login identifier |
| `password` | String | User password |
| `displayName` | String | Display name shown to others |
| `profileImageUrl` | String? | Optional profile image URL |
| `status` | String? | User status message |
| `isActive` | Boolean | Whether user is active |
| `lastSeenAt` | LocalDateTime? | Last online timestamp |
| `createdAt` | LocalDateTime | Auto-populated creation time |
| `updatedAt` | LocalDateTime | Auto-populated update time |

**Spring/JPA Concepts:**
| Annotation | Description |
|------------|-------------|
| `@Entity` | Marks this class as a JPA entity (maps to a database table) |
| `@Table(name = "app_users")` | Specifies the table name. "app_users" avoids conflicts with reserved keyword "users" in some databases |
| `@EntityListeners(AuditingEntityListener::class)` | Required for `@CreatedDate` and `@LastModifiedDate` to work |
| `@Id` | Marks the primary key field |
| `@GeneratedValue(strategy = GenerationType.IDENTITY)` | Auto-increment strategy for primary key |
| `@Column(unique = true)` | Creates a unique constraint on the column |
| `@NotBlank` | Bean Validation - field cannot be null or empty string |
| `@CreatedDate` | JPA Auditing - auto-populates with creation timestamp |
| `@LastModifiedDate` | JPA Auditing - auto-updates on every save |

---

##### `ChatRoom.kt`
Represents a chat room.

| Field | Type | Description |
|-------|------|-------------|
| `id` | Long | Primary key |
| `name` | String | Room name |
| `description` | String? | Optional description |
| `type` | ChatRoomType | DIRECT / GROUP / CHANNEL |
| `imageUrl` | String? | Room image |
| `isActive` | Boolean | Whether room is active |
| `maxMembers` | Int | Maximum allowed members (default: 100) |
| `createdBy` | User | User who created the room |
| `createdAt/updatedAt` | LocalDateTime | Timestamps |

**ChatRoomType Enum:**
- `DIRECT` - 1:1 private chat
- `GROUP` - Group chat (private)
- `CHANNEL` - Public channel

**JPA Concepts:**
| Annotation | Description |
|------------|-------------|
| `@Table(indexes = [...])` | Creates database indexes for faster queries. Indexes on `created_by`, `type`, `is_active` improve lookup performance |
| `@ManyToOne(fetch = FetchType.LAZY)` | Many chat rooms can belong to one creator. `LAZY` means the User is not loaded until accessed (performance optimization) |
| `@JoinColumn(name = "created_by")` | Specifies the foreign key column name |
| `@Enumerated(EnumType.STRING)` | Stores enum as string in DB (e.g., "GROUP") instead of ordinal number |

---

##### `ChatRoomMember.kt`
Junction table linking users to chat rooms.

| Field | Type | Description |
|-------|------|-------------|
| `id` | Long | Primary key |
| `chatRoom` | ChatRoom | Reference to chat room |
| `user` | User | Reference to user |
| `role` | MemberRole | OWNER / ADMIN / MEMBER |
| `isActive` | Boolean | Whether membership is active |
| `lastReadMessageId` | Long? | Last message ID read by user (for unread count) |
| `joinedAt` | LocalDateTime | When user joined |
| `leftAt` | LocalDateTime? | When user left (null if still member) |

**MemberRole Enum:**
- `OWNER` - Room creator, full permissions
- `ADMIN` - Administrator privileges
- `MEMBER` - Regular member

**JPA Concepts:**
| Annotation | Description |
|------------|-------------|
| `@UniqueConstraint(columnNames = ["chat_room_id", "user_id"])` | Ensures a user can only be a member of a room once (composite unique constraint) |
| Multiple `@Index` | Indexes on user_id, chat_room_id, is_active, role for query optimization |

---

##### `Message.kt`
Represents a chat message.

| Field | Type | Description |
|-------|------|-------------|
| `id` | Long | Primary key |
| `chatRoom` | ChatRoom | Room where message was sent |
| `sender` | User | User who sent the message |
| `type` | MessageType | TEXT / SYSTEM |
| `content` | String? | Message content |
| `isEdited` | Boolean | Whether message was edited |
| `isDeleted` | Boolean | Soft delete flag |
| `sequenceNumber` | Long | Message order within room (important!) |
| `createdAt` | LocalDateTime | When message was sent |
| `editedAt` | LocalDateTime? | When message was edited |

**MessageType Enum:**
- `TEXT` - Regular text message
- `SYSTEM` - System message (e.g., "User joined the room")

**Important Index:**
```kotlin
Index(name = "idx_message_room_sequence", columnList = "chat_room_id,sequence_number")
```
This composite index is crucial for efficiently fetching messages in order within a room.

---

#### DTOs (Data Transfer Objects)

##### `UserDto.kt`
- `UserDto` - User data for API responses (excludes password)
- `CreateUserRequest` - Request body for user registration
- `LoginRequest` - Request body for login

##### `ChatDto.kt`
- `ChatRoomDto` - Chat room data with member count and last message
- `CreateChatRoomRequest` - Request body for creating a room
- `MessageDto` - Message data for API responses
- `SendMessageRequest` - Request body for sending a message
- `MessagePageRequest/Response` - For cursor-based pagination
- `ChatRoomMemberDto` - Member data for a room

**Cursor-Based Pagination:**
```kotlin
data class MessagePageRequest(
    val chatRoomId: Long,
    val cursor: Long? = null,  // Last message ID seen
    val limit: Int = 50,
    val direction: MessageDirection = MessageDirection.BEFORE
)
```
This is more efficient than offset pagination for chat apps because:
1. No page drift when new messages arrive
2. Consistent performance regardless of how many messages exist

##### `WebSocketDto.kt`
WebSocket message types using Jackson polymorphic serialization.

```kotlin
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = ChatMessage::class, name = "CHAT_MESSAGE"),
    JsonSubTypes.Type(value = ErrorMessage::class, name = "ERROR")
)
sealed class WebSocketMessage
```

**What this does:**
When serializing to JSON, Jackson adds a `"type"` field:
```json
{
  "type": "CHAT_MESSAGE",
  "id": 1,
  "content": "Hello!",
  ...
}
```
When deserializing, Jackson reads the `"type"` field to determine which subclass to create.

---

#### Service Interfaces

##### `ChatService.kt`
Interface defining chat operations:
- `createChatRoom()` - Create a new room
- `getChatRoom()` / `getChatRooms()` - Fetch rooms
- `searchChatRooms()` - Search rooms by name
- `joinChatRoom()` / `leaveChatRoom()` - Membership management
- `getChatRoomMembers()` - List members
- `sendMessage()` - Send a message
- `getMessages()` / `getMessagesByCursor()` - Fetch messages

##### `UserService.kt`
Interface defining user operations:
- `createUser()` - Register new user
- `login()` - Authenticate user
- `getUserById()` - Get user info
- `searchUsers()` - Search users
- `updateLastSeen()` - Update online status

---

### 3. chat-persistence

Data access layer with JPA repositories, Redis configuration, and WebSocket management.

#### Configuration

##### `RedisConfig.kt`
Configures Redis connections and serialization.

```kotlin
@Bean("distributedObjectMapper")
fun distributedObjectMapper(): ObjectMapper = ObjectMapper().apply {
    registerModule(JavaTimeModule())
    registerModule(KotlinModule.Builder().build())
    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
}
```

**Key Configurations:**
| Bean | Purpose |
|------|---------|
| `distributedObjectMapper` | ObjectMapper configured for Kotlin and Java 8 date/time serialization |
| `redisTemplate` | For Redis operations (get/set/pub/sub). Uses `StringRedisSerializer` for both keys and values |
| `redisMessageListenerContainer` | Container for Redis Pub/Sub message listeners |

**Redis Concepts:**
- **RedisTemplate**: The main abstraction for Redis operations
- **StringRedisSerializer**: Serializes all data as strings (simpler, debuggable)
- **RedisMessageListenerContainer**: Manages subscriptions to Redis channels

---

##### `CacheConfig.kt`
Configures Spring Cache with Redis backend.

```kotlin
@EnableCaching
class CacheConfig {
    @Bean
    fun cacheManager(connectionFactory: RedisConnectionFactory): CacheManager
}
```

**Cache TTLs (Time To Live):**
| Cache Name | TTL | Purpose |
|------------|-----|---------|
| `users` | 60 min | User data changes infrequently |
| `chatRooms` | 15 min | Room data fairly stable |
| `chatRoomMembers` | 10 min | Membership changes moderately |
| `messages` | 5 min | Messages frequently added |

**Spring Caching Concepts:**
| Annotation | Description |
|------------|-------------|
| `@EnableCaching` | Enables Spring's cache abstraction |
| `@Cacheable("users")` | Cache the return value (used on service methods) |
| `@CacheEvict` | Remove entry from cache when data changes |
| `@CachePut` | Update cache without checking for existing value |

---

#### Repositories

##### `UserRepository.kt`
```kotlin
interface UserRepository : JpaRepository<User, Long> {
    fun findByUsername(username: String): User?
    fun existsByUsername(username: String): Boolean

    @Modifying
    @Query("UPDATE User u SET u.lastSeenAt = :lastSeenAt WHERE u.id = :userId")
    fun updateLastSeenAt(userId: Long, lastSeenAt: LocalDateTime)

    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE ...")
    fun searchUsers(query: String, pageable: Pageable): Page<User>
}
```

**Spring Data JPA Concepts:**
| Feature | Description |
|---------|-------------|
| `JpaRepository<User, Long>` | Extends JpaRepository with entity type and ID type. Provides `save()`, `findById()`, `delete()`, etc. |
| `findByUsername` | Spring Data auto-generates query from method name |
| `@Modifying` | Required for UPDATE/DELETE queries to indicate state change |
| `@Query` | Custom JPQL query when method name derivation isn't enough |
| `Page<User>` | Paginated result with total count, page info |

---

##### `ChatRoomRepository.kt`
```kotlin
@Query("""
    SELECT DISTINCT cr FROM ChatRoom cr
    JOIN ChatRoomMember crm ON cr.id = crm.chatRoom.id
    WHERE crm.user.id = :userId AND crm.isActive = true
    ORDER BY cr.updatedAt DESC
""")
fun findUserChatRooms(userId: Long, pageable: Pageable): Page<ChatRoom>
```
Fetches rooms where the user is an active member, ordered by most recently updated.

---

##### `ChatRoomMemberRepository.kt`
```kotlin
@Query("SELECT COUNT(crm) FROM ChatRoomMember crm WHERE ...")
fun countActiveMembersInRoom(chatRoomId: Long): Long

@Modifying
@Query("UPDATE ChatRoomMember crm SET crm.isActive = false, crm.leftAt = CURRENT_TIMESTAMP WHERE ...")
fun leaveChatRoom(chatRoomId: Long, userId: Long)
```
- Uses `CrudRepository` instead of `JpaRepository` (simpler, no pagination methods)
- Custom `@Modifying` query for leaving a room (soft delete pattern)

---

##### `MessageRepository.kt`
```kotlin
// Cursor-based pagination for fetching older messages
@Query("""
    SELECT m FROM Message m
    JOIN FETCH m.sender s
    JOIN FETCH m.chatRoom cr
    WHERE m.chatRoom.id = :chatRoomId
    AND m.isDeleted = false
    AND m.id < :cursor
    ORDER BY m.sequenceNumber DESC
""")
fun findMessagesBefore(chatRoomId: Long, cursor: Long, pageable: Pageable): List<Message>
```

**JPA Concepts:**
| Feature | Description |
|---------|-------------|
| `JOIN FETCH` | Eagerly loads the relationship in the same query (avoids N+1 problem) |
| `nativeQuery = true` | Uses raw SQL instead of JPQL (for LIMIT clause which isn't in JPQL) |

---

#### Redis Services

##### `MessageSequenceService.kt`
Generates unique, ordered sequence numbers for messages.

```kotlin
fun getNextSequence(chatRoomId: String): Long {
    val key = "$prefix:$chatRoomId"
    return redisTemplate.opsForValue().increment(key) ?: 1L
}
```

**Why Redis for sequences?**
- `INCR` command is **atomic** - guaranteed unique even with multiple servers
- Very fast (in-memory)
- Survives server restarts

**Redis Operations:**
| Method | Redis Command | Description |
|--------|---------------|-------------|
| `opsForValue()` | STRING operations | Simple key-value operations |
| `increment(key)` | INCR | Atomically increment by 1 |

---

##### `RedisMessageBroker.kt`
Handles distributed messaging between multiple server instances using Redis Pub/Sub.

**Key Components:**

```kotlin
@Service
class RedisMessageBroker(
    private val redisTemplate: RedisTemplate<String, String>,
    private val messageListenerContainer: RedisMessageListenerContainer,
    private val objectMapper: ObjectMapper
) : MessageListener
```

**How it works:**
1. Each server instance has a unique `serverId`
2. When a user sends a message, it's broadcast to Redis channel `chat.room.{roomId}`
3. All servers subscribed to that channel receive the message
4. Each server delivers the message to its locally connected users

**Key Methods:**

| Method | Purpose |
|--------|---------|
| `subscribeToRoom(roomId)` | Subscribe server to a room's Redis channel |
| `unsubscribeFromRoom(roomId)` | Unsubscribe from a room's channel |
| `broadcastToRoom(roomId, message)` | Publish message to all servers |
| `onMessage(message, pattern)` | Handle incoming messages from Redis |

**Deduplication Logic:**
```kotlin
private val processedMessages = ConcurrentHashMap<String, Long>()

// In onMessage():
if (processedMessages.containsKey(distributedMessage.id)) {
    return  // Already processed, skip
}
processedMessages[distributedMessage.id] = System.currentTimeMillis()
```
Prevents processing the same message twice (can happen with network issues).

**Cleanup Thread:**
```kotlin
@PostConstruct
fun initialize() {
    Thread {
        Thread.sleep(30000)
        cleanUpProcessedMessages()  // Remove entries older than 60 seconds
    }.apply { isDaemon = true; start() }
}
```
A daemon thread periodically cleans up old message IDs to prevent memory growth.

**Redis Pub/Sub Concepts:**
| Concept | Description |
|---------|-------------|
| `ChannelTopic("chat.room.$roomId")` | A named channel for pub/sub |
| `convertAndSend(channel, message)` | PUBLISH command - sends to all subscribers |
| `MessageListener.onMessage()` | Called when a message is received on subscribed channels |

---

##### `WebSocketSessionManager.kt`
Manages WebSocket connections and message delivery.

**Data Structures:**
```kotlin
private val userSession = ConcurrentHashMap<Long, MutableSet<WebSocketSession>>()
```
Maps user ID to their WebSocket sessions (a user can have multiple tabs/devices).

**Key Methods:**

| Method | Purpose |
|--------|---------|
| `addSession(userId, session)` | Track new WebSocket connection |
| `removeSession(userId, session)` | Clean up disconnected session |
| `joinRoom(userId, roomId)` | Subscribe server to room if first user from this server joins |
| `sendMessageToLocalRoom(roomId, message)` | Send message to all locally connected members |

**Server Room Tracking:**
```kotlin
val serverRoomKey = "chat:server:rooms:${serverId}"
redisTemplate.opsForSet().add(serverRoomKey, roomId.toString())
```
Each server tracks which rooms it has active users in using a Redis SET.

**Message Delivery Flow:**
1. User sends message via WebSocket
2. Message saved to DB with sequence number
3. `RedisMessageBroker.broadcastToRoom()` publishes to Redis
4. All servers receive via `onMessage()`
5. Each server calls `WebSocketSessionManager.sendMessageToLocalRoom()`
6. Message sent to all locally connected room members

---

## Architecture Patterns

### Multi-Module Clean Architecture
```
chat-application (depends on all)
       │
       ├── chat-domain (interfaces, DTOs, entities)
       │
       └── chat-persistence (implementations)
              │
              └── depends on chat-domain
```

### Distributed System Pattern
```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  Server A   │     │  Server B   │     │  Server C   │
│  (users     │     │  (users     │     │  (users     │
│   1, 2, 3)  │     │   4, 5)     │     │   6, 7, 8)  │
└──────┬──────┘     └──────┬──────┘     └──────┬──────┘
       │                   │                   │
       └───────────────────┴───────────────────┘
                           │
                    ┌──────┴──────┐
                    │    Redis    │
                    │   Pub/Sub   │
                    └─────────────┘
```

When User 1 sends a message:
1. Server A receives it via WebSocket
2. Server A broadcasts to Redis `chat.room.X`
3. Servers B and C receive and deliver to their local users

---

## Key Business Logic

### 1. Message Ordering
Messages use `sequenceNumber` generated by Redis `INCR` to guarantee order even when messages arrive out of order.

### 2. Soft Delete Pattern
`isDeleted` and `isActive` flags allow "deleting" without losing data:
- Messages: `isDeleted = true` (still in DB, not shown to users)
- Members: `isActive = false, leftAt = timestamp`

### 3. Cursor-Based Pagination
Instead of `OFFSET` which has performance issues:
```kotlin
WHERE m.id < :cursor ORDER BY m.id DESC LIMIT 50
```
Always O(1) performance regardless of total message count.

### 4. Deduplication
`RedisMessageBroker` tracks processed message IDs to prevent duplicate delivery in distributed scenarios.

---

## Dependencies Summary

| Dependency | Purpose |
|------------|---------|
| `spring-boot-starter-data-jpa` | JPA/Hibernate ORM |
| `spring-boot-starter-data-redis` | Redis client |
| `spring-boot-starter-websocket` | WebSocket support |
| `spring-boot-starter-cache` | Cache abstraction |
| `spring-boot-starter-validation` | Bean Validation (JSR-380) |
| `jackson-module-kotlin` | JSON serialization for Kotlin |
| `jackson-datatype-jsr310` | Java 8 date/time support |
| `postgresql` | Production database |
| `h2database` | Development/test database |
