package com.aitechmarketplace.backend.coupon.service;

import com.aitechmarketplace.backend.address.entity.Address;
import com.aitechmarketplace.backend.address.repository.AddressRepository;
import com.aitechmarketplace.backend.cart.entity.Cart;
import com.aitechmarketplace.backend.cart.entity.CartItem;
import com.aitechmarketplace.backend.cart.repository.CartItemRepository;
import com.aitechmarketplace.backend.cart.repository.CartRepository;
import com.aitechmarketplace.backend.common.exception.BadRequestException;
import com.aitechmarketplace.backend.common.exception.ConflictException;
import com.aitechmarketplace.backend.common.exception.ForbiddenException;
import com.aitechmarketplace.backend.common.exception.NotFoundException;
import com.aitechmarketplace.backend.coupon.dto.*;
import com.aitechmarketplace.backend.coupon.entity.Coupon;
import com.aitechmarketplace.backend.coupon.entity.CouponUsage;
import com.aitechmarketplace.backend.coupon.entity.DiscountType;
import com.aitechmarketplace.backend.coupon.repository.CouponRepository;
import com.aitechmarketplace.backend.coupon.repository.CouponUsageRepository;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class CouponServiceImpl implements CouponService {

    private static final BigDecimal STANDARD_SHIPPING_FEE = new BigDecimal("30000.00");

    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final AddressRepository addressRepository;

    public CouponServiceImpl(
            CouponRepository couponRepository,
            CouponUsageRepository couponUsageRepository,
            UserRepository userRepository,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            AddressRepository addressRepository
    ) {
        this.couponRepository = couponRepository;
        this.couponUsageRepository = couponUsageRepository;
        this.userRepository = userRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.addressRepository = addressRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CouponResponse> getPublicCoupons(Long userIdOrNull, Pageable pageable) {
        Page<Coupon> activePage = couponRepository.findActivePublicCoupons(OffsetDateTime.now(), pageable);
        if (userIdOrNull == null) {
            return activePage.map(CouponResponse::from);
        }

        List<CouponResponse> filtered = activePage.getContent().stream()
                .filter(coupon -> {
                    long userCount = couponUsageRepository.countByCouponIdAndUserIdAndStatus(coupon.getId(), userIdOrNull, "USED");
                    return userCount < coupon.getUsageLimitPerUser();
                })
                .map(CouponResponse::from)
                .toList();

        return new PageImpl<>(filtered, pageable, activePage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public CouponValidateResponse validateCoupon(Long userId, CouponValidateRequest request) {
        Coupon coupon = couponRepository.findByCodeIgnoreCase(request.couponCode())
                .orElseThrow(() -> new NotFoundException("Coupon not found", "COUPON_NOT_FOUND"));

        validateCouponEligibility(coupon, userId);

        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new BadRequestException("Cart is empty", "CART_EMPTY"));

        List<CartItem> items = cartItemRepository.findByCartIdOrderByIdAsc(cart.getId());
        if (items.isEmpty()) {
            throw new BadRequestException("Cart is empty", "CART_EMPTY");
        }

        BigDecimal subtotal = items.stream()
                .map(item -> item.getProduct().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (coupon.getMinOrderValue() != null && subtotal.compareTo(coupon.getMinOrderValue()) < 0) {
            throw new BadRequestException(
                    "Order subtotal (" + subtotal + " VND) does not meet minimum order threshold of " + coupon.getMinOrderValue() + " VND",
                    "COUPON_MIN_ORDER_NOT_MET"
            );
        }

        BigDecimal shippingFee = STANDARD_SHIPPING_FEE;
        boolean shippingDiscountPending = false;

        if (request.addressId() != null) {
            Address address = addressRepository.findById(request.addressId())
                    .orElseThrow(() -> new NotFoundException("Address not found", "ADDRESS_NOT_FOUND"));
            if (!address.getUser().getId().equals(userId)) {
                throw new ForbiddenException("You do not have permission to use this address", "FORBIDDEN");
            }
        } else if (coupon.getDiscountType() == DiscountType.SHIPPING) {
            shippingDiscountPending = true;
        }

        BigDecimal discountAmount = calculateDiscountAmount(coupon, subtotal, shippingFee);
        BigDecimal estimatedTotal = subtotal.add(shippingFee).subtract(discountAmount);
        if (estimatedTotal.compareTo(BigDecimal.ZERO) < 0) {
            estimatedTotal = BigDecimal.ZERO;
        }

        return new CouponValidateResponse(
                coupon.getCode(),
                coupon.getDiscountType(),
                coupon.getDiscountValue(),
                discountAmount,
                subtotal,
                shippingFee,
                estimatedTotal,
                true,
                shippingDiscountPending,
                "Coupon applied successfully"
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Coupon getValidCouponForOrder(String code, Long userId, BigDecimal subtotal, BigDecimal shippingFee) {
        Coupon coupon = couponRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new NotFoundException("Coupon not found", "COUPON_NOT_FOUND"));

        validateCouponEligibility(coupon, userId);

        if (coupon.getMinOrderValue() != null && subtotal.compareTo(coupon.getMinOrderValue()) < 0) {
            throw new BadRequestException(
                    "Order subtotal does not meet minimum order threshold of " + coupon.getMinOrderValue(),
                    "COUPON_MIN_ORDER_NOT_MET"
            );
        }

        return coupon;
    }

    @Override
    @Transactional
    public void recordCouponUsage(Coupon coupon, Long userId, Long orderId, BigDecimal discountAmount) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found", "USER_NOT_FOUND"));

        CouponUsage usage = new CouponUsage();
        usage.setCoupon(coupon);
        usage.setUser(user);
        usage.setOrderId(orderId);
        usage.setDiscountAmount(discountAmount);
        usage.setStatus("USED");
        usage.setUsedAt(OffsetDateTime.now());

        couponUsageRepository.save(usage);

        coupon.setUsedCount(coupon.getUsedCount() + 1);
        couponRepository.save(coupon);
    }

    @Override
    @Transactional
    public void rollbackCouponUsage(Long orderId) {
        couponUsageRepository.findByOrderId(orderId).ifPresent(usage -> {
            if ("USED".equalsIgnoreCase(usage.getStatus())) {
                usage.setStatus("RELEASED");
                usage.setReleasedAt(OffsetDateTime.now());
                couponUsageRepository.save(usage);

                Coupon coupon = usage.getCoupon();
                if (coupon.getUsedCount() > 0) {
                    coupon.setUsedCount(coupon.getUsedCount() - 1);
                    couponRepository.save(coupon);
                }
            }
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CouponResponse> getAdminCoupons(String status, String search, Pageable pageable) {
        org.springframework.data.jpa.domain.Specification<Coupon> spec = (root, query, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();

            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.trim().toUpperCase()));
            }

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), pattern),
                        cb.like(cb.lower(root.get("name")), pattern)
                ));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        return couponRepository.findAll(spec, pageable).map(CouponResponse::from);
    }

    @Override
    @Transactional
    public CouponResponse createCoupon(CouponCreateRequest request) {
        if (couponRepository.existsByCodeIgnoreCase(request.code())) {
            throw new ConflictException("Coupon code already exists", "COUPON_ALREADY_EXISTS");
        }

        if (request.discountType() == DiscountType.PERCENTAGE && request.maxDiscountAmount() == null) {
            throw new BadRequestException("Max discount amount is required for percentage discount", "INVALID_REQUEST_PAYLOAD");
        }

        if (request.startDate().isAfter(request.endDate())) {
            throw new BadRequestException("Start date must be before end date", "INVALID_REQUEST_PAYLOAD");
        }

        Coupon coupon = new Coupon();
        coupon.setCode(request.code());
        coupon.setName(request.name());
        coupon.setDescription(request.description());
        coupon.setDiscountType(request.discountType());
        coupon.setDiscountValue(request.discountValue());
        coupon.setMaxDiscountAmount(request.maxDiscountAmount());
        coupon.setMinOrderValue(request.minOrderValue() != null ? request.minOrderValue() : BigDecimal.ZERO);
        coupon.setUsageLimit(request.usageLimit());
        coupon.setUsedCount(0);
        coupon.setUsageLimitPerUser(request.usageLimitPerUser() != null ? request.usageLimitPerUser() : 1);
        coupon.setPublic(request.isPublic() != null ? request.isPublic() : true);
        coupon.setStatus("ACTIVE");
        coupon.setStartDate(request.startDate());
        coupon.setEndDate(request.endDate());

        Coupon saved = couponRepository.save(coupon);
        return CouponResponse.from(saved);
    }

    @Override
    @Transactional
    public CouponResponse updateCoupon(Long id, CouponUpdateRequest request) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Coupon not found", "COUPON_NOT_FOUND"));

        if (request.name() != null) {
            coupon.setName(request.name());
        }
        if (request.description() != null) {
            coupon.setDescription(request.description());
        }
        if (request.usageLimit() != null) {
            coupon.setUsageLimit(request.usageLimit());
        }
        if (request.endDate() != null) {
            coupon.setEndDate(request.endDate());
        }

        Coupon saved = couponRepository.save(coupon);
        return CouponResponse.from(saved);
    }

    @Override
    @Transactional
    public CouponResponse updateCouponStatus(Long id, String status) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Coupon not found", "COUPON_NOT_FOUND"));

        if (!"ACTIVE".equalsIgnoreCase(status) && !"INACTIVE".equalsIgnoreCase(status)) {
            throw new BadRequestException("Status must be ACTIVE or INACTIVE", "INVALID_REQUEST_PAYLOAD");
        }

        coupon.setStatus(status.toUpperCase());
        Coupon saved = couponRepository.save(coupon);
        return CouponResponse.from(saved);
    }

    private void validateCouponEligibility(Coupon coupon, Long userId) {
        if (!"ACTIVE".equalsIgnoreCase(coupon.getStatus())) {
            throw new BadRequestException("Coupon is not active", "COUPON_INACTIVE");
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (now.isBefore(coupon.getStartDate()) || now.isAfter(coupon.getEndDate())) {
            throw new BadRequestException("Coupon is expired or not yet active", "COUPON_EXPIRED");
        }

        if (coupon.getUsedCount() >= coupon.getUsageLimit()) {
            throw new BadRequestException("Coupon usage limit has been reached", "COUPON_USAGE_LIMIT_EXCEEDED");
        }

        if (userId != null) {
            long userUsage = couponUsageRepository.countByCouponIdAndUserIdAndStatus(coupon.getId(), userId, "USED");
            if (userUsage >= coupon.getUsageLimitPerUser()) {
                throw new BadRequestException("You have exceeded the usage limit for this coupon", "COUPON_USER_LIMIT_EXCEEDED");
            }
        }
    }

    private BigDecimal calculateDiscountAmount(Coupon coupon, BigDecimal subtotal, BigDecimal shippingFee) {
        BigDecimal discount = BigDecimal.ZERO;
        switch (coupon.getDiscountType()) {
            case PERCENTAGE -> {
                discount = subtotal.multiply(coupon.getDiscountValue()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                if (coupon.getMaxDiscountAmount() != null && discount.compareTo(coupon.getMaxDiscountAmount()) > 0) {
                    discount = coupon.getMaxDiscountAmount();
                }
            }
            case FIXED_AMOUNT -> {
                discount = coupon.getDiscountValue();
                if (discount.compareTo(subtotal) > 0) {
                    discount = subtotal;
                }
            }
            case SHIPPING -> {
                discount = coupon.getDiscountValue().min(shippingFee);
                if (coupon.getMaxDiscountAmount() != null && discount.compareTo(coupon.getMaxDiscountAmount()) > 0) {
                    discount = coupon.getMaxDiscountAmount();
                }
            }
        }
        return discount;
    }
}
