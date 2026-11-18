package lk.hms.modules.analytics;

import lk.hms.common.exception.BusinessException;
import lk.hms.common.security.CurrentUser;
import lk.hms.common.time.HospitalTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;

/**
 * Analytics reports (docs/04 §Analytics). Every figure is a SQL aggregate over the live tables.
 * Each report needs the permission registered with it in the constructor.
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private record Entry(String permission, Function<Filter, Report> query) {
    }

    private final Map<String, Entry> reports;
    private final DashboardService dashboard;
    private final HospitalTime time;

    public AnalyticsController(OperationalReports ops, ClinicalReports clinical, FinanceStaffReports finance,
                               DashboardService dashboard, HospitalTime time) {
        this.dashboard = dashboard;
        this.time = time;
        this.reports = Map.of(
                "patients", new Entry("report:operational", ops::patients),
                "appointments", new Entry("report:operational", ops::appointments),
                "doctors", new Entry("report:operational", ops::doctorWorkload),
                "departments", new Entry("report:operational", ops::departments),
                "inpatient", new Entry("report:operational", clinical::inpatient),
                "laboratory", new Entry("report:operational", clinical::laboratory),
                "pharmacy", new Entry("report:operational", clinical::pharmacy),
                "revenue", new Entry("report:financial", finance::revenue),
                "staff", new Entry("report:staff", finance::staff));
    }

    /** Signed-in users only; the cards depend on the caller's permissions. */
    @GetMapping("/dashboard")
    public Report dashboard() {
        return dashboard.forCurrentUser();
    }

    @GetMapping({"/{report}", "/doctors/workload"})
    public Report report(@PathVariable(required = false) String report,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                         @RequestParam(required = false) Long departmentId,
                         @RequestParam(required = false) String granularity) {
        Entry e = entry(report == null ? "doctors" : report);
        return e.query().apply(Filter.of(from, to, departmentId, granularity, time.today()));
    }

    @GetMapping("/{report}/export.csv")
    public ResponseEntity<byte[]> export(@PathVariable String report,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) Long departmentId,
                                         @RequestParam(required = false) String granularity) {
        Report r = entry(report).query().apply(Filter.of(from, to, departmentId, granularity, time.today()));
        String name = "hms-" + r.report() + "-" + r.from() + "-to-" + r.to() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(Csv.of(r).getBytes(StandardCharsets.UTF_8));
    }

    private Entry entry(String report) {
        Entry e = reports.get(report);
        if (e == null) {
            throw BusinessException.notFound("Report", report);
        }
        if (!CurrentUser.require().getPermissions().contains(e.permission())) {
            throw BusinessException.forbidden("ACCESS_DENIED", "You do not have access to this report.");
        }
        return e;
    }
}
