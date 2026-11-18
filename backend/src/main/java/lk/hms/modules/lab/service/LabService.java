package lk.hms.modules.lab.service;

import lk.hms.common.api.PageResponse;
import lk.hms.common.api.Paging;
import lk.hms.common.audit.AuditService;
import lk.hms.common.exception.BusinessException;
import lk.hms.common.security.CurrentUser;
import lk.hms.common.sequence.SequenceService;
import lk.hms.common.time.HospitalTime;
import lk.hms.modules.billing.service.BillingService;
import lk.hms.modules.doctors.entity.Doctor;
import lk.hms.modules.doctors.repository.DoctorRepository;
import lk.hms.modules.emr.entity.Encounter;
import lk.hms.modules.emr.repository.EncounterRepository;
import lk.hms.modules.emr.service.EmrAccess;
import lk.hms.modules.lab.dto.LabDtos.*;
import lk.hms.modules.lab.entity.LabRequest;
import lk.hms.modules.lab.entity.LabRequestItem;
import lk.hms.modules.lab.entity.LabResult;
import lk.hms.modules.lab.entity.LabTest;
import lk.hms.modules.lab.repository.LabRequestItemRepository;
import lk.hms.modules.lab.repository.LabRequestRepository;
import lk.hms.modules.lab.repository.LabResultRepository;
import lk.hms.modules.lab.repository.LabTestRepository;
import lk.hms.modules.notifications.NotificationService;
import lk.hms.modules.patients.entity.Patient;
import lk.hms.modules.patients.repository.PatientRepository;
import lk.hms.modules.users.entity.User;
import lk.hms.modules.users.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Laboratory workflow (docs/01 §2G): order -> sample collected -> processing -> completed
 * (result entered, flagged against the reference range) -> reviewed by a doctor.
 * Each ordered test raises a laboratory charge; cancelling an unbilled test removes it.
 */
@Service
public class LabService {

    private final LabTestRepository tests;
    private final LabRequestRepository requests;
    private final LabRequestItemRepository items;
    private final LabResultRepository results;
    private final EncounterRepository encounters;
    private final DoctorRepository doctors;
    private final PatientRepository patients;
    private final UserRepository users;
    private final EmrAccess emrAccess;
    private final BillingService billing;
    private final NotificationService notifications;
    private final SequenceService sequences;
    private final AuditService audit;
    private final HospitalTime time;

    public LabService(LabTestRepository tests, LabRequestRepository requests, LabRequestItemRepository items,
                      LabResultRepository results, EncounterRepository encounters, DoctorRepository doctors,
                      PatientRepository patients, UserRepository users, EmrAccess emrAccess, BillingService billing,
                      NotificationService notifications, SequenceService sequences, AuditService audit, HospitalTime time) {
        this.tests = tests;
        this.requests = requests;
        this.items = items;
        this.results = results;
        this.encounters = encounters;
        this.doctors = doctors;
        this.patients = patients;
        this.users = users;
        this.emrAccess = emrAccess;
        this.billing = billing;
        this.notifications = notifications;
        this.sequences = sequences;
        this.audit = audit;
        this.time = time;
    }

    // ================= catalogue =================

    @Transactional(readOnly = true)
    public List<LabTestView> catalogue(boolean activeOnly) {
        return tests.findAllByOrderByCategoryAscNameAsc().stream().filter(t -> !activeOnly || t.isActive()).map(LabService::testView).toList();
    }

    @Transactional
    public LabTestView saveTest(Long id, LabTestRequest r) {
        if (r.refRangeLow() != null && r.refRangeHigh() != null && r.refRangeHigh().compareTo(r.refRangeLow()) < 0) {
            throw BusinessException.badRequest("INVALID_RANGE", "The upper reference limit must not be below the lower limit.");
        }
        LabTest t;
        if (id == null) {
            String code = r.code().trim().toUpperCase(Locale.ROOT);
            if (tests.existsByCode(code)) {
                throw BusinessException.conflict("TEST_CODE_TAKEN", "Test code " + code + " already exists.");
            }
            t = new LabTest();
            t.setCode(code);
        } else {
            t = tests.findById(id).orElseThrow(() -> BusinessException.notFound("Lab test", id));
        }
        t.setName(r.name().trim());
        t.setCategory(r.category().trim().toUpperCase(Locale.ROOT));
        t.setSampleType(r.sampleType());
        t.setResultType(r.resultType());
        t.setUnit(blank(r.unit()));
        t.setRefRangeLow(r.refRangeLow());
        t.setRefRangeHigh(r.refRangeHigh());
        t.setRefRangeText(blank(r.refRangeText()));
        t.setTurnaroundHours(r.turnaroundHours());
        t.setPrice(r.price());
        t.setActive(r.active() == null || r.active());
        tests.saveAndFlush(t);
        audit.recordChange(id == null ? "CREATE" : "UPDATE", "LAB_TEST", t.getId(), "Lab test " + t.getCode(), null, null);
        return testView(t);
    }

