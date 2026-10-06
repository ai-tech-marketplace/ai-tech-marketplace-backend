package com.aitechmarketplace.backend.product.specification;

import com.aitechmarketplace.backend.product.entity.Product;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

public final class ProductSpecification {

private ProductSpecification() {
}

public static Specification<Product> isActive() {
    return (root, query, criteriaBuilder) ->
        criteriaBuilder.isTrue(root.get("active"));
}

public static Specification<Product> hasKeyword(
    String keyword
) {
    return (root, query, criteriaBuilder) -> {

        if (keyword == null || keyword.isBlank()) {
            return criteriaBuilder.conjunction();
        }

        String pattern = "%" +
            keyword.trim().toLowerCase() +
            "%";

        return criteriaBuilder.like(
            criteriaBuilder.lower(
                root.get("name")
            ),
            pattern
        );
    };
}

public static Specification<Product> hasCategoryId(
    Long categoryId
) {
    return (root, query, criteriaBuilder) -> {

        if (categoryId == null) {
            return criteriaBuilder.conjunction();
        }

        return criteriaBuilder.equal(
            root.join("categories").get("id"),
            categoryId
        );
    };
}

public static Specification<Product> priceGreaterThanOrEqual(
    BigDecimal minPrice
) {
    return (root, query, criteriaBuilder) -> {

        if (minPrice == null) {
            return criteriaBuilder.conjunction();
        }

        return criteriaBuilder.greaterThanOrEqualTo(
            root.get("price"),
            minPrice
        );
    };
}

public static Specification<Product> priceLessThanOrEqual(
    BigDecimal maxPrice
) {
    return (root, query, criteriaBuilder) -> {

        if (maxPrice == null) {
            return criteriaBuilder.conjunction();
        }

        return criteriaBuilder.lessThanOrEqualTo(
            root.get("price"),
            maxPrice
        );
    };
}

}