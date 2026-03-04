package com.dentalclinic.repository;

import com.dentalclinic.model.chat.ChatThread;
import com.dentalclinic.model.chat.ChatThreadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatThreadRepository extends JpaRepository<ChatThread, Long> {

    List<ChatThread> findByCustomer_IdOrderByLastMessageAtDesc(Long customerId);

    List<ChatThread> findAllByOrderByLastMessageAtDesc();

    default List<ChatThread> findAllOrderByLastMessageAtDesc() {
        return findAllByOrderByLastMessageAtDesc();
    }

    Optional<ChatThread> findFirstByCustomer_IdAndStatusInOrderByLastMessageAtDesc(Long customerId, Collection<ChatThreadStatus> statuses);

    List<ChatThread> findByStatusInOrderByLastMessageAtDesc(Collection<ChatThreadStatus> statuses);
}
