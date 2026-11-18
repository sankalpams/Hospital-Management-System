package lk.hms.modules.departments.service;

import lk.hms.common.api.PageResponse;
import lk.hms.common.api.Paging;
import lk.hms.common.audit.AuditService;
import lk.hms.common.exception.BusinessException;
import lk.hms.modules.departments.dto.DepartmentDtos.DepartmentOption;
import lk.hms.modules.departments.dto.DepartmentDtos.DepartmentRequest;
import lk.hms.modules.departments.dto.DepartmentDtos.DepartmentResponse;
import lk.hms.modules.departments.entity.Department;
import lk.hms.modules.departments.repository.DepartmentRepository;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Departments are never deleted (appointments, staff and revenue reference them); they are
 * deactivated instead, and only when no active staff remain assigned.
 */
@Service
public class DepartmentService {

    private static final Map<String, String> SORTS = Map.of("name", "name", "code", "code", "type", "type");

    private final DepartmentRepository departments;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public DepartmentService(DepartmentRepository departments, AuditService audit, JdbcTemplate jdbc) {
        this.departments = departments;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PageResponse<DepartmentResponse> search(String q, String type, Boolean active, int page, int size, String sort) {
        String pattern = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        Department.Type t = type == null || type.isBlank() ? null : parseType(type);
        var result = departments.search(pattern, t, active, Paging.of(page, size, sort, SORTS, Sort.by("name")));
        Map<Long, Long> staff = activeStaffCounts(result.getContent().stream().map(Department::getId).toList());
        return PageResponse.of(result, d -> DepartmentResponse.from(d, staff.getOrDefault(d.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public List<DepartmentOption> options() {
        return departments.findByActiveTrueOrderByName().stream().map(DepartmentOption::from).toList();
    }

    @Transactional(readOnly = true)
    public DepartmentResponse get(Long id) {
        Department d = load(id);
        return DepartmentResponse.from(d, activeStaffCounts(List.of(id)).getOrDefault(id, 0L));
    }

    @Transactional
    public DepartmentResponse create(DepartmentRequest req) {
        String code = req.code().trim().toUpperCase(Locale.ROOT);
        if (departments.existsByCode(code)) {
            throw BusinessException.conflict("DEPARTMENT_CODE_TAKEN", "Department code " + code + " is already used.");
        }
        if (departments.existsByNameIgnoreCase(req.name().trim())) {
            throw BusinessException.conflict("DEPARTMENT_NAME_TAKEN", "A department with this name already exists.");
        }
        Department d = new Department();
        d.setCode(code);
        apply(d, req);
        departments.saveAndFlush(d);
        audit.recordChange("CREATE", "DEPARTMENT", d.getId(), "Created department " + d.getName(), null, snapshot(d));
        return DepartmentResponse.from(d, 0);
    }

    @Transactional
    public DepartmentResponse update(Long id, DepartmentRequest req) {
        Department d = load(id);
        String code = req.code().trim().toUpperCase(Locale.ROOT);
        if (!d.getCode().equals(code)) {
            throw BusinessException.badRequest("CODE_IMMUTABLE", "Department codes cannot be changed once created.");
        }
        if (departments.existsByNameIgnoreCaseAndIdNot(req.name().trim(), id)) {
            throw BusinessException.conflict("DEPARTMENT_NAME_TAKEN", "A department with this name already exists.");
        }
        Map<String, Object> before = snapshot(d);
        apply(d, req);
        departments.saveAndFlush(d);
        audit.recordChange("UPDATE", "DEPARTMENT", id, "Updated department " + d.getName(), before, snapshot(d));
        return get(id);
    }

    @Transactional
    public DepartmentResponse setActive(Long id, boolean active) {
        Department d = load(id);
        if (d.isActive() == active) {
            return get(id);
        }
        if (!active) {
            long staff = activeStaffCounts(List.of(id)).getOrDefault(id, 0L);
            if (staff > 0) {
                throw BusinessException.conflict("DEPARTMENT_IN_USE",
                        staff + " active staff member(s) are assigned to this department. Reassign them first.");
            }
        }
        d.setActive(active);
        departments.saveAndFlush(d);
        audit.recordChange("STATUS_CHANGE", "DEPARTMENT", id, (active ? "Activated " : "Deactivated ") + d.getName(),
                Map.of("active", !active), Map.of("active", active));
        return get(id);
    }

    private Department load(Long id) {
        return departments.findById(id).orElseThrow(() -> BusinessException.notFound("Department", id));
    }

    private static void apply(Department d, DepartmentRequest req) {
        d.setName(req.name().trim());
        d.setType(req.type());
        d.setDescription(blank(req.description()));
        d.setLocation(blank(req.location()));
        d.setPhoneExtension(blank(req.phoneExtension()));
    }

    /** One grouped query for the whole page instead of a count per row. */
    private Map<Long, Long> activeStaffCounts(List<Long> ids) {
        Map<Long, Long> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        String in = String.join(",", ids.stream().map(i -> "?").toList());
        jdbc.query("SELECT department_id, COUNT(*) c FROM employees WHERE status IN ('ACTIVE','ON_LEAVE') AND department_id IN ("
                        + in + ") GROUP BY department_id",
                rs -> { out.put(rs.getLong(1), rs.getLong(2)); }, ids.toArray());
        return out;
    }

    private static Map<String, Object> snapshot(Department d) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", d.getCode());
        m.put("name", d.getName());
        m.put("type", d.getType().name());
        m.put("location", Objects.toString(d.getLocation(), ""));
        m.put("active", d.isActive());
        return m;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static Department.Type parseType(String s) {
        try {
            return Department.Type.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("INVALID_FILTER", "Unknown department type: " + s);
        }
    }
}
