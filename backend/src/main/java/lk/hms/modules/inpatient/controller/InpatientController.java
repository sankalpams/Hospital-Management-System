package lk.hms.modules.inpatient.controller;

import jakarta.validation.Valid;
import lk.hms.common.api.PageResponse;
import lk.hms.modules.inpatient.dto.InpatientDtos.*;
import lk.hms.modules.inpatient.service.InpatientService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class InpatientController {

    private final InpatientService service;

    public InpatientController(InpatientService service) {
        this.service = service;
    }

    @GetMapping("/wards")
    @PreAuthorize("hasAnyAuthority('admission:read','ward:manage')")
    public List<WardView> bedBoard() {
        return service.bedBoard();
    }

    @PostMapping("/wards")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ward:manage')")
    public WardView createWard(@Valid @RequestBody WardRequest body) {
        return service.createWard(body);
    }

    @PostMapping("/wards/{id}/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ward:manage')")
    public WardView addRoom(@PathVariable Long id, @Valid @RequestBody RoomRequest body) {
        return service.addRoom(id, body);
    }

    @PostMapping("/beds/{id}/status")
    @PreAuthorize("hasAnyAuthority('ward:manage','admission:manage')")
    public ResponseEntity<Void> bedStatus(@PathVariable Long id, @Valid @RequestBody BedStatusRequest body) {
        service.setBedStatus(id, body);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/admissions")
    @PreAuthorize("hasAuthority('admission:read')")
    public PageResponse<AdmissionView> admissions(@RequestParam(required = false) String status,
                                                  @RequestParam(required = false) Long patientId,
                                                  @RequestParam(required = false) String q,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "25") int size) {
        return service.search(status, patientId, q, page, size);
    }

    @GetMapping("/admissions/{id}")
    @PreAuthorize("hasAuthority('admission:read')")
    public AdmissionView admission(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/admissions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('admission:request')")
    public AdmissionView request(@Valid @RequestBody AdmissionRequest body) {
        return service.request(body);
    }

    @PostMapping("/admissions/{id}/admit")
    @PreAuthorize("hasAuthority('admission:manage')")
    public AdmissionView admit(@PathVariable Long id, @Valid @RequestBody BedRequest body) {
        return service.admit(id, body.bedId());
    }

    @PostMapping("/admissions/{id}/transfer")
    @PreAuthorize("hasAuthority('admission:manage')")
    public AdmissionView transfer(@PathVariable Long id, @Valid @RequestBody BedRequest body) {
        return service.transfer(id, body.bedId());
    }

    @PostMapping("/admissions/{id}/discharge")
    @PreAuthorize("hasAuthority('admission:manage')")
    public AdmissionView discharge(@PathVariable Long id, @Valid @RequestBody DischargeRequest body) {
        return service.discharge(id, body);
    }

    @PostMapping("/admissions/{id}/cancel")
    @PreAuthorize("hasAuthority('admission:manage')")
    public AdmissionView cancel(@PathVariable Long id, @Valid @RequestBody ReasonRequest body) {
        return service.cancel(id, body.reason());
    }
}
