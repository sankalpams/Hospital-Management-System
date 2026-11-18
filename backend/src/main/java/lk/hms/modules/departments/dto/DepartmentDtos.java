package lk.hms.modules.departments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lk.hms.modules.departments.entity.Department;

public final class DepartmentDtos {

    private DepartmentDtos() {
    }

    public record DepartmentResponse(Long id, String code, String name, String type, String description,
                                     String location, String phoneExtension, boolean active,
                                     long activeStaff) {
        public static DepartmentResponse from(Department d, long activeStaff) {
            return new DepartmentResponse(d.getId(), d.getCode(), d.getName(), d.getType().name(), d.getDescription(),
                    d.getLocation(), d.getPhoneExtension(), d.isActive(), activeStaff);
        }
    }

    /** Lightweight shape for dropdowns. */
    public record DepartmentOption(Long id, String code, String name, String type) {
        public static DepartmentOption from(Department d) {
            return new DepartmentOption(d.getId(), d.getCode(), d.getName(), d.getType().name());
        }
    }

    public record DepartmentRequest(
            @NotBlank @Size(max = 20) @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "Use letters, numbers and hyphens only.")
            String code,
            @NotBlank @Size(max = 100) String name,
            @NotNull Department.Type type,
            @Size(max = 255) String description,
            @Size(max = 100) String location,
            @Size(max = 10) @Pattern(regexp = "^[0-9]*$", message = "Extension must be digits.") String phoneExtension) {
    }
}
