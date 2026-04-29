package com.gov.serviceplatform.repository;

import com.gov.serviceplatform.entity.KnowledgeBase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {
    
    Page<KnowledgeBase> findByCategory(String category, Pageable pageable);
    
    Page<KnowledgeBase> findByIsActiveTrue(Pageable pageable);
    
    @Query("SELECT k FROM KnowledgeBase k WHERE k.isActive = true AND (k.title LIKE %:keyword% OR k.keywords LIKE %:keyword% OR k.content LIKE %:keyword%)")
    List<KnowledgeBase> searchByKeyword(@Param("keyword") String keyword);
    
    @Query("SELECT k FROM KnowledgeBase k WHERE k.isActive = true AND (k.category = :category OR :category IS NULL) AND (k.subCategory = :subCategory OR :subCategory IS NULL)")
    List<KnowledgeBase> findByCategoryAndSubCategory(@Param("category") String category, @Param("subCategory") String subCategory);
}
