package com.aitechmarketplace.backend.order.service;

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
import com.aitechmarketplace.backend.coupon.entity.Coupon;
import com.aitechmarketplace.backend.coupon.service.CouponService;
import com.aitechmarketplace.backend.coupon.entity.DiscountType;
import com.aitechmarketplace.backend.event.OrderEventBridge;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.integration.redis.util.RedisLockRegistry;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import com.aitechmarketplace.backend.order.dto.OrderCreateRequest;
import com.aitechmarketplace.backend.order.dto.OrderPageResponse;
import com.aitechmarketplace.backend.order.dto.OrderResponse;
import com.aitechmarketplace.backend.order.entity.*;
import com.aitechmarketplace.backend.order.repository.OrderItemRepository;
import com.aitechmarketplace.backend.order.repository.OrderRepository;
import com.aitechmarketplace.backend.order.repository.PaymentRepository;
import com.aitechmarketplace.backend.product.entity.Product;
import com.aitechmarketplace.backend.product.repository.ProductRepository;
import com.aitechmarketplace.backend.user.entity.User;
import com.aitechmarketplace.backend.user.repository.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService {

    private static final BigDecimal STANDARD_SHIPPING_FEE = new BigDecimal("30000.00");

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final CouponService couponService;
    private final RedisLockRegistry checkoutLockRegistry;
    private final ApplicationEventPublisher applicationEventPublisher;

    public OrderServiceImpl(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentRepository paymentRepository,
            UserRepository userRepository,
            AddressRepository addressRepository,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            CouponService couponService,
            @Qualifier("checkoutLockRegistry") RedisLockRegistry checkoutLockRegistry,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.couponService = couponService;
        this.checkoutLockRegistry = checkoutLockRegistry;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    @Transactional
    public OrderResponse createOrder(Long userId, OrderCreateRequest request) {
        Lock lock = checkoutLockRegistry.obtain("user:" + userId);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConflictException("Checkout interrupted", "CHECKOUT_INTERRUPTED");
        }
        if (!acquired) {
            throw new ConflictException("Checkout already in progress", "CHECKOUT_IN_PROGRESS");
        }

        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("User not found", "USER_NOT_FOUND"));

            Address address = addressRepository.findById(request.addressId())
                    .orElseThrow(() -> new NotFoundException("Address not found", "ADDRESS_NOT_FOUND"));

            if (!address.getUser().getId().equals(userId)) {
                throw new ForbiddenException("You do not have permission to use this address", "FORBIDDEN");
            }

            Cart cart = cartRepository.findByUserId(userId)
                    .orElseThrow(() -> new BadRequestException("Cart is empty", "CART_EMPTY"));

            List<CartItem> cartItems = cartItemRepository.findByCartIdOrderByIdAsc(cart.getId());
            if (cartItems.isEmpty()) {
                throw new BadRequestException("Cart is empty", "CART_EMPTY");
            }

            BigDecimal subtotal = BigDecimal.ZERO;
            for (CartItem item : cartItems) {
                Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                        .orElseThrow(() -> new BadRequestException("Product not found", "PRODUCT_NOT_FOUND"));
                if (!product.isActive()) {
                    throw new BadRequestException("Product '" + product.getName() + "' is no longer available", "PRODUCT_NOT_AVAILABLE");
                }
                if (product.getStockQuantity() < item.getQuantity()) {
                    throw new BadRequestException("Insufficient stock for product '" + product.getName() + "'", "OUT_OF_STOCK");
                }
                BigDecimal itemTotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                subtotal = subtotal.add(itemTotal);
            }

            BigDecimal shippingFee = STANDARD_SHIPPING_FEE;
            BigDecimal discountAmount = BigDecimal.ZERO;
            Coupon coupon = null;

            if (request.couponCode() != null && !request.couponCode().isBlank()) {
                coupon = couponService.getValidCouponForOrder(request.couponCode().trim(), userId, subtotal, shippingFee);
                discountAmount = calculateDiscount(coupon, subtotal, shippingFee);
            }

            BigDecimal totalAmount = subtotal.add(shippingFee).subtract(discountAmount);
            if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
                totalAmount = BigDecimal.ZERO;
            }

            String orderNumber = generateOrderNumber();

            Order order = new Order();
            order.setOrderNumber(orderNumber);
            order.setUser(user);
            order.setStatus(OrderStatus.PENDING);
            order.setPaymentMethod(request.paymentMethod());
            order.setPaymentStatus(PaymentStatus.PENDING);
            order.setSubtotal(subtotal);
            order.setShippingFee(shippingFee);
            order.setDiscountAmount(discountAmount);
            order.setTotalAmount(totalAmount);
            order.setShippingRecipientName(address.getRecipientName());
            order.setShippingPhone(address.getPhone());
            order.setShippingAddress(address.getAddressLine());
            order.setShippingWard(address.getWard());
            order.setShippingDistrict(address.getDistrict());
            order.setShippingCity(address.getCity());
            order.setShippingCountry(address.getCountry());
            order.setNotes(request.notes());

            for (CartItem item : cartItems) {
                Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                        .orElseThrow(() -> new BadRequestException("Product not found", "PRODUCT_NOT_FOUND"));
                OrderItem orderItem = new OrderItem(
                        order,
                        product,
                        product.getName(),
                        product.getSku(),
                        item.getQuantity(),
                        product.getPrice(),
                        product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()))
                );
                order.addItem(orderItem);

                product.setStockQuantity(product.getStockQuantity() - item.getQuantity());
                productRepository.save(product);
            }

            Order savedOrder = orderRepository.save(order);

            applicationEventPublisher.publishEvent(new OrderEventBridge.OrderCreatedApplicationEvent(savedOrder));

            Payment payment = new Payment(savedOrder, request.paymentMethod(), totalAmount);
            paymentRepository.save(payment);

            if (coupon != null) {
                couponService.recordCouponUsage(coupon, userId, savedOrder.getId(), discountAmount);
            }

            cartItemRepository.deleteByCartId(cart.getId());

            String paymentUrl = generatePaymentUrl(request.paymentMethod(), orderNumber);

            return OrderResponse.from(savedOrder, paymentUrl);
        } finally {
            lock.unlock();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public OrderPageResponse getUserOrders(Long userId, OrderStatus status, Pageable pageable) {
        if (status != null) {
            return OrderPageResponse.from(orderRepository.findByUserIdAndStatus(userId, status, pageable).map(OrderResponse::from));
        }
        return OrderPageResponse.from(orderRepository.findByUserId(userId, pageable).map(OrderResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId, Long userId, boolean isAdmin) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found", "ORDER_NOT_FOUND"));

        if (!isAdmin && !order.getUser().getId().equals(userId)) {
            throw new NotFoundException("Order not found", "ORDER_NOT_FOUND");
        }

        return OrderResponse.from(order);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long orderId, Long userId, boolean isAdmin) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found", "ORDER_NOT_FOUND"));

        if (!isAdmin && !order.getUser().getId().equals(userId)) {
            throw new NotFoundException("Order not found", "ORDER_NOT_FOUND");
        }

        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BadRequestException("Order cannot be cancelled in status " + order.getStatus(), "ORDER_CANNOT_BE_CANCELLED");
        }

        for (OrderItem item : order.getItems()) {
            if (item.getProduct() != null) {
                Product product = productRepository.findByIdForUpdate(item.getProduct().getId()).orElse(null);
                if (product != null) {
                    product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                    productRepository.save(product);
                }
            }
        }

        couponService.rollbackCouponUsage(order.getId());

        order.setStatus(OrderStatus.CANCELLED);
        Order savedOrder = orderRepository.save(order);

        applicationEventPublisher.publishEvent(new OrderEventBridge.OrderCancelledApplicationEvent(savedOrder, "User requested cancellation"));

        return OrderResponse.from(savedOrder);
    }

    private BigDecimal calculateDiscount(Coupon coupon, BigDecimal subtotal, BigDecimal shippingFee) {
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

    private String generateOrderNumber() {
        String datePrefix = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "ORD-" + datePrefix + "-" + suffix;
    }

    private String generatePaymentUrl(PaymentMethod paymentMethod, String orderNumber) {
        return switch (paymentMethod) {
            case VNPAY -> "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?orderNumber=" + orderNumber;
            case MOMO -> "https://test-payment.momo.vn/v2/gateway/api/create?orderNumber=" + orderNumber;
            default -> null;
        };
    }
}
