package lk.hms.modules.lab.controller;

import jakarta.validation.Valid;
import lk.hms.common.api.PageResponse;
import lk.hms.modules.lab.dto.LabDtos.*;
import lk.hms.modules.lab.service.LabService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class LabController {

    private final LabService service;

    public LabController(LabService service) {
        this.service = service;
    }

    @GetMapping("/lab-tests")
    @PreAuthorize("hasAnyAuthority('lab:request:read','lab:request:create','lab:catalogue:manage')")
    public List<LabTestView> tests(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return service.catalogue(activeOnly);
    }

    @PostMapping("/lab-tests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('lab:catalogue:manage')")
    public LabTestView createTest(@Valid @RequestBody LabTestRequest body) {
        return service.saveTest(null, body);
    }

    @PutMapping("/lab-tests/{id}")
    @PreAuthorize("hasAuthority('lab:catalogue:manage')")
    public LabTestView updateTest(@PathVariable Long id, @Valid @RequestBody LabTestRequest body) {
        return service.saveTest(id, body);
    }

    @PostMapping("/encounters/{encounterId}/lab-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('lab:request:create')")
    public RequestView order(@PathVariable Long encounterId, @Valid @RequestBody OrderRequest body) {
        return service.order(encounterId, body);
    }

    @GetMapping("/lab-requests")
    @PreAuthorize("hasAuthority('lab:request:read')")
    public PageResponse<RequestView> search(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) Long patientId,
                                            @RequestParam(required = false) String priority,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "25") int size) {
        return service.search(status, patientId, priority, q, from, to, page, size);
    }

    @GetMapping("/lab-requests/{id}")
    @PreAuthorize("hasAuthority('lab:request:read')")
    public RequestView get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/lab-items/{id}/collect-sample")
    @PreAuthorize("hasAuthority('lab:sample:collect')")
    public RequestView collect(@PathVariable Long id) {
        return service.collectSample(id);
    }

    @PostMapping("/lab-items/{id}/start-processing")
    @PreAuthorize("hasAuthority('lab:sample:collect')")
    public RequestView start(@PathVariable Long id) {
        return service.startProcessing(id);
    }

    @PostMapping("/lab-items/{id}/result")
    @PreAuthorize("hasAuthority('lab:result:enter')")
    public RequestView result(@PathVariable Long id, @Valid @RequestBody ResultRequest body) {
        return service.enterResult(id, body);
    }

    @PostMapping("/lab-items/{id}/review")
    @PreAuthorize("hasAuthority('lab:result:review')")
    public RequestView review(@PathVariable Long id) {
        return service.review(id);
    }

    @PostMapping("/lab-items/{id}/cancel")
    @PreAuthorize("hasAuthority('lab:request:create')")
    public RequestView cancel(@PathVariable Long id, @Valid @RequestBody ReasonRequest body) {
        return service.cancelItem(id, body.reason());
    }
}
