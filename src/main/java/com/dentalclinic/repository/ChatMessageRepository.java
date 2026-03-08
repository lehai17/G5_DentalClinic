package com.dentalclinic.repository;

import com.dentalclinic.model.chat.ChatMessage;
import com.dentalclinic.model.chat.ChatThread;
import com.dentalclinic.model.user.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByThread_IdOrderByCreatedAtAsc(Long threadId);

    long countByThread_IdAndIsReadFalse(Long threadId);

    ChatMessage findTopByThreadOrderByCreatedAtDesc(ChatThread thread);

    long countByThread_IdAndIsReadFalseAndSender_RoleIn(Long threadId, List<Role> roles);

    long countByThread_IdAndIsReadFalseAndSender_Role(Long threadId, Role role);

    @Modifying
    @Query("""
        update ChatMessage m
        set m.isRead = true
        where m.thread.id = :threadId
          and m.sender.id <> :viewerId
          and m.isRead = false
    """)
    int markIncomingMessagesRead(@Param("threadId") Long threadId, @Param("viewerId") Long viewerId);
}
