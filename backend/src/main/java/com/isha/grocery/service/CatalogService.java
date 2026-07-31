package com.isha.grocery.service;

import com.isha.grocery.domain.Item;
import com.isha.grocery.dto.Responses;
import com.isha.grocery.repo.ItemRepository;
import com.isha.grocery.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Godown inventory listing: item, available quantity and price (Week 1). */
@Service
public class CatalogService {

    private final ItemRepository items;

    public CatalogService(ItemRepository items) {
        this.items = items;
    }

    @Transactional(readOnly = true)
    public List<Responses.ItemView> list(String query, String category) {
        String q = (query == null || query.isBlank()) ? null : query.trim();
        String cat = (category == null || category.isBlank() || "All".equalsIgnoreCase(category))
                ? null : category.trim();
        return items.search(q, cat).stream().map(CatalogService::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<String> categories() {
        return items.findCategories();
    }

    @Transactional(readOnly = true)
    public Responses.ItemView get(Long id) {
        return items.findById(id)
                .map(CatalogService::toView)
                .orElseThrow(() -> ApiException.notFound("That item is no longer in the catalog."));
    }

    static Responses.ItemView toView(Item item) {
        return new Responses.ItemView(
                item.getId(),
                item.getName(),
                item.getCategory(),
                item.getDescription(),
                item.getEmoji(),
                item.getUnit(),
                item.getPrice(),
                item.getAvailableQuantity(),
                item.getAvailableQuantity() > 0);
    }
}
