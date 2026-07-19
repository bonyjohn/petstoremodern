package com.petstore.core.order.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.petstore.core.common.EventPublisher;
import com.petstore.core.order.document.OrderAddress;
import com.petstore.core.order.document.OrderContact;
import com.petstore.core.order.document.OrderCreditCard;
import com.petstore.core.order.document.OrderDocument;
import com.petstore.core.order.document.OrderLine;
import com.petstore.core.order.document.OrderStatus;
import com.petstore.core.order.document.StatusChange;
import com.petstore.core.order.repository.OrderRepository;
import com.petstore.core.order.web.AdminOrderResponse;
import com.petstore.core.order.web.OrderAddressDto;
import com.petstore.core.order.web.OrderContactDto;
import com.petstore.core.order.web.OrderCreditCardDto;
import com.petstore.core.order.web.OrderLineRequest;
import com.petstore.core.order.web.OrderLineResponse;
import com.petstore.core.order.web.OrderResponse;
import com.petstore.core.order.web.PlaceOrderRequest;
import com.petstore.core.order.web.StatusChangeResponse;

/**
 * Places orders (with server-side re-pricing — the client cart's prices are
 * untrusted) and reads them back for their owner. Item/product/customer lookups
 * go straight to the MongoDB collections by name: the collections are the
 * contract between modules, not each other's Java classes, which keeps the
 * order module free of catalog/customer dependencies.
 */
@Service
public class OrderService {

	private static final String DEFAULT_LOCALE = "en_US";

	private final OrderRepository orderRepository;
	private final OrderIdGenerator orderIdGenerator;
	private final EventPublisher eventPublisher;
	private final MongoTemplate mongoTemplate;

	public OrderService(OrderRepository orderRepository, OrderIdGenerator orderIdGenerator,
			EventPublisher eventPublisher, MongoTemplate mongoTemplate) {
		this.orderRepository = orderRepository;
		this.orderIdGenerator = orderIdGenerator;
		this.eventPublisher = eventPublisher;
		this.mongoTemplate = mongoTemplate;
	}

	public OrderResponse place(String userId, PlaceOrderRequest request) {
		String locale = request.locale() == null ? DEFAULT_LOCALE : request.locale();
		Instant now = Instant.now();

		List<OrderLine> lines = new ArrayList<>();
		BigDecimal totalValue = BigDecimal.ZERO;
		int lineNo = 1;
		for (OrderLineRequest lineRequest : request.lines()) {
			OrderLine line = priceLine(lineNo++, lineRequest, locale);
			lines.add(line);
			totalValue = totalValue.add(line.unitPrice().multiply(BigDecimal.valueOf(line.qty())));
		}

		Document customer = mongoTemplate.findById(userId, Document.class, "customers");
		String orderId = orderIdGenerator.nextOrderId();
		OrderDocument order = new OrderDocument(
				orderId, userId, customerEmail(customer), locale, now,
				OrderStatus.PENDING, List.of(new StatusChange(OrderStatus.PENDING, now)), totalValue,
				toContact(request.shipTo()), toContact(request.billTo()),
				toCreditCard(request.creditCard(), customer),
				lines);
		orderRepository.save(order);

		eventPublisher.publish(new OrderPlacedEvent(orderId, locale, totalValue));
		return toResponse(orderRepository.findById(orderId).orElseThrow());
	}

	public List<OrderResponse> listOwn(String userId) {
		return orderRepository.findByUserIdOrderByOrderDateDesc(userId).stream()
				.map(this::toResponse)
				.toList();
	}

	/** All orders for the admin grid, optionally filtered by status, newest first. */
	public List<AdminOrderResponse> listForAdmin(OrderStatus status) {
		List<OrderDocument> orders = status == null
				? orderRepository.findAllByOrderByOrderDateDesc()
				: orderRepository.findByStatusOrderByOrderDateDesc(status);
		return orders.stream()
				.map(order -> new AdminOrderResponse(order.id(), order.userId(), order.orderDate(),
						order.locale(), order.totalValue(), order.status().name()))
				.toList();
	}

