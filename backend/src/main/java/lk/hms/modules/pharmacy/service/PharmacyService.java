package lk.hms.modules.pharmacy.service;

import lk.hms.common.api.PageResponse;
import lk.hms.common.api.Paging;
import lk.hms.common.audit.AuditService;
import lk.hms.common.exception.BusinessException;
import lk.hms.common.settings.SettingsService;
import lk.hms.common.time.HospitalTime;
import lk.hms.modules.pharmacy.dto.PharmacyDtos.*;
import lk.hms.modules.pharmacy.entity.Medicine;
import lk.hms.modules.pharmacy.entity.MedicineBatch;
import lk.hms.modules.pharmacy.entity.MedicineCategory;
import lk.hms.modules.pharmacy.entity.StockTransaction;
import lk.hms.modules.pharmacy.entity.Supplier;
import lk.hms.modules.pharmacy.repository.*;
import lk.hms.modules.users.entity.User;
import lk.hms.modules.users.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Medicine catalogue, suppliers, stock receipts/adjustments/write-offs and stock alerts (docs/01 §2H). */
@Service
public class PharmacyService {

    private static final Map<String, String> SORTS = Map.of("name", "genericName", "code", "code");

    private final MedicineRepository medicines;
    private final MedicineCategoryRepository categories;
    private final SupplierRepository suppliers;
    private final MedicineBatchRepository batches;
    private final StockTransactionRepository transactions;
    private final UserRepository users;
    private final StockLedger ledger;
    private final SettingsService settings;
    private final AuditService audit;
    private final HospitalTime time;

    public PharmacyService(MedicineRepository medicines, MedicineCategoryRepository categories, SupplierRepository suppliers,
                           MedicineBatchRepository batches, StockTransactionRepository transactions, UserRepository users,
                           StockLedger ledger, SettingsService settings, AuditService audit, HospitalTime time) {
        this.medicines = medicines;
        this.categories = categories;
        this.suppliers = suppliers;
        this.batches = batches;
        this.transactions = transactions;
        this.users = users;
        this.ledger = ledger;
        this.settings = settings;
        this.audit = audit;
        this.time = time;
    }

    // ================= catalogue =================

    @Transactional(readOnly = true)
    public PageResponse<MedicineView> search(String q, Long categoryId, boolean lowStockOnly, int page, int size, String sort) {
        String term = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        String like = term == null ? null : "%" + term + "%";
        if (lowStockOnly) {
            List<MedicineView> low = lowStock().stream()
                    .filter(m -> categoryId == null || categoryId.equals(m.categoryId()))
                    .filter(m -> term == null || (m.name() + " " + m.code()).toLowerCase(Locale.ROOT).contains(term))
                    .toList();
            return new PageResponse<>(low, 0, low.size(), low.size(), 1);
        }
        var result = medicines.search(like, categoryId, Paging.of(page, size, sort, SORTS, Sort.by("genericName")));
        return PageResponse.of(result, mapper(result.getContent()));
    }

    @Transactional
    public MedicineView saveMedicine(Long id, MedicineRequest r) {
        if (!categories.existsById(r.categoryId())) {
            throw BusinessException.badRequest("UNKNOWN_CATEGORY", "Choose a valid category.");
        }
        Medicine m;
        if (id == null) {
            String code = r.code().trim().toUpperCase(Locale.ROOT);
            if (medicines.existsByCode(code)) {
                throw BusinessException.conflict("MEDICINE_CODE_TAKEN", "Medicine code " + code + " already exists.");
            }
            m = new Medicine();
            m.setCode(code);
        } else {
            m = medicines.findById(id).orElseThrow(() -> BusinessException.notFound("Medicine", id));
            if (r.version() == null || r.version() != m.getVersion()) {
                throw BusinessException.conflict("CONCURRENT_MODIFICATION", "This medicine was changed by someone else. Reload and try again.");
            }
        }
        m.setGenericName(r.genericName().trim());
        m.setBrandName(blank(r.brandName()));
        m.setCategoryId(r.categoryId());
        m.setDosageForm(r.dosageForm());
        m.setStrength(r.strength().trim());
        m.setUnit(r.unit().trim());
        m.setUnitPrice(r.unitPrice().setScale(2, RoundingMode.HALF_UP));
        m.setReorderLevel(r.reorderLevel());
        m.setPrescriptionRequired(r.prescriptionRequired());
        m.setActive(r.active() == null || r.active());
        medicines.saveAndFlush(m);
        audit.recordChange(id == null ? "CREATE" : "UPDATE", "MEDICINE", m.getId(), m.getDisplayName(), null, null);
        return mapper(List.of(m)).apply(m);
    }

