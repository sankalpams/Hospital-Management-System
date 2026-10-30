package com.hms.patient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hms.audit.AuditAction;
import com.hms.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PatientExportService {
    private final PatientRepository patientRepository;
    private final AuditService auditService;

    public record ExportData(byte[] bytes, String contentType, String filename) {}

    @Transactional(readOnly = true)
    public ExportData export(UUID patientId, String format) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> com.hms.common.AppException.notFound("Patient not found."));

        auditService.record(AuditAction.EXPORT, "Patient", patient.getId().toString(), null, null);

        if ("pdf".equalsIgnoreCase(format)) {
            return exportPdf(patient);
        } else {
            return exportJson(patient);
        }
    }

    private ExportData exportJson(Patient patient) {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mrn", patient.getMrn());
        data.put("nic", patient.getNic());
        data.put("fullName", patient.fullName());
        data.put("firstName", patient.getFirstName());
        data.put("lastName", patient.getLastName());
        data.put("dateOfBirth", patient.getDateOfBirth());
        data.put("age", patient.getAge());
        data.put("sex", patient.getSex());
        data.put("phone", patient.getPhone());
        data.put("email", patient.getEmail());
        data.put("address", Map.of(
                "line1", Objects.toString(patient.getAddressLine1(), ""),
                "line2", Objects.toString(patient.getAddressLine2(), ""),
                "city", Objects.toString(patient.getCity(), ""),
                "district", patient.getDistrict()
        ));
        data.put("emergencyContact", Map.of(
                "name", Objects.toString(patient.getGuardianName(), ""),
                "relationship", Objects.toString(patient.getGuardianRelationship(), ""),
                "phone", Objects.toString(patient.getGuardianPhone(), "")
        ));
        data.put("bloodGroup", patient.getBloodGroup());
        data.put("chronicConditions", patient.getChronicConditions());
        data.put("insurance", Map.of(
                "provider", Objects.toString(patient.getInsuranceProvider(), ""),
                "policyNumber", Objects.toString(patient.getInsurancePolicyNumber(), "")
        ));
        data.put("allergies", patient.getAllergies().stream().map(a -> Map.of(
                "allergen", a.getAllergen(),
                "reaction", a.getReaction(),
                "severity", a.getSeverity().name(),
                "notes", Objects.toString(a.getNotes(), "")
        )).toList());
        data.put("consents", patient.getConsents().stream().map(c -> Map.of(
                "purpose", c.getPurpose(),
                "givenDate", c.getGivenDate().toString(),
                "withdrawnDate", c.getWithdrawnDate() != null ? c.getWithdrawnDate().toString() : "",
                "witnessName", Objects.toString(c.getWitnessName(), "")
        )).toList());

        try {
            byte[] bytes = mapper.writeValueAsBytes(data);
            return new ExportData(bytes, "application/json", "patient-" + patient.getMrn() + ".json");
        } catch (Exception e) {
            throw new RuntimeException("Could not serialize patient export: " + e.getMessage(), e);
        }
    }

    private ExportData exportPdf(Patient patient) {
        // Pure Java valid PDF 1.4 generation without external library dependency
        String title = "HOSPITAL MANAGEMENT SYSTEM - PATIENT SUMMARY";
        String mrn = "MRN: " + patient.getMrn();
        String name = "Name: " + patient.fullName();
        String nic = "NIC/Passport: " + (patient.getNic() != null ? patient.getNic() : "N/A");
        String dob = "DOB: " + patient.getDateOfBirth().format(DateTimeFormatter.ISO_DATE) + " (" + patient.getAgeSex() + ")";
        String phone = "Contact: " + (patient.getPhone() != null ? patient.getPhone() : "N/A");
        String district = "District: " + patient.getDistrict();
        String blood = "Blood Group: " + (patient.getBloodGroup() != null ? patient.getBloodGroup() : "Unknown");
        String conditions = "Chronic Conditions: " + (patient.getChronicConditions() != null ? patient.getChronicConditions() : "None reported");

        StringBuilder allergiesText = new StringBuilder("Allergies: ");
        if (patient.getAllergies().isEmpty()) {
            allergiesText.append("No known allergies (NKDA)");
        } else {
            patient.getAllergies().forEach(a -> allergiesText.append(a.getAllergen()).append(" [").append(a.getSeverity()).append("], "));
        }

        List<String> lines = List.of(
                title,
                "==================================================",
                mrn,
                name,
                nic,
                dob,
                phone,
                district,
                blood,
                conditions,
                allergiesText.toString(),
                "==================================================",
                "Confidential Clinical Document - Hospital Management System"
        );

        byte[] pdfBytes = generateSimplePdf(lines);
        return new ExportData(pdfBytes, "application/pdf", "patient-" + patient.getMrn() + ".pdf");
    }

    private byte[] generateSimplePdf(List<String> lines) {
        StringBuilder streamContent = new StringBuilder();
        streamContent.append("BT\n/F1 12 Tf\n50 750 Td\n18 TL\n");
        for (String line : lines) {
            String sanitized = line.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
            streamContent.append("(").append(sanitized).append(") '\n");
        }
        streamContent.append("ET\n");

        byte[] streamBytes = streamContent.toString().getBytes(StandardCharsets.ISO_8859_1);
        int streamLength = streamBytes.length;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            List<Integer> xrefs = new ArrayList<>();
            baos.write("%PDF-1.4\n".getBytes(StandardCharsets.ISO_8859_1));

            // obj 1: Catalog
            xrefs.add(baos.size());
            baos.write("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));

            // obj 2: Pages
            xrefs.add(baos.size());
            baos.write("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));

            // obj 3: Page
            xrefs.add(baos.size());
            baos.write("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));

            // obj 4: Contents
            xrefs.add(baos.size());
            baos.write(("4 0 obj\n<< /Length " + streamLength + " >>\nstream\n").getBytes(StandardCharsets.ISO_8859_1));
            baos.write(streamBytes);
            baos.write("\nendstream\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));

            // obj 5: Font
            xrefs.add(baos.size());
            baos.write("5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));

            // xref table
            int xrefOffset = baos.size();
            baos.write("xref\n0 6\n0000000000 65535 f \n".getBytes(StandardCharsets.ISO_8859_1));
            for (int offset : xrefs) {
                baos.write(String.format(Locale.ROOT, "%010d 00000 n \n", offset).getBytes(StandardCharsets.ISO_8859_1));
            }

            // trailer
            baos.write(("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n" + xrefOffset + "\n%%EOF\n").getBytes(StandardCharsets.ISO_8859_1));
        } catch (IOException e) {
            throw new RuntimeException("Error assembling PDF", e);
        }

        return baos.toByteArray();
    }
}

