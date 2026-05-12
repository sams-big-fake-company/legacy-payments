package com.bigfake.payments.controller;

import com.bigfake.payments.model.dto.RefundRequest;
import com.bigfake.payments.model.entity.Refund;
import com.bigfake.payments.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * REST controller for refund operations.
 */
@RestController
@RequestMapping("/api/v1/refunds")
@Tag(name = "Refunds", description = "Refund processing endpoints")
public class RefundController {

    @Autowired
    private RefundService refundService;

    @PostMapping
    @Operation(summary = "Process a refund")
    public ResponseEntity<Refund> processRefund(@Valid @RequestBody RefundRequest request) {
        Refund refund = refundService.processRefund(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(refund);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get refund by ID")
    public ResponseEntity<Refund> getRefund(@PathVariable Long id) {
        Refund refund = refundService.getRefundById(id);
        return ResponseEntity.ok(refund);
    }

    @GetMapping("/payment/{paymentId}")
    @Operation(summary = "Get all refunds for a payment")
    public ResponseEntity<List<Refund>> getRefundsForPayment(@PathVariable Long paymentId) {
        List<Refund> refunds = refundService.getRefundsForPayment(paymentId);
        return ResponseEntity.ok(refunds);
    }
}
