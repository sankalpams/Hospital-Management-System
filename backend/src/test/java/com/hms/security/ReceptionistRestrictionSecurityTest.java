package com.hms.security;

import com.hms.config.DevDataSeeder;
import com.hms.patient.PatientController;
import com.hms.patient.PatientService;
import com.hms.role.PermissionCode;
import com.hms.role.RoleCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReceptionistRestrictionSecurityTest {

    @Test
    @DisplayName("RECEPTIONIST role has PATIENT_READ and PATIENT_WRITE but strictly lacks EMR_READ and EMR_WRITE")
    void receptionistAuthorities_StrictlyLacksEmr() throws Exception {
        DevDataSeeder seeder = new DevDataSeeder(null, null, null, null, null, null, null, null, null, null);
        Method matrixMethod = DevDataSeeder.class.getDeclaredMethod("matrix");
        matrixMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<RoleCode, EnumSet<PermissionCode>> matrix = (Map<RoleCode, EnumSet<PermissionCode>>) matrixMethod.invoke(seeder);

        EnumSet<PermissionCode> recPerms = matrix.get(RoleCode.RECEPTIONIST);
        assertThat(recPerms).contains(PermissionCode.PATIENT_READ, PermissionCode.PATIENT_WRITE);
        assertThat(recPerms).doesNotContain(PermissionCode.EMR_READ, PermissionCode.EMR_WRITE, PermissionCode.VITALS_WRITE);
    }

    @Test
    @DisplayName("PatientController clinical history endpoint requires EMR_READ authority which locks out receptionists")
    void clinicalHistoryEndpoint_RequiresEmrRead() throws NoSuchMethodException {
        Method method = PatientController.class.getMethod("getClinicalHistory", java.util.UUID.class);
        PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuth).isNotNull();
        assertThat(preAuth.value()).isEqualTo("hasAuthority('EMR_READ')");
    }

    @Test
    @DisplayName("Patient merge endpoint requires administrative USER_MANAGE authority")
    void mergeEndpoint_RequiresAdminPrivilege() throws NoSuchMethodException {
        Method method = PatientController.class.getMethod("merge", java.util.UUID.class, com.hms.patient.PatientDtos.MergeRequest.class);
        PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuth).isNotNull();
        assertThat(preAuth.value()).isEqualTo("hasAuthority('USER_MANAGE')");
    }
}