    // ================= ordering =================

    @Transactional
    public RequestView order(Long encounterId, OrderRequest r) {
        Encounter e = encounters.findById(encounterId).orElseThrow(() -> BusinessException.notFound("Encounter", encounterId));
        Doctor me = emrAccess.callerDoctor().filter(d -> d.getId().equals(e.getDoctorId()))
                .orElseThrow(() -> BusinessException.forbidden("NOT_TREATING_DOCTOR", "Only the treating doctor can order tests for this encounter."));
        if (e.getStatus() != Encounter.Status.OPEN) {
            throw BusinessException.conflict("ENCOUNTER_CLOSED", "This encounter is closed.");
        }
        Set<Long> ids = new LinkedHashSet<>(r.testIds());
        Map<Long, LabTest> found = tests.findAllById(ids).stream().filter(LabTest::isActive)
                .collect(Collectors.toMap(LabTest::getId, Function.identity()));
        if (found.size() != ids.size()) {
            throw BusinessException.badRequest("UNKNOWN_TEST", "Some tests are not available. Reload the test list.");
        }
        LabRequest req = new LabRequest();
        req.setRequestNo(sequences.nextFormatted("LAB", time.today().getYear(), 6));
        req.setPatientId(e.getPatientId());
        req.setEncounterId(e.getId());
        req.setRequestedBy(me.getId());
        req.setDepartmentId(e.getDepartmentId());
        req.setPriority(r.priority());
        req.setClinicalNotes(blank(r.clinicalNotes()));
        req.setRequestedAt(time.now());
        for (Long id : ids) {
            LabRequestItem item = new LabRequestItem();
            item.setRequest(req);
            item.setLabTestId(id);
            item.setPrice(found.get(id).getPrice());
            req.getItems().add(item);
        }
        requests.saveAndFlush(req);
        for (LabRequestItem item : req.getItems()) {
            LabTest t = found.get(item.getLabTestId());
            billing.raiseCharge(req.getPatientId(), "LABORATORY", "Lab: " + t.getName(), "LAB_REQUEST_ITEM", item.getId(),
                    req.getDepartmentId(), BigDecimal.ONE, item.getPrice());
        }
        audit.recordChange("CREATE", "LAB_REQUEST", req.getId(), "Ordered " + req.getRequestNo() + " (" + ids.size() + " tests, " + r.priority() + ")", null, null);
        if (!"ROUTINE".equals(r.priority())) {
            notifications.notifyPermission("lab:sample:collect", "LAB", "WARNING", r.priority() + " lab request " + req.getRequestNo(),
                    ids.size() + " test(s) ordered by " + me.getDisplayName(), "/lab?status=REQUESTED");
        }
        return views(List.of(req)).get(0);
    }

    @Transactional
    public RequestView cancelItem(Long itemId, String reason) {
        LabRequestItem item = item(itemId);
        boolean requester = emrAccess.callerDoctor().map(d -> d.getId().equals(item.getRequest().getRequestedBy())).orElse(false);
        if (!requester) {
            throw BusinessException.forbidden("NOT_REQUESTER", "Only the requesting doctor can cancel this test.");
        }
        move(item, LabRequestItem.Status.CANCELLED);
        item.setCancelReason(reason.trim());
        billing.cancelCharge("LAB_REQUEST_ITEM", item.getId());
        audit.recordChange("STATUS_CHANGE", "LAB_REQUEST", item.getRequest().getId(), "Cancelled test #" + itemId + ": " + reason.trim(), null, null);
        return views(List.of(item.getRequest())).get(0);
    }

    // ================= lab workflow =================

    @Transactional
    public RequestView collectSample(Long itemId) {
        LabRequestItem item = item(itemId);
        move(item, LabRequestItem.Status.SAMPLE_COLLECTED);
        item.setSampleBarcode(sequences.nextFormatted("SMP", time.today().getYear(), 6));
        item.setSampleCollectedAt(time.now());
        item.setSampleCollectedBy(CurrentUser.require().getId());
        audit.recordChange("STATUS_CHANGE", "LAB_REQUEST", item.getRequest().getId(), "Sample " + item.getSampleBarcode() + " collected", null, null);
        return views(List.of(item.getRequest())).get(0);
    }

    @Transactional
    public RequestView startProcessing(Long itemId) {
        LabRequestItem item = item(itemId);
        move(item, LabRequestItem.Status.PROCESSING);
        item.setProcessingStartedAt(time.now());
        return views(List.of(item.getRequest())).get(0);
    }

