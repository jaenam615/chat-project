package com.chat.persistence.service

import com.chat.domain.dto.CreateUserRequest
import com.chat.domain.dto.LoginRequest
import com.chat.domain.dto.UserDto
import com.chat.domain.model.User
import com.chat.domain.service.UserService
import com.chat.persistence.repository.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.LocalDateTime

@Service
@Transactional
class UserServiceImpl(
    private val userRepository: UserRepository,
) : UserService {
    override fun createUser(request: CreateUserRequest): UserDto {
        if (userRepository.existsByUsername(request.username)) {
            throw IllegalArgumentException("Username ${request.username} already exists")
        }

        val user =
            User(
                username = request.username,
                password = hashPassword(request.password),
                displayName = request.displayName,
            )

        val savedUser = userRepository.save(user)
        return userToDto(savedUser)
    }

    override fun login(request: LoginRequest): UserDto {
        val user =
            userRepository.findByUsername(request.username)
                ?: throw IllegalArgumentException("Username ${request.username} does not exist")

        if (user.password != hashPassword(request.password)) {
            throw IllegalArgumentException("Password ${request.username} does not match password")
        }

        return userToDto(user)
    }

    override fun getUserById(userId: Long): UserDto {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { IllegalArgumentException("User with ID $userId does not exist") }
        return userToDto(user)
    }

    override fun searchUsers(
        query: String,
        pageable: Pageable,
    ): Page<UserDto> = userRepository.searchUsers(query, pageable).map { userToDto(it) }

    override fun updateLastSeen(userId: Long): UserDto {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { IllegalArgumentException("User with ID $userId does not exist") }

        val now = LocalDateTime.now()
        userRepository.updateLastSeenAt(userId, now)

        return userToDto(user.copy(lastSeenAt = now))
    }

    private fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())

        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun userToDto(user: User): UserDto =
        UserDto(
            id = user.id,
            username = user.username,
            displayName = user.displayName,
            profileImageUrl = user.profileImageUrl,
            isActive = user.isActive,
            status = user.status,
            lastSeenAt = user.lastSeenAt,
            createdAt = user.createdAt,
        )
}
