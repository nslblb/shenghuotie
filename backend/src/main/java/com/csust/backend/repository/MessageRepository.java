package com.csust.backend.repository;

import com.csust.backend.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("SELECT m FROM Message m WHERE " +
            "((m.senderId = :userId1 AND m.receiverId = :userId2 AND m.deletedBySender = false) OR " +
            "(m.senderId = :userId2 AND m.receiverId = :userId1 AND m.deletedByReceiver = false)) " +
            "ORDER BY m.createdAt DESC")
    Page<Message> findConversation(@Param("userId1") Long userId1,
                                   @Param("userId2") Long userId2,
                                   Pageable pageable);

    @Modifying
    @Query("UPDATE Message m SET m.isRead = true WHERE m.senderId = :partnerId AND m.receiverId = :userId AND m.isRead = false")
    void markConversationRead(@Param("userId") Long userId, @Param("partnerId") Long partnerId);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.receiverId = :userId AND m.isRead = false AND m.deletedByReceiver = false")
    long countUnreadByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.senderId = :partnerId AND m.receiverId = :userId AND m.isRead = false AND m.deletedByReceiver = false")
    long countUnreadFromPartner(@Param("userId") Long userId, @Param("partnerId") Long partnerId);

    @Query("SELECT m FROM Message m WHERE m.id IN (" +
            "SELECT MAX(m2.id) FROM Message m2 WHERE (m2.senderId = :userId AND m2.deletedBySender = false) OR (m2.receiverId = :userId AND m2.deletedByReceiver = false) " +
            "GROUP BY CASE WHEN m2.senderId = :userId THEN m2.receiverId ELSE m2.senderId END" +
            ") ORDER BY m.createdAt DESC")
    List<Message> findLatestMessagesPerConversation(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE Message m SET m.deletedBySender = true WHERE m.senderId = :userId AND m.receiverId = :partnerId")
    int softDeleteAsSender(@Param("userId") Long userId, @Param("partnerId") Long partnerId);

    @Modifying
    @Query("UPDATE Message m SET m.deletedByReceiver = true WHERE m.receiverId = :userId AND m.senderId = :partnerId")
    int softDeleteAsReceiver(@Param("userId") Long userId, @Param("partnerId") Long partnerId);

    @Modifying
    @Query("DELETE FROM Message m WHERE m.id IN :ids AND (m.senderId = :userId OR m.receiverId = :userId)")
    void deleteByIdsAndUserId(@Param("ids") List<Long> ids, @Param("userId") Long userId);

    @Modifying
    @Query("UPDATE Message m SET m.deletedBySender = true WHERE m.id IN :ids AND m.senderId = :userId")
    int softDeleteByIdsAsSender(@Param("ids") List<Long> ids, @Param("userId") Long userId);

    @Modifying
    @Query("UPDATE Message m SET m.deletedByReceiver = true WHERE m.id IN :ids AND m.receiverId = :userId")
    int softDeleteByIdsAsReceiver(@Param("ids") List<Long> ids, @Param("userId") Long userId);

    @Modifying
    @Query("UPDATE Message m SET m.isRecalled = true WHERE m.id = :id AND m.senderId = :senderId")
    int recallMessage(@Param("id") Long id, @Param("senderId") Long senderId);

    @Query("SELECT m FROM Message m WHERE m.id IN :ids")
    List<Message> findAllByIds(@Param("ids") List<Long> ids);

    @Query("SELECT m FROM Message m WHERE " +
            "((m.senderId = :userId1 AND m.receiverId = :userId2) OR " +
            "(m.senderId = :userId2 AND m.receiverId = :userId1)) " +
            "AND m.deletedBySender = true AND m.deletedByReceiver = true")
    List<Message> findBothDeletedMessages(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    @Modifying
    @Query("DELETE FROM Message m WHERE m.id IN :ids")
    void deleteByIds(@Param("ids") List<Long> ids);
}
