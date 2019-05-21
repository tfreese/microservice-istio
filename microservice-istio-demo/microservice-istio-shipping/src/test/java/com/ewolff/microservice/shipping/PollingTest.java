package com.ewolff.microservice.shipping;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.ewolff.microservice.shipping.poller.ShippingPoller;

import au.com.dius.pact.consumer.Pact;
import au.com.dius.pact.consumer.dsl.DslPart;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit.PactProviderRule;
import au.com.dius.pact.consumer.junit.PactVerification;
import au.com.dius.pact.core.model.RequestResponsePact;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = ShippingTestApp.class, webEnvironment = WebEnvironment.DEFINED_PORT)
@ActiveProfiles("test")
public class PollingTest {

	@Autowired
	private ShipmentRepository shipmentRepository;

	@Autowired
	private ShippingPoller shippingPoller;

	@Rule
	public PactProviderRule mockProvider = new PactProviderRule("OrderProvider", "localhost", 8081, this);

	private DslPart feedBody(Date now) {
		return new PactDslJsonBody().date("updated", "yyyy-MM-dd'T'kk:mm:ss.SSS+0000", now)
									.eachLike("orders")
									.numberType("id", 1)
									.stringType("link", "http://localhost:8081/order/1")
									.date("updated", "yyyy-MM-dd'T'kk:mm:ss.SSS+0000", now)
									.closeArray();
	}

	public DslPart order(Date now) {
		return new PactDslJsonBody().numberType("id", 1)
									.numberType("numberOfLines", 1)
									.stringType("deliveryService", "Hermes")
									.object("customer")
									.numberType("customerId", 1)
									.stringType("name", "Wolff")
									.stringType("firstname", "Eberhard")
									.stringType("email", "eberhard.wolff@posteo.net")
									.closeObject()
									.object("shippingAddress")
									.stringType("street", "Krischerstr. 100")
									.stringType("zip", "40789")
									.stringType("city", "Monheim am Rhein")
									.closeObject()
									.array("orderLine")
									.object()
									.numberType("count", 42)
									.object("item")
									.numberType("itemId", 1)
									.stringType("name", "iPod")
									.closeObject()
									.closeArray();
	}

	@Pact(consumer = "Shipping")
	public RequestResponsePact createFragment(PactDslWithProvider builder) {
		Map<String, String> headers = new HashMap<String, String>();
		headers.put("Content-Type", "application/json");
		Date now = new Date();
		return builder	.uponReceiving("Request for order feed")
						.method("GET")
						.path("/feed")
						.willRespondWith()
						.status(200)
						.headers(headers)
						.body(feedBody(now))
						.uponReceiving("Request for an order")
						.method("GET")
						.path("/order/1")
						.willRespondWith()
						.status(200)
						.headers(headers)
						.body(order(now))
						.toPact();
	}

	@Test
	@PactVerification
	public void orderArePolled() {
		long countBeforePoll = shipmentRepository.count();
		shippingPoller.pollInternal();
		assertThat(shipmentRepository.count(), is(greaterThan(countBeforePoll)));
	}

}
