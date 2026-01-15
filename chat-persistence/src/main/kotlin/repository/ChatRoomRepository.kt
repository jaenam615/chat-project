package com.chat.persistence.repository

import com.chat.domain.model.ChatRoom
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ChatRoomRepository : JpaRepository<ChatRoom, Long> {
    @Query(
        """
        SELECT DISTINCT cr FROM ChatRoom cr 
        JOIN ChatRoomMember crm ON cr.id = crm.chatRoom.id 
        WHERE crm.user.id = :userId AND crm.isActive = true AND cr.isActive = true
        ORDER BY cr.updatedAt DESC
    """,
    )
    fun findUserChatRooms(
        userId: Long,
        pageable: Pageable,
    ): Page<ChatRoom>

    fun findByIsActiveTrueOrderByCreatedAtDesc(): List<ChatRoom>

    fun findByNameContainingIgnoreCaseAndIsActiveTrueOrderByCreatedAtDesc(name: String): List<ChatRoom>
}