    @Transactional
    public RequestView enterResult(Long itemId, ResultRequest r) {
        LabRequestItem item = item(itemId);
        LabTest t = tests.findById(item.getLabTestId()).orElseThrow();
        boolean numeric = "NUMERIC".equals(t.getResultType());
        if (numeric && r.numericValue() == null) {
            throw BusinessException.badRequest("VALUE_REQUIRED", "Enter the numeric result.");
        }
        if (!numeric && (r.textValue() == null || r.textValue().isBlank())) {
            throw BusinessException.badRequest("VALUE_REQUIRED", "Enter the result.");
        }
        move(item, LabRequestItem.Status.COMPLETED);
        item.setCompletedAt(time.now());

        LabResult res = new LabResult();
        res.setLabRequestItemId(item.getId());
        res.setNumericValue(numeric ? r.numericValue() : null);
        res.setTextValue(numeric ? null : r.textValue().trim());
        res.setUnit(t.getUnit());
        res.setRefRangeLow(t.getRefRangeLow());
        res.setRefRangeHigh(t.getRefRangeHigh());
        res.setFlag(numeric ? flag(r.numericValue(), t.getRefRangeLow(), t.getRefRangeHigh()) : null);
        res.setComments(blank(r.comments()));
        res.setEnteredBy(CurrentUser.require().getId());
        res.setEnteredAt(time.now());
        results.save(res);

        LabRequest req = item.getRequest();
        audit.recordChange("RESULT", "LAB_REQUEST", req.getId(), t.getCode() + " result entered" + (res.getFlag() == null ? "" : " (" + res.getFlag() + ")"), null, null);
        doctors.findById(req.getRequestedBy()).map(d -> d.getEmployee().getUserId()).filter(Objects::nonNull).ifPresent(uid -> {
            boolean abnormal = "H".equals(res.getFlag()) || "L".equals(res.getFlag());
            Patient p = patients.findById(req.getPatientId()).orElseThrow();
            notifications.notifyUsers(List.of(uid), "LAB", abnormal ? "WARNING" : "INFO",
                    (abnormal ? "Abnormal result: " : "Result ready: ") + t.getName(),
                    p.getFullName() + " (" + p.getMrn() + ")" + (res.getNumericValue() != null ? " · " + res.getNumericValue().stripTrailingZeros().toPlainString() + " " + Objects.toString(t.getUnit(), "") : ""),
                    "/patients/" + p.getId() + "?tab=lab");
        });
        return views(List.of(req)).get(0);
    }

    /** Requesting doctor, or a doctor in the requesting department. */
    @Transactional
    public RequestView review(Long itemId) {
        LabRequestItem item = item(itemId);
        Doctor me = emrAccess.callerDoctor().orElseThrow(() -> BusinessException.forbidden("NOT_A_DOCTOR", "Your account is not linked to a doctor."));
        LabRequest req = item.getRequest();
        boolean allowed = me.getId().equals(req.getRequestedBy()) || me.getEmployee().getDepartment().getId().equals(req.getDepartmentId());
        if (!allowed) {
            throw BusinessException.forbidden("NOT_REVIEWER", "Only the requesting doctor or a colleague in the same department can review this result.");
        }
        move(item, LabRequestItem.Status.REVIEWED);
        item.setReviewedAt(time.now());
        item.setReviewedBy(me.getId());
        audit.recordChange("STATUS_CHANGE", "LAB_REQUEST", req.getId(), "Result #" + itemId + " reviewed", null, null);
        return views(List.of(req)).get(0);
    }

    // ================= queries =================

