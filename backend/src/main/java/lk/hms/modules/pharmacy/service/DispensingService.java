package lk.hms.modules.pharmacy.service;

import lk.hms.common.audit.AuditService;
import lk.hms.common.exception.BusinessException;
import lk.hms.common.security.CurrentUser;
import lk.hms.common.sequence.SequenceService;
import lk.hms.common.time.HospitalTime;
import lk.hms.modules.billing.service.BillingService;
import lk.hms.modules.doctors.entity.Doctor;
import lk.hms.modules.doctors.repository.DoctorRepository;
import lk.hms.modules.emr.entity.Prescription;
import lk.hms.modules.emr.entity.PrescriptionItem;
import lk.hms.modules.emr.repository.PrescriptionRepository;
import lk.hms.modules.notifications.NotificationService;
import lk.hms.modules.patients.entity.Patient;
import lk.hms.modules.patients.repository.PatientRepository;
import lk.hms.modules.pharmacy.dto.PharmacyDtos.*;
import lk.hms.modules.pharmacy.entity.Dispensation;
import lk.hms.modules.pharmacy.entity.DispensationItem;
import lk.hms.modules.pharmacy.entity.Medicine;
import lk.hms.modules.pharmacy.entity.MedicineBatch;
import lk.hms.modules.pharmacy.repository.DispensationItemRepository;
import lk.hms.modules.pharmacy.repository.DispensationRepository;
import lk.hms.modules.pharmacy.repository.MedicineBatchRepository;
import lk.hms.modules.pharmacy.repository.MedicineRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dispensing (docs/01 §2H). Batches are allocated first-expiry-first-out under row locks;
 * expired batches are never used; a line can never exceed the remaining prescribed quantity or
 * the usable stock. Each dispensed batch line becomes a ledger movement and a pharmacy charge.
 */
@Service
public class DispensingService {

    private final PrescriptionRepository prescriptions;
    private final MedicineRepository medicines;
    private final MedicineBatchRepository batches;
    private final DispensationRepository dispensations;
    private final DispensationItemRepository dispensationItems;
    private final PatientRepository patients;
    private final DoctorRepository doctors;
    private final PharmacyService pharmacy;
    private final StockLedger ledger;
    private final BillingService billing;
    private final NotificationService notifications;
    private final SequenceService sequences;
    private final AuditService audit;
    private final HospitalTime time;

    public DispensingService(PrescriptionRepository prescriptions, MedicineRepository medicines, MedicineBatchRepository batches,
                             DispensationRepository dispensations, DispensationItemRepository dispensationItems,
                             PatientRepository patients, DoctorRepository doctors, PharmacyService pharmacy, StockLedger ledger,
                             BillingService billing, NotificationService notifications, SequenceService sequences,
                             AuditService audit, HospitalTime time) {
        this.prescriptions = prescriptions;
        this.medicines = medicines;
        this.batches = batches;
        this.dispensations = dispensations;
        this.dispensationItems = dispensationItems;
        this.patients = patients;
        this.doctors = doctors;
        this.pharmacy = pharmacy;
        this.ledger = ledger;
        this.billing = billing;
        this.notifications = notifications;
        this.sequences = sequences;
        this.audit = audit;
        this.time = time;
    }

