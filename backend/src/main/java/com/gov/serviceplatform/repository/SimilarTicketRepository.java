package com.gov.serviceplatform.repository;

import com.gov.serviceplatform.entity.SimilarTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SimilarTicketRepository extends JpaRepository<SimilarTicket, Long> {
    
    List<SimilarTicket> findBySourceTicketIdOrderBySimilarityScoreDesc(Long sourceTicketId);
    
    List<SimilarTicket> findBySimilarTicketId(Long similarTicketId);
    
    @Query("SELECT s FROM SimilarTicket s WHERE s.sourceTicket.id = :ticketId OR s.similarTicket.id = :ticketId ORDER BY s.similarityScore DESC")
    List<SimilarTicket> findAllRelatedTickets(@Param("ticketId") Long ticketId);
    
    @Query("SELECT s FROM SimilarTicket s WHERE s.sourceTicket.id = :sourceId AND s.similarTicket.id = :similarId")
    SimilarTicket findBySourceAndSimilar(@Param("sourceId") Long sourceId, @Param("similarId") Long similarId);
}
