package com.dentalclinic.repository;

import com.dentalclinic.model.notification.Notification;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUser_IdOrderByCreatedAtDesc(Long userId);

    long countByUser_IdAndIsReadFalse(Long userId);

    @Modifying
    @Transactional
    @Query("""
        update Notification n
        set n.isRead = true
        where n.id = :notificationId
          and n.user.id = :userId
    """)
    int markAsRead(Long notificationId, Long userId);
}