    @Transactional(readOnly = true)
    public List<CategoryView> categories() {
        return categories.findAllByOrderByNameAsc().stream().map(c -> new CategoryView(c.getId(), c.getName(), c.getDescription())).toList();
    }

    @Transactional
    public CategoryView addCategory(CategoryRequest r) {
        if (categories.existsByNameIgnoreCase(r.name().trim())) {
            throw BusinessException.conflict("CATEGORY_EXISTS", "This category already exists.");
        }
        MedicineCategory c = new MedicineCategory();
        c.setName(r.name().trim());
        c.setDescription(blank(r.description()));
        categories.saveAndFlush(c);
        return new CategoryView(c.getId(), c.getName(), c.getDescription());
    }

    // ================= suppliers =================

    @Transactional(readOnly = true)
    public List<SupplierView> suppliers() {
        return suppliers.findAllByOrderByNameAsc().stream().map(PharmacyService::supplierView).toList();
    }

    @Transactional
    public SupplierView saveSupplier(Long id, SupplierRequest r) {
        String name = r.name().trim();
        if (id == null ? suppliers.existsByNameIgnoreCase(name) : suppliers.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw BusinessException.conflict("SUPPLIER_EXISTS", "A supplier with this name already exists.");
        }
        Supplier s = id == null ? new Supplier() : suppliers.findById(id).orElseThrow(() -> BusinessException.notFound("Supplier", id));
        s.setName(name);
        s.setContactPerson(blank(r.contactPerson()));
        s.setPhone(r.phone().trim());
        s.setEmail(blank(r.email()));
        s.setAddress(blank(r.address()));
        s.setRegistrationNo(blank(r.registrationNo()));
        s.setActive(r.active() == null || r.active());
        suppliers.saveAndFlush(s);
        audit.recordChange(id == null ? "CREATE" : "UPDATE", "SUPPLIER", s.getId(), s.getName(), null, null);
        return supplierView(s);
    }

    // ================= stock =================

    @Transactional(readOnly = true)
    public List<BatchView> batches(Long medicineId) {
        if (!medicines.existsById(medicineId)) {
            throw BusinessException.notFound("Medicine", medicineId);
        }
        return batchViews(batches.findByMedicineIdOrderByExpiryDateAsc(medicineId));
    }

    @Transactional
    public BatchView receive(ReceiptRequest r) {
        Medicine m = medicines.findById(r.medicineId()).orElseThrow(() -> BusinessException.notFound("Medicine", r.medicineId()));
        if (r.supplierId() != null && !suppliers.existsById(r.supplierId())) {
            throw BusinessException.badRequest("UNKNOWN_SUPPLIER", "Choose a valid supplier.");
        }
        if (!r.expiryDate().isAfter(time.today())) {
            throw BusinessException.badRequest("EXPIRED_STOCK", "Expired or same-day-expiry stock cannot be received.");
        }
        if (r.manufactureDate() != null && !r.expiryDate().isAfter(r.manufactureDate())) {
            throw BusinessException.badRequest("INVALID_DATES", "Expiry must be after the manufacture date.");
        }
        String batchNo = r.batchNo().trim().toUpperCase(Locale.ROOT);
        if (batches.existsByMedicineIdAndBatchNo(m.getId(), batchNo)) {
            throw BusinessException.conflict("BATCH_EXISTS", "Batch " + batchNo + " is already recorded for " + m.getDisplayName() + ". Adjust it instead.");
        }
        MedicineBatch b = new MedicineBatch();
        b.setMedicineId(m.getId());
        b.setSupplierId(r.supplierId());
        b.setBatchNo(batchNo);
        b.setManufactureDate(r.manufactureDate());
        b.setExpiryDate(r.expiryDate());
        b.setReceivedDate(time.today());
        b.setQuantityReceived(r.quantity());
        b.setQuantityOnHand(0);
        b.setCostPrice(r.costPrice());
        batches.saveAndFlush(b);
        ledger.move(b, "RECEIPT", r.quantity(), "GRN", b.getId(), null);
        audit.recordChange("STOCK", "MEDICINE", m.getId(), "Received " + r.quantity() + " " + m.getUnit() + " batch " + batchNo, null, null);
        return batchViews(List.of(b)).get(0);
    }