    /** Pharmacy queue: prescriptions still to dispense, oldest first. */
    @Transactional(readOnly = true)
    public List<QueuePrescription> queue(String status, Long patientId) {
        Set<Prescription.Status> wanted;
        try {
            wanted = status == null || status.isBlank()
                    ? EnumSet.of(Prescription.Status.ACTIVE, Prescription.Status.PARTIALLY_DISPENSED)
                    : EnumSet.of(Prescription.Status.valueOf(status.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("INVALID_FILTER", "Unknown prescription status: " + status);
        }
        // shortcut: the queue shows the oldest 200 open prescriptions; add paging if a pharmacy ever has more waiting.
        List<Prescription> list = prescriptions.queue(wanted, patientId, PageRequest.of(0, 200));
        return views(list);
    }

    @Transactional
    public DispenseResult dispense(Long prescriptionId, DispenseRequest r) {
        Prescription rx = prescriptions.findById(prescriptionId).orElseThrow(() -> BusinessException.notFound("Prescription", prescriptionId));
        if (rx.getStatus() != Prescription.Status.ACTIVE && rx.getStatus() != Prescription.Status.PARTIALLY_DISPENSED) {
            throw BusinessException.conflict("PRESCRIPTION_NOT_DISPENSABLE", "Prescription " + rx.getPrescriptionNo() + " is " + rx.getStatus().name().toLowerCase(Locale.ROOT).replace('_', ' ') + ".");
        }
        Map<Long, PrescriptionItem> rxItems = rx.getItems().stream().collect(Collectors.toMap(PrescriptionItem::getId, Function.identity()));
        Set<Long> seen = new HashSet<>();
        for (DispenseLine line : r.items()) {
            PrescriptionItem item = rxItems.get(line.prescriptionItemId());
            if (item == null) {
                throw BusinessException.badRequest("UNKNOWN_ITEM", "Item #" + line.prescriptionItemId() + " is not on this prescription.");
            }
            if (!seen.add(item.getId())) {
                throw BusinessException.badRequest("DUPLICATE_ITEM", "Each prescription line can appear once.");
            }
            int remaining = item.getQuantityPrescribed() - item.getQuantityDispensed();
            if (line.quantity() > remaining) {
                throw BusinessException.badRequest("EXCEEDS_PRESCRIBED", "Only " + remaining + " remain to be dispensed on this line.");
            }
        }

        Dispensation d = new Dispensation();
        d.setDispensationNo(sequences.nextFormatted("DSP", time.today().getYear(), 6));
        d.setPrescriptionId(rx.getId());
        d.setPatientId(rx.getPatientId());
        d.setDispensedBy(CurrentUser.require().getId());
        d.setDispensedAt(time.now());
        d.setNotes(r.notes() == null || r.notes().isBlank() ? null : r.notes().trim());
        dispensations.saveAndFlush(d);

        List<String> summary = new ArrayList<>();
        Set<Long> touchedMedicines = new HashSet<>();
        for (DispenseLine line : r.items()) {
            PrescriptionItem item = rxItems.get(line.prescriptionItemId());
            Medicine m = medicines.findById(item.getMedicineId()).orElseThrow();
            List<MedicineBatch> fefo = batches.lockDispensable(m.getId(), time.today());
            int available = fefo.stream().mapToInt(MedicineBatch::getQuantityOnHand).sum();
            if (available < line.quantity()) {
                throw BusinessException.conflict("INSUFFICIENT_STOCK", m.getDisplayName() + ": " + available + " " + m.getUnit()
                        + " in usable stock, " + line.quantity() + " requested.");
            }
            int needed = line.quantity();
            for (MedicineBatch b : fefo) {
                if (needed == 0) break;
                int take = Math.min(needed, b.getQuantityOnHand());
                DispensationItem di = new DispensationItem();
                di.setDispensationId(d.getId());
                di.setPrescriptionItemId(item.getId());
                di.setBatchId(b.getId());
                di.setQuantity(take);
                di.setUnitPrice(m.getUnitPrice());
                dispensationItems.saveAndFlush(di);
                ledger.move(b, "DISPENSE", -take, "DISPENSATION", d.getId(), null);
                billing.raiseCharge(rx.getPatientId(), "PHARMACY", m.getDisplayName() + " (batch " + b.getBatchNo() + ")",
                        "DISPENSATION_ITEM", di.getId(), null, BigDecimal.valueOf(take), m.getUnitPrice());
                needed -= take;
            }
            item.setQuantityDispensed(item.getQuantityDispensed() + line.quantity());
            summary.add(m.getDisplayName() + " x " + line.quantity());
            touchedMedicines.add(m.getId());
        }
        boolean complete = rx.getItems().stream().allMatch(i -> i.getQuantityDispensed() >= i.getQuantityPrescribed());
        rx.setStatus(complete ? Prescription.Status.DISPENSED : Prescription.Status.PARTIALLY_DISPENSED);
        prescriptions.saveAndFlush(rx);
        audit.recordChange("DISPENSE", "PRESCRIPTION", rx.getId(), d.getDispensationNo() + ": " + String.join(", ", summary), null, null);
        alertIfLow(touchedMedicines);
        return new DispenseResult(d.getDispensationNo(), rx.getStatus().name(), summary);
    }

    /** Notify pharmacists when a dispense leaves a medicine at or below its reorder level. */
    private void alertIfLow(Set<Long> medicineIds) {
        for (Long id : medicineIds) {
            Medicine m = medicines.findById(id).orElseThrow();
            int left = pharmacy.usableStock(id);
            if (left <= m.getReorderLevel()) {
                notifications.notifyPermission("stock:manage", "PHARMACY", left == 0 ? "CRITICAL" : "WARNING",
                        (left == 0 ? "Out of stock: " : "Low stock: ") + m.getDisplayName(),
                        left + " " + m.getUnit() + " left (reorder level " + m.getReorderLevel() + ")", "/pharmacy?tab=stock&lowStock=true");
            }
        }
    }

    private List<QueuePrescription> views(List<Prescription> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Set<Long> medIds = list.stream().flatMap(p -> p.getItems().stream()).map(PrescriptionItem::getMedicineId).collect(Collectors.toSet());
        Map<Long, Medicine> meds = medicines.findAllById(medIds).stream().collect(Collectors.toMap(Medicine::getId, Function.identity()));
        Map<Long, Integer> stock = new HashMap<>();
        batches.usableStock(medIds, time.today()).forEach(row -> stock.put((Long) row[0], ((Number) row[1]).intValue()));
        Map<Long, Patient> pts = patients.findAllById(list.stream().map(Prescription::getPatientId).distinct().toList())
                .stream().collect(Collectors.toMap(Patient::getId, Function.identity()));
        Map<Long, String> docs = doctors.findAllById(list.stream().map(Prescription::getDoctorId).distinct().toList())
                .stream().collect(Collectors.toMap(Doctor::getId, Doctor::getDisplayName));
        return list.stream().map(p -> {
            Patient pt = pts.get(p.getPatientId());
            return new QueuePrescription(p.getId(), p.getPrescriptionNo(), p.getStatus().name(), pt.getId(), pt.getMrn(), pt.getFullName(),
                    docs.get(p.getDoctorId()), p.getNotes(), p.getAllergyOverrideReason(), p.getCreatedAt(),
                    p.getItems().stream().map(i -> {
                        Medicine m = meds.get(i.getMedicineId());
                        return new QueueItem(i.getId(), m.getId(), m.getDisplayName(), i.getDose(), i.getFrequency(), i.getRoute(),
                                i.getDurationDays(), i.getQuantityPrescribed(), i.getQuantityDispensed(),
                                i.getQuantityPrescribed() - i.getQuantityDispensed(), stock.getOrDefault(m.getId(), 0), m.getUnit(), i.getInstructions());
                    }).toList());
        }).toList();
    }
}
