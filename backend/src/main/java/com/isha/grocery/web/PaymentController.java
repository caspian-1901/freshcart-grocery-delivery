package com.isha.grocery.web;

import com.isha.grocery.dto.Requests;
import com.isha.grocery.dto.Responses;
import com.isha.grocery.service.MockPaymentGateway;
import com.isha.grocery.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService payments;
    private final MockPaymentGateway gateway;

    public PaymentController(PaymentService payments, MockPaymentGateway gateway) {
        this.payments = payments;
        this.gateway = gateway;
    }

    @PostMapping("/initiate")
    public Responses.PaymentInitiation initiate(@Valid @RequestBody Requests.InitiatePayment request) {
        return payments.initiate(request.orderId(), request.method());
    }

    /** Posted by the gateway once the user completes payment. */
    @PostMapping("/callback")
    public Map<String, String> callback(@Valid @RequestBody Requests.PaymentCallback request) {
        payments.handleCallback(request.gatewayRef(), request.status(), request.signature());
        return Map.of("received", "true");
    }

    /**
     * Fallback status check (Week 3): the payment screen polls this, and it also
     * runs on a schedule, so a missing callback never strands an order.
     */
    @GetMapping("/orders/{orderId}/status")
    public Responses.OrderView status(@PathVariable Long orderId) {
        return payments.checkStatus(orderId);
    }

    // ------------------------------------------------------------------
    // Mock gateway surface — stands in for the hosted checkout page
    // ------------------------------------------------------------------

    /**
     * Simulates the user acting on the gateway's own page.
     *
     * @param outcome SUCCESS, SUCCESS_NO_CALLBACK (to demonstrate the fallback),
     *                or FAILURE
     */
    @PostMapping("/mock-gateway/{gatewayRef}/pay")
    public Map<String, Object> mockPay(@PathVariable String gatewayRef,
                                       @RequestParam(defaultValue = "SUCCESS") String outcome) {
        MockPaymentGateway.Outcome choice;
        try {
            choice = MockPaymentGateway.Outcome.valueOf(outcome.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("BAD_OUTCOME", "Unknown payment outcome: " + outcome);
        }

        boolean deliversCallback = gateway.pay(gatewayRef, choice);
        if (deliversCallback) {
            // The gateway calls us back, exactly as a real one would.
            payments.handleCallback(gatewayRef,
                    choice == MockPaymentGateway.Outcome.FAILURE ? "FAILED" : "SUCCESS",
                    gateway.expectedSignature(gatewayRef));
        }

        return Map.of(
                "gatewayRef", gatewayRef,
                "outcome", choice.name(),
                "callbackDelivered", deliversCallback,
                "note", deliversCallback
                        ? "Callback delivered to the backend."
                        : "Callback withheld — the fallback status check will resolve this order.");
    }
}