    @Transactional
    public BatchView adjust(AdjustmentRequest r) {
        if (r.quantity() == 0) {
            throw BusinessException.badRequest("ZERO_ADJUSTMENT", "Enter a non-zero quantity.");
        }
        MedicineBatch b = batches.lockById(r.batchId()).orElseThrow(() -> BusinessException.notFound("Batch", r.batchId()));
        if (b.getQuantityOnHand() + r.quantity() < 0) {
            throw BusinessException.badRequest("INSUFFICIENT_STOCK", "Only " + b.getQuantityOnHand() + " left in batch " + b.getBatchNo() + ".");
        }
        ledger.move(b, r.quantity() > 0 ? "ADJUSTMENT_IN" : "ADJUSTMENT_OUT", r.quantity(), "ADJUSTMENT", null, r.reason().trim());
        audit.recordChange("STOCK", "MEDICINE", b.getMedicineId(), "Adjusted batch " + b.getBatchNo() + " by " + r.quantity() + ": " + r.reason().trim(), null, null);
        return batchViews(List.of(b)).get(0);
    }

    @Transactional
    public int writeOffExpired() {
        List<MedicineBatch> expired = batches.lockExpiredWithStock(time.today());
        for (MedicineBatch b : expired) {
            ledger.move(b, "EXPIRED_WRITE_OFF", -b.getQuantityOnHand(), "ADJUSTMENT", null, "Expired " + b.getExpiryDate());
        }
        if (!expired.isEmpty()) {
            audit.recordChange("STOCK", "MEDICINE", null, "Wrote off " + expired.size() + " expired batch(es)", null, null);
        }
        return expired.size();
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionView> transactions(Long medicineId, String type, LocalDate from, LocalDate to, int page, int size) {
        var start = from == null ? java.time.Instant.EPOCH : time.toInstant(time.toUtc(from.atStartOfDay()));
        var end = to == null ? java.time.Instant.parse("9999-12-31T00:00:00Z") : time.toInstant(time.toUtc(to.plusDays(1).atStartOfDay()));
        var result = transactions.search(medicineId, blank(type), start, end, Paging.of(page, size, null, Map.of(), Sort.by(Sort.Direction.DESC, "id")));
        List<StockTransaction> rows = result.getContent();
        Map<Long, Medicine> meds = medicines.findAllById(rows.stream().map(StockTransaction::getMedicineId).distinct().toList())
                .stream().collect(Collectors.toMap(Medicine::getId, Function.identity()));
        Map<Long, String> batchNos = batches.findAllById(rows.stream().map(StockTransaction::getBatchId).distinct().toList())
                .stream().collect(Collectors.toMap(MedicineBatch::getId, MedicineBatch::getBatchNo));
        Map<Long, String> names = users.findAllById(rows.stream().map(StockTransaction::getPerformedBy).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getFullName));
        return PageResponse.of(result, t -> new TransactionView(t.getId(), t.getCreatedAt(), t.getMedicineId(),
                meds.get(t.getMedicineId()).getDisplayName(), batchNos.get(t.getBatchId()), t.getType(), t.getQuantity(),
                t.getBalanceAfter(), t.getReferenceType(), t.getReferenceId(), t.getNote(), names.get(t.getPerformedBy())));
    }

