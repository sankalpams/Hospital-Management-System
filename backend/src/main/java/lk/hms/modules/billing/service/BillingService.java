package lk.hms.modules.billing.service;

import lk.hms.common.api.PageResponse;
import lk.hms.common.api.Paging;
import lk.hms.common.audit.AuditService;
import lk.hms.common.exception.BusinessException;
import lk.hms.common.security.CurrentUser;
import lk.hms.common.sequence.SequenceService;
import lk.hms.common.settings.SettingsService;
import lk.hms.common.time.HospitalTime;
import lk.hms.modules.billing.dto.BillingDtos.*;
import lk.hms.modules.billing.entity.ChargeItem;
import lk.hms.modules.billing.entity.Invoice;
import lk.hms.modules.billing.entity.InvoiceItem;
import lk.hms.modules.billing.entity.Payment;
import lk.hms.modules.billing.entity.ServiceItem;
import lk.hms.modules.billing.repository.ChargeItemRepository;
import lk.hms.modules.billing.repository.InvoiceRepository;
import lk.hms.modules.billing.repository.PaymentRepository;
import lk.hms.modules.billing.repository.ServiceItemRepository;
import lk.hms.modules.patients.entity.Patient;
import lk.hms.modules.patients.repository.PatientRepository;
import lk.hms.modules.users.entity.User;
import lk.hms.modules.users.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Charges, invoices and payments (docs/01 §2I). Source events raise charges exactly once;
 * invoices snapshot them; payments never exceed the balance and are serialised per invoice by a
 * row lock. Refunds are negative payments against the original. Void only when nothing is paid,
 * which releases the charges for re-billing.
 */
@Service
public class BillingService {

    private final ChargeItemRepository charges;
    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final ServiceItemRepository services;
    private final PatientRepository patients;
    private final UserRepository users;
    private final SequenceService sequences;
    private final SettingsService settings;
    private final AuditService audit;
    private final HospitalTime time;

    public BillingService(ChargeItemRepository charges, InvoiceRepository invoices, PaymentRepository payments,
                          ServiceItemRepository services, PatientRepository patients, UserRepository users,
                          SequenceService sequences, SettingsService settings, AuditService audit, HospitalTime time) {
        this.charges = charges;
        this.invoices = invoices;
        this.payments = payments;
        this.services = services;
        this.patients = patients;
        this.users = users;
        this.sequences = sequences;
        this.settings = settings;
        this.audit = audit;
        this.time = time;
    }

    // ================= charges =================

    /** Called by clinical modules inside their own transaction. Idempotent per source event (returns null if already charged). */
    @Transactional(propagation = Propagation.MANDATORY)
    public ChargeItem raiseCharge(Long patientId, String category, String description, String sourceType, Long sourceId,
                            Long departmentId, BigDecimal quantity, BigDecimal unitPrice) {
        if (charges.existsBySourceTypeAndSourceId(sourceType, sourceId)) {
            return null;
        }
        ChargeItem c = new ChargeItem();
        c.setPatientId(patientId);
        c.setCategory(category);
        c.setDescription(description.length() > 255 ? description.substring(0, 255) : description);
        c.setSourceType(sourceType);
        c.setSourceId(sourceId);
        c.setDepartmentId(departmentId);
        c.setQuantity(quantity);
        c.setUnitPrice(InvoiceMath.money(unitPrice));
        c.setAmount(InvoiceMath.lineTotal(quantity, unitPrice));
        c.setServiceDate(time.now());
        c.setCreatedBy(CurrentUser.get().map(p -> p.getId()).orElse(null));
        return charges.save(c);
    }

