package com.dentalclinic.repository;

import com.dentalclinic.model.notification.Notification;
import com.dentalclinic.model.notification.NotificationReferenceType;
import com.dentalclinic.model.notification.NotificationType;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUser_IdOrderByCreatedAtDesc(Long userId);

    Page<Notification> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Notification> findByUser_IdAndIsReadFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<Notification> findTop5ByUser_IdOrderByCreatedAtDesc(Long userId);

    long countByUser_IdAndIsReadFalse(Long userId);

    boolean existsByUser_IdAndTypeAndReferenceTypeAndReferenceId(
            Long userId,
            NotificationType type,
            NotificationReferenceType referenceType,
            Long referenceId
    );

    @Modifying
    @Transactional
    @Query("""
        update Notification n
        set n.isRead = true,
            n.readAt = CURRENT_TIMESTAMP
        where n.id = :notificationId
          and n.user.id = :userId
    """)
    int markAsRead(@Param("notificationId") Long notificationId, @Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query("""
        update Notification n
        set n.isRead = true,
            n.readAt = CURRENT_TIMESTAMP
        where n.user.id = :userId
          and n.isRead = false
    """)
    int markAllAsRead(@Param("userId") Long userId);
}
