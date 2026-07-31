package com.isha.grocery.web;

import com.isha.grocery.dto.Responses;
import com.isha.grocery.service.CatalogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/items")
    public List<Responses.ItemView> items(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) String category) {
        return catalog.list(q, category);
    }

    @GetMapping("/items/{id}")
    public Responses.ItemView item(@PathVariable Long id) {
        return catalog.get(id);
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return catalog.categories();
    }
}