    /** Removes the charge of a cancelled source event; refuses once it is on an invoice (void that first). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelCharge(String sourceType, Long sourceId) {
        charges.findBySourceTypeAndSourceId(sourceType, sourceId).ifPresent(c -> {
            if (c.getInvoiceId() != null) {
                throw BusinessException.conflict("CHARGE_BILLED", "This item is already invoiced. Void the invoice first.");
            }
            charges.delete(c);
        });
    }

    @Transactional
    public ChargeView addServiceCharge(ServiceChargeRequest r) {
        requirePatient(r.patientId());
        ServiceItem s = services.findById(r.serviceId()).filter(ServiceItem::isActive)
                .orElseThrow(() -> BusinessException.badRequest("UNKNOWN_SERVICE", "Choose a service from the catalogue."));
        // Manual services have no natural source row; a sequence gives each one a unique source id.
        long sourceId = sequences.next("SVC-CHARGE");
        ChargeItem c = raiseCharge(r.patientId(), "SERVICE", s.getName(), "SERVICE", sourceId, null, r.quantity(), s.getPrice());
        audit.recordChange("CREATE", "PATIENT", r.patientId(), "Service charge: " + s.getName() + " x " + r.quantity(), null, null);
        return chargeView(c);
    }

    @Transactional(readOnly = true)
    public List<ChargeView> unbilled(Long patientId) {
        requirePatient(patientId);
        return charges.findByPatientIdAndInvoiceIdIsNullOrderByServiceDateAsc(patientId).stream().map(BillingService::chargeView).toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceView> catalogue() {
        return services.findByActiveTrueOrderByCategoryAscNameAsc().stream()
                .map(s -> new ServiceView(s.getId(), s.getCode(), s.getName(), s.getCategory(), s.getPrice())).toList();
    }

    // ================= invoices =================

    @Transactional
    public InvoiceView createInvoice(CreateInvoiceRequest r) {
        requirePatient(r.patientId());
        List<ChargeItem> selected = charges.findAllById(new LinkedHashSet<>(r.chargeIds()));
        if (selected.size() != new HashSet<>(r.chargeIds()).size()
                || selected.stream().anyMatch(c -> !c.getPatientId().equals(r.patientId()))) {
            throw BusinessException.badRequest("INVALID_CHARGES", "Some charges do not exist or belong to another patient.");
        }
        if (selected.stream().anyMatch(c -> c.getInvoiceId() != null)) {
            throw BusinessException.conflict("CHARGE_ALREADY_BILLED", "Some charges are already on another invoice. Reload and try again.");
        }
        BigDecimal discount = r.discountAmount() == null ? BigDecimal.ZERO : r.discountAmount();
        if (discount.signum() > 0 && (r.discountReason() == null || r.discountReason().isBlank())) {
            throw BusinessException.badRequest("DISCOUNT_REASON_REQUIRED", "Give a reason for the discount.");
        }
        InvoiceMath.Totals t;
        try {
            t = InvoiceMath.totals(selected.stream().map(ChargeItem::getAmount).toList(), discount, taxPercent());
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("INVALID_DISCOUNT", e.getMessage());
        }

        Invoice inv = new Invoice();
        inv.setInvoiceNo(sequences.nextFormatted("INV", time.today().getYear(), 6));
        inv.setPatientId(r.patientId());
        inv.setSubtotal(t.subtotal());
        inv.setDiscountAmount(t.discount());
        inv.setDiscountReason(discount.signum() > 0 ? r.discountReason().trim() : null);
        inv.setTaxAmount(t.tax());
        inv.setTotalAmount(t.total());
        inv.setCreatedBy(CurrentUser.require().getId());
        for (ChargeItem c : selected) {
            InvoiceItem line = new InvoiceItem();
            line.setInvoice(inv);
            line.setChargeItemId(c.getId());
            line.setCategory(c.getCategory());
            line.setDescription(c.getDescription());
            line.setQuantity(c.getQuantity());
            line.setUnitPrice(c.getUnitPrice());
            line.setLineTotal(c.getAmount());
            inv.getItems().add(line);
        }
        invoices.saveAndFlush(inv);
        selected.forEach(c -> c.setInvoiceId(inv.getId()));
        audit.recordChange("CREATE", "INVOICE", inv.getId(), "Draft " + inv.getInvoiceNo() + " total " + inv.getTotalAmount(), null, null);
        return view(inv);
    }

    @Transactional
    public InvoiceView issue(Long id) {
        Invoice inv = locked(id);
        if (inv.getStatus() != Invoice.Status.DRAFT) {
            throw BusinessException.conflict("INVALID_STATUS_TRANSITION", "Only a draft invoice can be issued.");
        }
        inv.setStatus(inv.getTotalAmount().signum() == 0 ? Invoice.Status.PAID : Invoice.Status.ISSUED);
        inv.setIssuedAt(time.now());
        inv.setDueDate(time.today().plusDays(30));
        audit.recordChange("STATUS_CHANGE", "INVOICE", id, "Issued " + inv.getInvoiceNo(), null, null);
        return view(inv);
    }

    @Transactional
    public InvoiceView voidInvoice(Long id, String reason) {
        Invoice inv = locked(id);
        if (inv.getStatus() == Invoice.Status.VOID) {
            throw BusinessException.conflict("INVALID_STATUS_TRANSITION", "This invoice is already void.");
        }
        if (inv.getAmountPaid().signum() != 0) {
            throw BusinessException.conflict("INVOICE_HAS_PAYMENTS", "Refund all payments before voiding this invoice.");
        }
        inv.setStatus(Invoice.Status.VOID);
        inv.setVoidReason(reason.trim());
        inv.setVoidedAt(time.now());
        charges.findByInvoiceId(id).forEach(c -> c.setInvoiceId(null));   // charges can be billed again
        audit.recordChange("STATUS_CHANGE", "INVOICE", id, "Voided " + inv.getInvoiceNo() + ": " + reason.trim(), null, null);
        return view(inv);
    }

    // ================= payments =================

    @Transactional
    public InvoiceView pay(Long invoiceId, PaymentRequest r) {
        Invoice inv = locked(invoiceId);
        if (inv.getStatus() != Invoice.Status.ISSUED && inv.getStatus() != Invoice.Status.PARTIALLY_PAID) {
            throw BusinessException.conflict("INVOICE_NOT_PAYABLE", "Only an issued, unpaid invoice can take payments.");
        }
        BigDecimal amount = InvoiceMath.money(r.amount());
        if (amount.compareTo(inv.balance()) > 0) {
            throw BusinessException.badRequest("OVERPAYMENT", "The balance due is LKR " + inv.balance().toPlainString() + ".");
        }
        if (!"CASH".equals(r.method()) && (r.referenceNo() == null || r.referenceNo().isBlank())) {
            throw BusinessException.badRequest("REFERENCE_REQUIRED", "Enter the card authorisation, transfer or cheque reference.");
        }
        Payment p = new Payment();
        p.setReceiptNo(sequences.nextFormatted("RCP", time.today().getYear(), 6));
        p.setInvoiceId(invoiceId);
        p.setAmount(amount);
        p.setMethod(r.method());
        p.setReferenceNo(blank(r.referenceNo()));
        p.setNotes(blank(r.notes()));
        p.setReceivedBy(CurrentUser.require().getId());
        p.setReceivedAt(time.now());
        payments.save(p);
        applyPaid(inv, amount);
        audit.recordChange("PAYMENT", "INVOICE", invoiceId, p.getReceiptNo() + " " + r.method() + " " + amount.toPlainString(), null, null);
        return view(inv);
    }

    @Transactional
    public InvoiceView refund(Long paymentId, RefundRequest r) {
        Payment original = payments.findById(paymentId).orElseThrow(() -> BusinessException.notFound("Payment", paymentId));
        if (original.getRefundOfPaymentId() != null) {
            throw BusinessException.badRequest("NOT_REFUNDABLE", "A refund cannot itself be refunded.");
        }
        Invoice inv = locked(original.getInvoiceId());
        BigDecimal amount = InvoiceMath.money(r.amount());
        BigDecimal refundable = original.getAmount().subtract(payments.refundedAmount(paymentId));
        if (amount.compareTo(refundable) > 0) {
            throw BusinessException.badRequest("REFUND_TOO_LARGE", "At most LKR " + refundable.toPlainString() + " can be refunded on this receipt.");
        }
        Payment refund = new Payment();
        refund.setReceiptNo(sequences.nextFormatted("RCP", time.today().getYear(), 6));
        refund.setInvoiceId(inv.getId());
        refund.setAmount(amount.negate());
        refund.setMethod(original.getMethod());
        refund.setRefundOfPaymentId(paymentId);
        refund.setNotes(r.reason().trim());
        refund.setReceivedBy(CurrentUser.require().getId());
        refund.setReceivedAt(time.now());
        payments.save(refund);
        applyPaid(inv, amount.negate());
        audit.recordChange("REFUND", "INVOICE", inv.getId(), refund.getReceiptNo() + " refund of " + original.getReceiptNo()
                + " " + amount.toPlainString() + ": " + r.reason().trim(), null, null);
        return view(inv);
    }

    // ================= queries =================

    @Transactional(readOnly = true)
    public InvoiceView get(Long id) {
        return view(invoices.findById(id).orElseThrow(() -> BusinessException.notFound("Invoice", id)));
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceView> search(String status, Long patientId, String q, LocalDate from, LocalDate to, int page, int size) {
        Set<Invoice.Status> statuses = status == null || status.isBlank() ? EnumSet.allOf(Invoice.Status.class)
                : Arrays.stream(status.split(",")).map(s -> parse(s.trim())).collect(Collectors.toCollection(() -> EnumSet.noneOf(Invoice.Status.class)));
        String term = q == null || q.isBlank() ? null : q.trim();
        var start = from == null ? java.time.Instant.EPOCH : time.toInstant(time.toUtc(from.atStartOfDay()));
        var end = to == null ? java.time.Instant.parse("9999-12-31T00:00:00Z") : time.toInstant(time.toUtc(to.plusDays(1).atStartOfDay()));
        var result = invoices.search(statuses, patientId, term == null ? null : term.toUpperCase(Locale.ROOT),
                term == null ? null : "%" + term.toLowerCase(Locale.ROOT) + "%", start, end,
                Paging.of(page, size, null, Map.of(), Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.of(result, this::view);
    }

    // ================= helpers =================

    private void applyPaid(Invoice inv, BigDecimal delta) {
        inv.setAmountPaid(inv.getAmountPaid().add(delta));
        int cmp = inv.getAmountPaid().compareTo(inv.getTotalAmount());
        inv.setStatus(cmp >= 0 ? Invoice.Status.PAID : inv.getAmountPaid().signum() > 0 ? Invoice.Status.PARTIALLY_PAID : Invoice.Status.ISSUED);
        invoices.saveAndFlush(inv);
    }

    private BigDecimal taxPercent() {
        try {
            return new BigDecimal(settings.getString("billing.tax_rate_percent", "0"));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private Invoice locked(Long id) {
        return invoices.lockById(id).orElseThrow(() -> BusinessException.notFound("Invoice", id));
    }

    private void requirePatient(Long id) {
        if (!patients.existsById(id)) {
            throw BusinessException.notFound("Patient", id);
        }
    }

    private static Invoice.Status parse(String s) {
        try {
            return Invoice.Status.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("INVALID_FILTER", "Unknown invoice status: " + s);
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static ChargeView chargeView(ChargeItem c) {
        return new ChargeView(c.getId(), c.getCategory(), c.getDescription(), c.getQuantity(), c.getUnitPrice(), c.getAmount(),
                c.getSourceType(), c.getSourceId(), c.getServiceDate(), c.getInvoiceId());
    }

    private InvoiceView view(Invoice inv) {
        Patient p = patients.findById(inv.getPatientId()).orElseThrow();
        List<Payment> pays = payments.findByInvoiceIdOrderByIdAsc(inv.getId());
        Map<Long, String> names = users.findAllById(pays.stream().map(Payment::getReceivedBy).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
        Map<Long, BigDecimal> refunded = pays.stream().filter(x -> x.getRefundOfPaymentId() != null)
                .collect(Collectors.groupingBy(Payment::getRefundOfPaymentId, Collectors.reducing(BigDecimal.ZERO, x -> x.getAmount().negate(), BigDecimal::add)));
        return new InvoiceView(inv.getId(), inv.getInvoiceNo(), p.getId(), p.getMrn(), p.getFullName(), inv.getStatus().name(),
                inv.getSubtotal(), inv.getDiscountAmount(), inv.getDiscountReason(), inv.getTaxAmount(), inv.getTotalAmount(),
                inv.getAmountPaid(), inv.balance(), inv.getIssuedAt(), inv.getVoidReason(), inv.getCreatedAt(),
                inv.getItems().stream().map(i -> new InvoiceLine(i.getId(), i.getCategory(), i.getDescription(), i.getQuantity(),
                        i.getUnitPrice(), i.getLineTotal())).toList(),
                pays.stream().map(x -> new PaymentView(x.getId(), x.getReceiptNo(), x.getAmount(), x.getMethod(), x.getReferenceNo(),
                        x.getNotes(), x.getRefundOfPaymentId(),
                        x.getRefundOfPaymentId() == null ? x.getAmount().subtract(refunded.getOrDefault(x.getId(), BigDecimal.ZERO)) : BigDecimal.ZERO,
                        names.get(x.getReceivedBy()), x.getReceivedAt())).toList());
    }
}
