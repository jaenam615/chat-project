package com.chat.persistence.repository

import com.chat.domain.model.ChatRoomMember
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface ChatRoomMemberRepository : CrudRepository<ChatRoomMember, Long> {
    fun findByChatRoomIdAndIsActiveTrue(chatRoomId: Long): List<ChatRoomMember>

    fun findOneByChatRoomIdAndUserIdAndIsActiveTrue(
        userId: Long,
        chatRoomId: Long,
    ): ChatRoomMember?

    @Query("SELECT COUNT(crm) FROM ChatRoomMember crm WHERE crm.chatRoom.id = :chatRoomId AND crm.isActive = true")
    fun countActiveMembersInRoom(chatRoomId: Long): Long

    @Modifying
    @Query(
        """
        UPDATE ChatRoomMember crm
        SET crm.isActive = false, crm.leftAt = CURRENT_TIMESTAMP 
        WHERE crm.chatRoom.id = :chatRoomId AND crm.user.id = :userId
    """,
    )
    fun leaveChatRoom(
        chatRoomId: Long,
        userId: Long,
    )

    fun existsByChatRoomIdAndUserIdAndIsActiveTrue(
        chatRoomId: Long,
        userId: Long,
    ): Boolean
}
