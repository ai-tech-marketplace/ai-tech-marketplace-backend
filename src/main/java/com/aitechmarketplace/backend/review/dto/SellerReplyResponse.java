package com.aitechmarketplace.backend.review.dto;

import java.time.OffsetDateTime;

public record SellerReplyResponse(
        Long reviewId,
        String sellerReply,
        OffsetDateTime sellerRepliedAt
) {}