	/** The owner sees their order; an admin sees any; everyone else gets empty (404 upstream). */
	public Optional<OrderResponse> getForUser(String orderId, String userId, boolean admin) {
		return orderRepository.findById(orderId)
				.filter(order -> admin || order.userId().equals(userId))
				.map(this::toResponse);
	}

	/**
	 * Prices one line from the {@code items}/{@code products} collections at the
	 * requested locale's own listPrice. An item with no detail block for that
	 * locale is rejected, not en_US-priced.
	 */
	private OrderLine priceLine(int lineNo, OrderLineRequest lineRequest, String locale) {
		Document item = mongoTemplate.findById(lineRequest.itemId(), Document.class, "items");
		if (item == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown item: " + lineRequest.itemId());
		}
		String productId = item.getString("productId");
		Document product = mongoTemplate.findById(productId, Document.class, "products");
		String categoryId = product == null ? null : product.getString("categoryId");

		Document details = item.get("details", Document.class);
		Document localeDetail = details.get(locale, Document.class);
		if (localeDetail == null || localeDetail.get("listPrice") == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"item " + lineRequest.itemId() + " not available in locale " + locale);
		}
		BigDecimal unitPrice = toBigDecimal(localeDetail.get("listPrice"));

		return new OrderLine(lineNo, lineRequest.itemId(), productId, categoryId,
				lineRequest.qty(), unitPrice, 0);
	}

	private String customerEmail(Document customer) {
		if (customer == null) {
			return null;
		}
		Document account = customer.get("account", Document.class);
		Document contactInfo = account == null ? null : account.get("contactInfo", Document.class);
		return contactInfo == null ? null : contactInfo.getString("email");
	}

	/**
	 * The customer API serves masked card numbers, and the checkout form
	 * round-trips them; a masked number here means "the card on file", so the
	 * order snapshot takes the stored number instead of the mask.
	 */
	private OrderCreditCard toCreditCard(OrderCreditCardDto dto, Document customer) {
		String cardNumber = dto.cardNumber();
		if (cardNumber != null && cardNumber.contains("*")) {
			Document account = customer == null ? null : customer.get("account", Document.class);
			Document storedCard = account == null ? null : account.get("creditCard", Document.class);
			cardNumber = storedCard == null ? null : storedCard.getString("cardNumber");
		}
		return new OrderCreditCard(cardNumber, dto.cardType(), dto.expiryDate());
	}

	/** The mapping layer stores BigDecimal as Decimal128 or String depending on configuration. */
	private BigDecimal toBigDecimal(Object value) {
		return switch (value) {
			case Decimal128 decimal -> decimal.bigDecimalValue();
			case String string -> new BigDecimal(string);
			case Number number -> new BigDecimal(number.toString());
			default -> throw new IllegalStateException("Unexpected price type: " + value.getClass());
		};
	}

	private OrderContact toContact(OrderContactDto dto) {
		OrderAddressDto address = dto.address();
		OrderAddress orderAddress = address == null ? null
				: new OrderAddress(address.street(), address.city(), address.state(), address.zipCode(), address.country());
		return new OrderContact(dto.familyName(), dto.givenName(), orderAddress, dto.email(), dto.phone());
	}

	private OrderResponse toResponse(OrderDocument order) {
		List<OrderLineResponse> lines = order.lines().stream()
				.map(line -> new OrderLineResponse(line.lineNo(), line.itemId(), line.productId(),
						line.categoryId(), line.qty(), line.unitPrice(), line.qtyShipped()))
				.toList();
		List<StatusChangeResponse> history = order.statusHistory().stream()
				.map(change -> new StatusChangeResponse(change.status().name(), change.at()))
				.toList();
		return new OrderResponse(order.id(), order.status().name(), order.orderDate(),
				order.locale(), order.totalValue(), lines, history);
	}
}
