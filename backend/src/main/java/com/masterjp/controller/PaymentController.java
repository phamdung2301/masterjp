package com.masterjp.controller;

import com.masterjp.dto.PaymentDtos;
import com.masterjp.security.UserPrincipal;
import com.masterjp.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Phase D — F-044: thanh toan SePay.
 * <p>
 * - /invoices (POST/GET/DELETE): can JWT, chi thao tac hoa don cua chinh user.
 * - /sepay/webhook: PUBLIC — verify bang header X-Webhook-Secret (khong JWT),
 *   luon tra 200 (tru 401 sai secret) de SePay khong retry vo han.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** Tao hoa don PENDING + huong dan chuyen khoan. Request: {"planType": "MONTHLY"}. */
    @PostMapping("/invoices")
    public ResponseEntity<PaymentDtos.InvoiceDto> createInvoice(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody PaymentDtos.CreateInvoiceRequest req) {
        String planType = req == null ? null : req.planType();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.createInvoice(principal.getId(), planType));
    }

    /** Liet ke hoa don cua chinh user (?status= tuy chon), moi nhat truoc. */
    @GetMapping("/invoices")
    public List<PaymentDtos.InvoiceDto> myInvoices(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String status) {
        return paymentService.listMyInvoices(principal.getId(), status);
    }

    /** Huy hoa don PENDING cua chinh minh. */
    @DeleteMapping("/invoices/{code}")
    public PaymentDtos.CancelInvoiceResponse cancelInvoice(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String code) {
        return paymentService.cancelInvoice(principal.getId(), code);
    }

    /**
     * Webhook SePay — PUBLIC (permitAll o SecurityConfig), verify secret o service.
     * Body doc dang Map phong thu vi field name SePay thuc te chua duoc xac minh.
     */
    @PostMapping("/sepay/webhook")
    public Object sepayWebhook(
            @RequestHeader(value = "X-Webhook-Secret", required = false) String secret,
            @RequestBody Map<String, Object> payload) {
        return paymentService.handleWebhook(secret, payload);
    }
}
