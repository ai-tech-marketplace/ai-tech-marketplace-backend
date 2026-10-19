package com.aitechmarketplace.backend.review.dto;

public record HelpfulVoteResponse(
        Long reviewId,
        Integer helpfulCount,
        Boolean voted
) {}