    // ================= alerts =================

    @Transactional(readOnly = true)
    public Alerts alerts() {
        int days = expiryWindow();
        LocalDate today = time.today();
        List<MedicineBatch> soon = batches.expiringBy(today.plusDays(days));
        return new Alerts(lowStock(),
                batchViews(soon.stream().filter(b -> b.getExpiryDate().isAfter(today)).toList()),
                batchViews(soon.stream().filter(b -> !b.getExpiryDate().isAfter(today)).toList()),
                days);
    }

    /** Active medicines whose usable (unexpired) stock is at or below the reorder level. */
    @Transactional(readOnly = true)
    public List<MedicineView> lowStock() {
        List<Medicine> active = medicines.findByActiveTrue();
        Function<Medicine, MedicineView> map = mapper(active);
        return active.stream().map(map).filter(MedicineView::lowStock)
                .sorted(Comparator.comparingInt(MedicineView::stockOnHand)).toList();
    }

    public int usableStock(Long medicineId) {
        return stockMap(List.of(medicineId)).getOrDefault(medicineId, 0);
    }

    int expiryWindow() {
        return settings.getInt("pharmacy.expiry_alert_days", 90);
    }

    // ================= mapping =================

    private Map<Long, Integer> stockMap(Collection<Long> ids) {
        Map<Long, Integer> out = new HashMap<>();
        if (!ids.isEmpty()) {
            batches.usableStock(ids, time.today()).forEach(row -> out.put((Long) row[0], ((Number) row[1]).intValue()));
        }
        return out;
    }

    private Function<Medicine, MedicineView> mapper(List<Medicine> list) {
        Map<Long, Integer> stock = stockMap(list.stream().map(Medicine::getId).toList());
        Map<Long, String> cats = categories.findAllById(list.stream().map(Medicine::getCategoryId).distinct().toList())
                .stream().collect(Collectors.toMap(MedicineCategory::getId, MedicineCategory::getName));
        return m -> {
            int onHand = stock.getOrDefault(m.getId(), 0);
            return new MedicineView(m.getId(), m.getCode(), m.getGenericName(), m.getBrandName(), m.getDisplayName(), m.getCategoryId(),
                    cats.get(m.getCategoryId()), m.getDosageForm(), m.getStrength(), m.getUnit(), m.getUnitPrice(), m.getReorderLevel(),
                    m.isPrescriptionRequired(), m.isActive(), onHand, m.isActive() && onHand <= m.getReorderLevel(), m.getVersion());
        };
    }

    private List<BatchView> batchViews(List<MedicineBatch> list) {
        Map<Long, String> sup = suppliers.findAllById(list.stream().map(MedicineBatch::getSupplierId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Supplier::getId, Supplier::getName));
        Map<Long, String> names = medicines.findAllById(list.stream().map(MedicineBatch::getMedicineId).distinct().toList())
                .stream().collect(Collectors.toMap(Medicine::getId, Medicine::getDisplayName));
        LocalDate today = time.today();
        LocalDate soon = today.plusDays(expiryWindow());
        return list.stream().map(b -> new BatchView(b.getId(), b.getMedicineId(), names.get(b.getMedicineId()), b.getBatchNo(), sup.get(b.getSupplierId()),
                b.getManufactureDate(), b.getExpiryDate(), b.getReceivedDate(), b.getQuantityReceived(), b.getQuantityOnHand(),
                b.getCostPrice(), !b.getExpiryDate().isAfter(today) ? "EXPIRED" : !b.getExpiryDate().isAfter(soon) ? "EXPIRING" : "OK")).toList();
    }

    private static SupplierView supplierView(Supplier s) {
        return new SupplierView(s.getId(), s.getName(), s.getContactPerson(), s.getPhone(), s.getEmail(), s.getAddress(),
                s.getRegistrationNo(), s.isActive());
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
