package lk.hms.modules.pharmacy.controller;

import jakarta.validation.Valid;
import lk.hms.common.api.PageResponse;
import lk.hms.modules.pharmacy.dto.PharmacyDtos.*;
import lk.hms.modules.pharmacy.service.DispensingService;
import lk.hms.modules.pharmacy.service.PharmacyService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class PharmacyController {

    private final PharmacyService pharmacy;
    private final DispensingService dispensing;

    public PharmacyController(PharmacyService pharmacy, DispensingService dispensing) {
        this.pharmacy = pharmacy;
        this.dispensing = dispensing;
    }

    @GetMapping("/medicines")
    @PreAuthorize("hasAuthority('medicine:read')")
    public PageResponse<MedicineView> medicines(@RequestParam(required = false) String q,
                                                @RequestParam(required = false) Long categoryId,
                                                @RequestParam(defaultValue = "false") boolean lowStock,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "25") int size,
                                                @RequestParam(required = false) String sort) {
        return pharmacy.search(q, categoryId, lowStock, page, size, sort);
    }

    @PostMapping("/medicines")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('medicine:manage')")
    public MedicineView createMedicine(@Valid @RequestBody MedicineRequest body) {
        return pharmacy.saveMedicine(null, body);
    }

    @PutMapping("/medicines/{id}")
    @PreAuthorize("hasAuthority('medicine:manage')")
    public MedicineView updateMedicine(@PathVariable Long id, @Valid @RequestBody MedicineRequest body) {
        return pharmacy.saveMedicine(id, body);
    }

    @GetMapping("/medicines/{id}/batches")
    @PreAuthorize("hasAuthority('medicine:read')")
    public List<BatchView> batches(@PathVariable Long id) {
        return pharmacy.batches(id);
    }

    @GetMapping("/medicine-categories")
    @PreAuthorize("hasAuthority('medicine:read')")
    public List<CategoryView> categories() {
        return pharmacy.categories();
    }

    @PostMapping("/medicine-categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('medicine:manage')")
    public CategoryView addCategory(@Valid @RequestBody CategoryRequest body) {
        return pharmacy.addCategory(body);
    }

    @GetMapping("/suppliers")
    @PreAuthorize("hasAnyAuthority('supplier:manage','stock:manage')")
    public List<SupplierView> suppliers() {
        return pharmacy.suppliers();
    }

    @PostMapping("/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('supplier:manage')")
    public SupplierView addSupplier(@Valid @RequestBody SupplierRequest body) {
        return pharmacy.saveSupplier(null, body);
    }

    @PutMapping("/suppliers/{id}")
    @PreAuthorize("hasAuthority('supplier:manage')")
    public SupplierView updateSupplier(@PathVariable Long id, @Valid @RequestBody SupplierRequest body) {
        return pharmacy.saveSupplier(id, body);
    }

    @PostMapping("/stock/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('stock:manage')")
    public BatchView receive(@Valid @RequestBody ReceiptRequest body) {
        return pharmacy.receive(body);
    }

    @PostMapping("/stock/adjustments")
    @PreAuthorize("hasAuthority('stock:manage')")
    public BatchView adjust(@Valid @RequestBody AdjustmentRequest body) {
        return pharmacy.adjust(body);
    }

    @PostMapping("/stock/write-off-expired")
    @PreAuthorize("hasAuthority('stock:manage')")
    public Map<String, Integer> writeOff() {
        return Map.of("batches", pharmacy.writeOffExpired());
    }

    @GetMapping("/stock/transactions")
    @PreAuthorize("hasAuthority('medicine:read')")
    public PageResponse<TransactionView> transactions(@RequestParam(required = false) Long medicineId,
                                                      @RequestParam(required = false) String type,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "50") int size) {
        return pharmacy.transactions(medicineId, type, from, to, page, size);
    }

    @GetMapping("/pharmacy/alerts")
    @PreAuthorize("hasAuthority('medicine:read')")
    public Alerts alerts() {
        return pharmacy.alerts();
    }

    @GetMapping("/pharmacy/queue")
    @PreAuthorize("hasAuthority('prescription:read')")
    public List<QueuePrescription> queue(@RequestParam(required = false) String status, @RequestParam(required = false) Long patientId) {
        return dispensing.queue(status, patientId);
    }

    @PostMapping("/prescriptions/{id}/dispense")
    @PreAuthorize("hasAuthority('pharmacy:dispense')")
    public DispenseResult dispense(@PathVariable Long id, @Valid @RequestBody DispenseRequest body) {
        return dispensing.dispense(id, body);
    }
}
