package com.isha.grocery.repo;

import com.isha.grocery.domain.Item;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {

    List<Item> findByActiveTrueOrderByCategoryAscNameAsc();

    @Query("""
            select i from Item i
            where i.active = true
              and (:category is null or i.category = :category)
              and (:q is null or lower(i.name) like lower(concat('%', :q, '%')))
            order by i.category asc, i.name asc
            """)
    List<Item> search(@Param("q") String q, @Param("category") String category);

    @Query("select distinct i.category from Item i where i.active = true order by i.category")
    List<String> findCategories();

    /**
     * Week 2 fix: stock is re-read under a row lock at write time, so two
     * concurrent updates cannot both pass the same availability check.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Item i where i.id = :id")
    Optional<Item> findByIdForUpdate(@Param("id") Long id);
}