    @Transactional(readOnly = true)
    public PageResponse<RequestView> search(String status, Long patientId, String priority, String q, LocalDate from, LocalDate to,
                                            int page, int size) {
        Set<LabRequestItem.Status> statuses = status == null || status.isBlank() ? EnumSet.allOf(LabRequestItem.Status.class)
                : Arrays.stream(status.split(",")).map(s -> parseStatus(s.trim()))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(LabRequestItem.Status.class)));
        String term = q == null || q.isBlank() ? null : q.trim();
        var start = from == null ? java.time.Instant.EPOCH : time.toInstant(time.toUtc(from.atStartOfDay()));
        var end = to == null ? java.time.Instant.parse("9999-12-31T00:00:00Z") : time.toInstant(time.toUtc(to.plusDays(1).atStartOfDay()));
        // STAT first, then URGENT, then oldest first: the order a lab works in.
        // shortcut: priority ordering is applied within the page; move it into SQL (CASE ORDER BY) if worklists exceed one page.
        var result = requests.search(statuses, patientId, blank(priority), term == null ? null : term.toUpperCase(Locale.ROOT),
                term == null ? null : "%" + term.toLowerCase(Locale.ROOT) + "%", start, end,
                Paging.of(page, size, null, Map.of(), Sort.by(Sort.Direction.ASC, "requestedAt")));
        List<RequestView> sorted = views(result.getContent()).stream()
                .sorted(Comparator.comparingInt((RequestView v) -> List.of("STAT", "URGENT", "ROUTINE").indexOf(v.priority()))).toList();
        return new PageResponse<>(sorted, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public RequestView get(Long id) {
        return views(List.of(requests.findById(id).orElseThrow(() -> BusinessException.notFound("Lab request", id)))).get(0);
    }

    // ================= helpers =================

    static String flag(BigDecimal value, BigDecimal low, BigDecimal high) {
        if (low != null && value.compareTo(low) < 0) return "L";
        if (high != null && value.compareTo(high) > 0) return "H";
        return low == null && high == null ? null : "N";
    }

    private LabRequestItem item(Long id) {
        return items.findById(id).orElseThrow(() -> BusinessException.notFound("Lab test item", id));
    }

    private static void move(LabRequestItem item, LabRequestItem.Status next) {
        if (!item.getStatus().canMoveTo(next)) {
            throw BusinessException.conflict("INVALID_STATUS_TRANSITION",
                    "A test that is " + item.getStatus().name().toLowerCase(Locale.ROOT).replace('_', ' ') + " cannot move to "
                            + next.name().toLowerCase(Locale.ROOT).replace('_', ' ') + ".");
        }
        item.setStatus(next);
    }

    private static LabRequestItem.Status parseStatus(String s) {
        try {
            return LabRequestItem.Status.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("INVALID_FILTER", "Unknown status: " + s);
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static LabTestView testView(LabTest t) {
        return new LabTestView(t.getId(), t.getCode(), t.getName(), t.getCategory(), t.getSampleType(), t.getResultType(), t.getUnit(),
                t.getRefRangeLow(), t.getRefRangeHigh(), t.getRefRangeText(), t.getTurnaroundHours(), t.getPrice(), t.isActive());
    }

    /** The request's status is its least advanced live item (cancelled items do not hold it back). */
    private static String overall(LabRequest r) {
        return r.getItems().stream().map(LabRequestItem::getStatus).filter(s -> s != LabRequestItem.Status.CANCELLED)
                .min(Comparator.naturalOrder()).map(Enum::name).orElse("CANCELLED");
    }

    private List<RequestView> views(List<LabRequest> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<LabRequestItem> all = list.stream().flatMap(r -> r.getItems().stream()).toList();
        Map<Long, LabTest> testMap = tests.findAllById(all.stream().map(LabRequestItem::getLabTestId).distinct().toList())
                .stream().collect(Collectors.toMap(LabTest::getId, Function.identity()));
        Map<Long, LabResult> resultMap = results.findByLabRequestItemIdIn(all.stream().map(LabRequestItem::getId).toList())
                .stream().collect(Collectors.toMap(LabResult::getLabRequestItemId, Function.identity()));
        Map<Long, String> userNames = users.findAllById(resultMap.values().stream().map(LabResult::getEnteredBy).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getFullName));
        Map<Long, Patient> patientMap = patients.findAllById(list.stream().map(LabRequest::getPatientId).distinct().toList())
                .stream().collect(Collectors.toMap(Patient::getId, Function.identity()));
        Map<Long, String> doctorNames = doctors.findAllById(list.stream().map(LabRequest::getRequestedBy).distinct().toList())
                .stream().collect(Collectors.toMap(Doctor::getId, Doctor::getDisplayName));
        return list.stream().map(r -> {
            Patient p = patientMap.get(r.getPatientId());
            return new RequestView(r.getId(), r.getRequestNo(), p.getId(), p.getMrn(), p.getFullName(), r.getEncounterId(),
                    doctorNames.get(r.getRequestedBy()), r.getPriority(), r.getClinicalNotes(), r.getRequestedAt(), overall(r),
                    r.getItems().stream().map(i -> {
                        LabTest t = testMap.get(i.getLabTestId());
                        LabResult res = resultMap.get(i.getId());
                        return new ItemView(i.getId(), t.getId(), t.getCode(), t.getName(), t.getSampleType(), i.getStatus().name(),
                                i.getPrice(), i.getSampleBarcode(), i.getSampleCollectedAt(), i.getCompletedAt(), i.getReviewedAt(),
                                i.getCancelReason(), r.getRequestedAt().plus(Duration.ofHours(t.getTurnaroundHours())),
                                res == null ? null : new ResultView(res.getNumericValue(), res.getTextValue(), res.getUnit(),
                                        res.getRefRangeLow(), res.getRefRangeHigh(), res.getFlag(), res.getComments(),
                                        userNames.get(res.getEnteredBy()), res.getEnteredAt()));
                    }).toList());
        }).toList();
    }
}
