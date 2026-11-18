package lk.hms.modules.billing.controller;

import jakarta.validation.Valid;
import lk.hms.common.api.PageResponse;
import lk.hms.modules.billing.dto.BillingDtos.*;
import lk.hms.modules.billing.service.BillingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class BillingController {

    private final BillingService service;

    public BillingController(BillingService service) {
        this.service = service;
    }

    @GetMapping("/patients/{patientId}/unbilled-charges")
    @PreAuthorize("hasAuthority('billing:read')")
    public List<ChargeView> unbilled(@PathVariable Long patientId) {
        return service.unbilled(patientId);
    }

    @GetMapping("/services")
    @PreAuthorize("hasAnyAuthority('billing:read','charge:create')")
    public List<ServiceView> services() {
        return service.catalogue();
    }

    @PostMapping("/charges")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('charge:create')")
    public ChargeView charge(@Valid @RequestBody ServiceChargeRequest body) {
        return service.addServiceCharge(body);
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasAuthority('billing:read')")
    public PageResponse<InvoiceView> invoices(@RequestParam(required = false) String status,
                                              @RequestParam(required = false) Long patientId,
                                              @RequestParam(required = false) String q,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return service.search(status, patientId, q, from, to, page, size);
    }

    @GetMapping("/invoices/{id}")
    @PreAuthorize("hasAuthority('billing:read')")
    public InvoiceView invoice(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('invoice:create')")
    public InvoiceView create(@Valid @RequestBody CreateInvoiceRequest body) {
        return service.createInvoice(body);
    }

    @PostMapping("/invoices/{id}/issue")
    @PreAuthorize("hasAuthority('invoice:create')")
    public InvoiceView issue(@PathVariable Long id) {
        return service.issue(id);
    }

    @PostMapping("/invoices/{id}/void")
    @PreAuthorize("hasAuthority('invoice:void')")
    public InvoiceView voidInvoice(@PathVariable Long id, @Valid @RequestBody ReasonRequest body) {
        return service.voidInvoice(id, body.reason());
    }

    @PostMapping("/invoices/{id}/payments")
    @PreAuthorize("hasAuthority('payment:record')")
    public InvoiceView pay(@PathVariable Long id, @Valid @RequestBody PaymentRequest body) {
        return service.pay(id, body);
    }

    @PostMapping("/payments/{id}/refund")
    @PreAuthorize("hasAuthority('payment:refund')")
    public InvoiceView refund(@PathVariable Long id, @Valid @RequestBody RefundRequest body) {
        return service.refund(id, body);
    }
}
