package lk.hms.modules.emr.controller;

import jakarta.validation.Valid;
import lk.hms.modules.emr.dto.EmrDtos.*;
import lk.hms.modules.emr.service.EmrService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class EmrController {

    private final EmrService service;

    public EmrController(EmrService service) {
        this.service = service;
    }

    /** {@code reason} is the break-the-glass justification when the caller is not treating the patient. */
    @GetMapping("/patients/{patientId}/emr")
    @PreAuthorize("hasAuthority('emr:read')")
    public EmrView record(@PathVariable Long patientId, @RequestParam(required = false) String reason) {
        return service.patientRecord(patientId, reason);
    }

    @GetMapping("/encounters/{id}")
    @PreAuthorize("hasAuthority('emr:read')")
    public EncounterView encounter(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return service.encounter(id, reason);
    }

    @PostMapping("/encounters/{id}/diagnoses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('emr:write')")
    public DiagnosisView diagnosis(@PathVariable Long id, @Valid @RequestBody DiagnosisRequest body) {
        return service.addDiagnosis(id, body);
    }

    @PostMapping("/encounters/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('emr:write')")
    public NoteView note(@PathVariable Long id, @Valid @RequestBody NoteRequest body) {
        return service.addDoctorNote(id, body);
    }

    @PostMapping("/encounters/{id}/nursing-notes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('nursing-note:write')")
    public NoteView nursingNote(@PathVariable Long id, @Valid @RequestBody TextRequest body) {
        return service.addNursingNote(id, body);
    }

    @PutMapping("/notes/{id}")
    @PreAuthorize("hasAnyAuthority('emr:write','nursing-note:write')")
    public NoteView editNote(@PathVariable Long id, @Valid @RequestBody TextRequest body) {
        return service.editNote(id, body);
    }

    @PostMapping("/notes/{id}/sign")
    @PreAuthorize("hasAnyAuthority('emr:write','nursing-note:write')")
    public NoteView sign(@PathVariable Long id) {
        return service.signNote(id);
    }

    @PostMapping("/notes/{id}/addenda")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('emr:write','nursing-note:write')")
    public NoteView addendum(@PathVariable Long id, @Valid @RequestBody TextRequest body) {
        return service.addAddendum(id, body);
    }

    @PostMapping("/patients/{patientId}/vitals")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('vitals:record')")
    public VitalsView vitals(@PathVariable Long patientId, @Valid @RequestBody VitalsRequest body) {
        return service.recordVitals(patientId, body);
    }

    @PostMapping("/patients/{patientId}/allergies")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('allergy:write')")
    public AllergyView allergy(@PathVariable Long patientId, @Valid @RequestBody AllergyRequest body) {
        return service.addAllergy(patientId, body);
    }

    @PostMapping("/allergies/{id}/resolve")
    @PreAuthorize("hasAuthority('allergy:write')")
    public AllergyView resolve(@PathVariable Long id) {
        return service.resolveAllergy(id);
    }

    @PostMapping("/encounters/{id}/prescriptions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('prescription:create')")
    public PrescriptionView prescribe(@PathVariable Long id, @Valid @RequestBody PrescriptionRequest body) {
        return service.prescribe(id, body);
    }

    @PostMapping("/prescriptions/{id}/cancel")
    @PreAuthorize("hasAuthority('prescription:create')")
    public PrescriptionView cancelPrescription(@PathVariable Long id) {
        return service.cancelPrescription(id);
    }

    @GetMapping("/medicines/options")
    @PreAuthorize("hasAnyAuthority('medicine:read','prescription:create')")
    public List<MedicineOption> medicineOptions(@RequestParam(required = false) String q) {
        return service.medicineOptions(q);
    }
}
