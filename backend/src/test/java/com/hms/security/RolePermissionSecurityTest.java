package com.hms.security;

import com.hms.config.DevDataSeeder;
import com.hms.role.PermissionCode;
import com.hms.role.RoleCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RolePermissionSecurityTest {

    @Test
    @DisplayName("DevDataSeeder matrix grants ADMIN all permissions")
    void adminHasAllPermissions() throws Exception {
        Map<RoleCode, EnumSet<PermissionCode>> matrix = getMatrix();
        assertThat(matrix.get(RoleCode.ADMIN)).containsExactlyInAnyOrder(PermissionCode.values());
    }

    @Test
    @DisplayName("RECEPTIONIST must NOT have EMR_READ or EMR_WRITE or VITALS_WRITE")
    void receptionistDeniedClinicalPermissions() throws Exception {
        Map<RoleCode, EnumSet<PermissionCode>> matrix = getMatrix();
        EnumSet<PermissionCode> receptionPermissions = matrix.get(RoleCode.RECEPTIONIST);

        assertThat(receptionPermissions)
                .doesNotContain(PermissionCode.EMR_READ)
                .doesNotContain(PermissionCode.EMR_WRITE)
                .doesNotContain(PermissionCode.VITALS_WRITE)
                .doesNotContain(PermissionCode.USER_MANAGE)
                .doesNotContain(PermissionCode.AUDIT_READ);

        assertThat(receptionPermissions).contains(
                PermissionCode.PATIENT_READ,
                PermissionCode.PATIENT_WRITE,
                PermissionCode.APPOINTMENT_READ,
                PermissionCode.APPOINTMENT_WRITE,
                PermissionCode.INVOICE_READ
        );
    }

    @Test
    @DisplayName("NURSE has EMR_READ and VITALS_WRITE but NOT EMR_WRITE or INVOICE_VOID")
    void nursePermissionBoundaries() throws Exception {
        Map<RoleCode, EnumSet<PermissionCode>> matrix = getMatrix();
        EnumSet<PermissionCode> nursePermissions = matrix.get(RoleCode.NURSE);

        assertThat(nursePermissions)
                .contains(PermissionCode.EMR_READ, PermissionCode.VITALS_WRITE, PermissionCode.PATIENT_READ)
                .doesNotContain(PermissionCode.EMR_WRITE)
                .doesNotContain(PermissionCode.INVOICE_VOID)
                .doesNotContain(PermissionCode.USER_MANAGE);
    }

    @Test
    @DisplayName("ACCOUNTANT has INVOICE_READ, INVOICE_WRITE, INVOICE_VOID but NOT clinical or user permissions")
    void accountantPermissionBoundaries() throws Exception {
        Map<RoleCode, EnumSet<PermissionCode>> matrix = getMatrix();
        EnumSet<PermissionCode> accountantPermissions = matrix.get(RoleCode.ACCOUNTANT);

        assertThat(accountantPermissions)
                .contains(
                        PermissionCode.INVOICE_READ,
                        PermissionCode.INVOICE_WRITE,
                        PermissionCode.INVOICE_VOID,
                        PermissionCode.REPORT_READ,
                        PermissionCode.REPORT_EXPORT
                )
                .doesNotContain(
                        PermissionCode.EMR_READ,
                        PermissionCode.EMR_WRITE,
                        PermissionCode.PATIENT_WRITE,
                        PermissionCode.USER_MANAGE,
                        PermissionCode.AUDIT_READ
                );
    }

    @Test
    @DisplayName("Only ADMIN has USER_MANAGE, USER_READ, AUDIT_READ, SETTINGS_MANAGE")
    void onlyAdminHasAdministrativePermissions() throws Exception {
        Map<RoleCode, EnumSet<PermissionCode>> matrix = getMatrix();

        for (RoleCode role : RoleCode.values()) {
            if (role == RoleCode.ADMIN) {
                assertThat(matrix.get(role)).contains(
                        PermissionCode.USER_MANAGE,
                        PermissionCode.USER_READ,
                        PermissionCode.AUDIT_READ,
                        PermissionCode.SETTINGS_MANAGE
                );
            } else {
                assertThat(matrix.get(role)).doesNotContain(
                        PermissionCode.USER_MANAGE,
                        PermissionCode.USER_READ,
                        PermissionCode.AUDIT_READ,
                        PermissionCode.SETTINGS_MANAGE
                );
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<RoleCode, EnumSet<PermissionCode>> getMatrix() throws Exception {
        DevDataSeeder seeder = new DevDataSeeder(null, null, null, null, null, null, null, null, null, null);
        Method matrixMethod = DevDataSeeder.class.getDeclaredMethod("matrix");
        matrixMethod.setAccessible(true);
        return (Map<RoleCode, EnumSet<PermissionCode>>) matrixMethod.invoke(seeder);
    }
}

