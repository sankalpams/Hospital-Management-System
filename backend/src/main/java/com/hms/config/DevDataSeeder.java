package com.hms.config;

import com.hms.department.Department;
import com.hms.department.DepartmentRepository;
import com.hms.doctor.Doctor;
import com.hms.doctor.DoctorRepository;
import com.hms.employee.Employee;
import com.hms.employee.EmployeeRepository;
import com.hms.patient.*;
import com.hms.role.*;
import com.hms.user.User;
import com.hms.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements CommandLineRunner {
    private final PermissionRepository permissions;
    private final RoleRepository roles;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final DepartmentRepository departments;
    private final DoctorRepository doctors;
    private final EmployeeRepository employees;
    private final PatientRepository patients;
    private final PatientAllergyRepository allergies;
    private final MrnGeneratorService mrnGenerator;

    @Override
    @Transactional
    public void run(String... args) {
        // 1. Permissions & Roles
        for (PermissionCode code : PermissionCode.values()) {
            permissions.findByCode(code).orElseGet(() -> permissions.save(new Permission(code, description(code))));
        }
        Map<RoleCode, EnumSet<PermissionCode>> matrix = matrix();
        for (RoleCode code : RoleCode.values()) {
            Role role = roles.findWithPermissionsByCode(code).orElseGet(() -> new Role(code, title(code), description(code)));
            role.getPermissions().clear();
            matrix.get(code).forEach(permission -> role.getPermissions().add(permissions.findByCode(permission).orElseThrow()));
            roles.save(role);
        }

        // 2. Base 7 Demo Users
        seedUser("admin", "admin@hms.local", "Amara", "Perera", "+94771234567", RoleCode.ADMIN);
        User doctorUser1 = seedUser("doctor", "doctor@hms.local", "Nimal", "Fernando", "+94771234568", RoleCode.DOCTOR);
        User nurseUser1 = seedUser("nurse", "nurse@hms.local", "Samanthi", "Silva", "+94771234569", RoleCode.NURSE);
        User recUser1 = seedUser("reception", "reception@hms.local", "Kavindi", "Jayasinghe", "+94771234570", RoleCode.RECEPTIONIST);
        User labUser1 = seedUser("lab", "lab@hms.local", "Ruwan", "Bandara", "+94771234571", RoleCode.LAB_STAFF);
        User pharmUser1 = seedUser("pharmacy", "pharmacy@hms.local", "Ishara", "Gunasekara", "+94771234572", RoleCode.PHARMACIST);
        User accUser1 = seedUser("accounts", "accounts@hms.local", "Tharindu", "Wijesinghe", "+94771234573", RoleCode.ACCOUNTANT);

        // 3. Departments (8 core departments)
        Department genMed = seedDept("GEN_MED", "General Medicine", "Primary outpatient and inpatient medical care", "+94112345001", "Building A, Floor 1");
        Department genSurg = seedDept("GEN_SURG", "General Surgery", "Comprehensive surgical operations and trauma care", "+94112345002", "Building B, Floor 2");
        Department ped = seedDept("PED", "Pediatrics", "Specialized pediatric medicine and child wellness", "+94112345003", "Building A, Floor 2");
        Department obgyn = seedDept("OBGYN", "Obstetrics & Gynecology", "Maternity, women's health and neonatal support", "+94112345004", "Building C, Floor 1");
        Department card = seedDept("CARD", "Cardiology", "Cardiovascular diagnosis, ECG, echo and critical coronary care", "+94112345005", "Building B, Floor 3");
        Department neur = seedDept("NEUR", "Neurology", "Brain and nervous system diagnostic and clinical care", "+94112345006", "Building B, Floor 4");
        Department orth = seedDept("ORTH", "Orthopedics", "Bone, joint and musculoskeletal trauma surgery", "+94112345007", "Building A, Floor 3");
        Department derm = seedDept("DERM", "Dermatology", "Skin disorders, cosmetic and therapeutic dermatology", "+94112345008", "Building C, Floor 2");

        List<Department> deptList = List.of(genMed, genSurg, ped, obgyn, card, neur, orth, derm);

        // 4. Doctors (12 doctors)
        seedDoc(doctorUser1, genMed, "Consultant Physician", "MBBS (Colombo), MD (Med), FRCP", "SLMC-24101", 3000.00);

        String[][] docData = {
                {"dr.kasun", "kasun.d@hms.local", "Kasun", "Dissanayake", "+94772345001", "General Surgeon", "MBBS (Peradeniya), MS (Surgery), FRCS", "SLMC-24102", "3500.00"},
                {"dr.priyanthi", "priyanthi.a@hms.local", "Priyanthi", "Alahakoon", "+94772345002", "Consultant Pediatrician", "MBBS (Kelaniya), DCH, MD (Pediatrics)", "SLMC-24103", "2800.00"},
                {"dr.rohan", "rohan.w@hms.local", "Rohan", "Wickramasinghe", "+94772345003", "Obstetrician & Gynecologist", "MBBS (Colombo), MS (O&G), MRCOG", "SLMC-24104", "3500.00"},
                {"dr.anura", "anura.s@hms.local", "Anura", "Senanayake", "+94772345004", "Cardiologist", "MBBS (Colombo), MD (Cardiology), FACC", "SLMC-24105", "4000.00"},
                {"dr.manel", "manel.r@hms.local", "Manel", "Rajapaksa", "+94772345005", "Neurologist", "MBBS (Ruhuna), MD (Neurology), MRCP", "SLMC-24106", "3800.00"},
                {"dr.sunil", "sunil.k@hms.local", "Sunil", "Kulatunga", "+94772345006", "Orthopedic Surgeon", "MBBS (Colombo), MS (Orth), FRCS (Orth)", "SLMC-24107", "3500.00"},
                {"dr.shanthi", "shanthi.p@hms.local", "Shanthi", "Perera", "+94772345007", "Dermatologist", "MBBS (Sri Jayewardenepura), MD (Derm)", "SLMC-24108", "2500.00"},
                {"dr.dinesh", "dinesh.g@hms.local", "Dinesh", "Gunawardena", "+94772345008", "Physician", "MBBS (Colombo), MD (Medicine)", "SLMC-24109", "3000.00"},
                {"dr.dilhani", "dilhani.f@hms.local", "Dilhani", "Fernando", "+94772345009", "Pediatrician", "MBBS (Peradeniya), MD (Ped)", "SLMC-24110", "2800.00"},
                {"dr.chamara", "chamara.l@hms.local", "Chamara", "Liyanage", "+94772345010", "Cardiologist", "MBBS (Kelaniya), MD, FRCP", "SLMC-24111", "4000.00"},
                {"dr.nadeeka", "nadeeka.j@hms.local", "Nadeeka", "Jayawardena", "+94772345011", "General Surgeon", "MBBS (Colombo), MS, FRCS", "SLMC-24112", "3500.00"}
        };

        for (int i = 0; i < docData.length; i++) {
            String[] d = docData[i];
            User u = seedUser(d[0], d[1], d[2], d[3], d[4], RoleCode.DOCTOR);
            Department dept = deptList.get((i + 1) % deptList.size());
            seedDoc(u, dept, d[5], d[6], d[7], Double.parseDouble(d[8]));
        }

        // 5. Staff & Employees (10 employees across roles)
        seedEmp(nurseUser1, genMed, "EMP-NUR-001", "Nursing Sister / Ward In-Charge", "+94771234569");
        seedEmp(recUser1, genMed, "EMP-REC-001", "Chief Reception Officer", "+94771234570");
        seedEmp(labUser1, card, "EMP-LAB-001", "Senior Medical Laboratory Technologist", "+94771234571");
        seedEmp(pharmUser1, genMed, "EMP-PHM-001", "Chief Pharmacist", "+94771234572");
        seedEmp(accUser1, null, "EMP-ACC-001", "Senior Hospital Accountant", "+94771234573");

        String[][] staffData = {
                {"nurse2", "anoma.s@hms.local", "Anoma", "Seneviratne", "+94773456001", RoleCode.NURSE, "EMP-NUR-002", "Staff Nurse - ICU"},
                {"nurse3", "kumari.r@hms.local", "Kumari", "Ratnayake", "+94773456002", RoleCode.NURSE, "EMP-NUR-003", "Staff Nurse - Pediatrics"},
                {"rec2", "lasantha.w@hms.local", "Lasantha", "Weerasinghe", "+94773456003", RoleCode.RECEPTIONIST, "EMP-REC-002", "Registration Desk Clerk"},
                {"lab2", "chathura.h@hms.local", "Chathura", "Hettiarachchi", "+94773456004", RoleCode.LAB_STAFF, "EMP-LAB-002", "Pathology Lab Assistant"},
                {"pharm2", "hansi.k@hms.local", "Hansi", "Kariyawasam", "+94773456005", RoleCode.PHARMACIST, "EMP-PHM-002", "Dispensary Pharmacist"}
        };
        for (String[] s : staffData) {
            User u = seedUser(s[0], s[1], s[2], s[3], s[4], RoleCode.valueOf(s[5]));
            seedEmp(u, genMed, s[6], s[7], s[4]);
        }

        // 6. Patients (~500 realistic Sri Lankan patients + 5 duplicate pairs)
        if (patients.count() < 100) {
            seedPatients();
        }
    }

    private void seedPatients() {
        Random rnd = new Random(42);

        String[] firstNamesMale = {
                "Nimal", "Kamal", "Sunil", "Priyantha", "Bandula", "Chaminda", "Sanath", "Lasantha",
                "Mahela", "Kumar", "Anura", "Dinesh", "Ruwan", "Thilak", "Sajith", "Rohan", "Kasun",
                "Nuwan", "Chamara", "Roshan", "Sandun", "Malith", "Chathura", "Danushka", "Dineth",
                "Indika", "Pradeep", "Samantha", "Saman", "Sampath", "Duminda", "Chinthaka"
        };
        String[] firstNamesFemale = {
                "Samanthi", "Kavindi", "Nilmini", "Shanthi", "Champa", "Kumari", "Sandamali", "Anoma",
                "Malkanthi", "Renuka", "Rohini", "Swarna", "Kusum", "Manel", "Kanthi", "Anusha",
                "Priyadarshani", "Nirosha", "Dilhani", "Nadeeka", "Chandima", "Menaka", "Hansi",
                "Oshadi", "Rashmi", "Tharushi", "Dinithi", "Sachini", "Pavithra", "Dulani"
        };
        String[] lastNames = {
                "Perera", "Silva", "Fernando", "Jayasinghe", "Bandara", "Gunasekara", "Wijesinghe",
                "Dissanayake", "Rajapaksa", "Senanayake", "Alahakoon", "Rathnayake", "Kulatunga",
                "Abeysekera", "Wickramasinghe", "Gunawardena", "Karunaratne", "Liyanage",
                "Jayawardena", "Weerasinghe", "Hettiarachchi", "Gamage", "Ranasinghe", "Nanayakkara"
        };
        String[] districts = {
                "Colombo", "Gampaha", "Kalutara", "Kandy", "Matale", "Nuwara Eliya", "Galle",
                "Matara", "Hambantota", "Jaffna", "Kilinochchi", "Mannar", "Vavuniya", "Mullaitivu",
                "Batticaloa", "Ampara", "Trincomalee", "Kurunegala", "Puttalam", "Anuradhapura",
                "Polonnaruwa", "Badulla", "Monaragala", "Ratnapura", "Kegalle"
        };
        String[] bloodGroups = {"A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"};
        String[] chronicConditionsPool = {
                "Hypertension", "Type 2 Diabetes Mellitus", "Bronchial Asthma", "Ischemic Heart Disease",
                "Dyslipidemia", "Hypothyroidism", "Chronic Kidney Disease Stage 2", "Osteoarthritis",
                "Gastroesophageal Reflux Disease (GERD)", "Migraine"
        };
        String[][] allergyPool = {
                {"Penicillin", "Anaphylactic shock, bronchospasm", "SEVERE"},
                {"Amoxicillin", "Diffuse maculopapular rash, pruritus", "MODERATE"},
                {"Aspirin", "Facial angioedema, urticaria", "SEVERE"},
                {"Sulfa drugs", "Erythematous rash, mild fever", "MODERATE"},
                {"Cephalexin", "Mild skin hives, itching", "MILD"},
                {"Ibuprofen / NSAIDs", "Dyspnea, wheezing", "SEVERE"},
                {"Iodine contrast", "Nausea, mild erythema", "MILD"},
                {"Peanuts", "Lip swelling, acute urticaria", "SEVERE"}
        };
        String[] insurancePool = {
                "Sri Lanka Insurance Corporation", "Ceylinco Life Healthcare", "AIA Insurance Lanka",
                "Allianz Insurance Lanka", "Softlogic Life Health Plan", "Union Assurance Health Shield", ""
        };

        List<Patient> batch = new ArrayList<>(520);

        for (int i = 1; i <= 500; i++) {
            boolean isMale = rnd.nextBoolean();
            String first = isMale ? firstNamesMale[rnd.nextInt(firstNamesMale.length)] : firstNamesFemale[rnd.nextInt(firstNamesFemale.length)];
            String last = lastNames[rnd.nextInt(lastNames.length)];
            int age = 5 + rnd.nextInt(75);
            LocalDate dob = LocalDate.now().minusYears(age).minusDays(rnd.nextInt(360));
            String sex = isMale ? "MALE" : "FEMALE";
            String district = districts[rnd.nextInt(districts.length)];
            String phone = String.format("+947%d%07d", 1 + rnd.nextInt(7), rnd.nextInt(10000000));
            String blood = bloodGroups[rnd.nextInt(bloodGroups.length)];
            String nic = (rnd.nextBoolean() || age >= 40)
                    ? String.format("%09d%s", 500000000 + rnd.nextInt(400000000), rnd.nextBoolean() ? "V" : "X")
                    : String.format("19%02d%08d", 60 + rnd.nextInt(40), rnd.nextInt(100000000));

            Patient p = new Patient();
            p.setMrn(String.format("MRN-2026-%06d", i));
            p.setNic(nic);
            p.setFirstName(first);
            p.setLastName(last);
            p.setDateOfBirth(dob);
            p.setSex(sex);
            p.setPhone(phone);
            p.setEmail(first.toLowerCase() + "." + last.toLowerCase() + i + "@mail.lk");
            p.setAddressLine1("No " + (10 + rnd.nextInt(250)) + ", Main Street");
            p.setCity(district);
            p.setDistrict(district);
            p.setBloodGroup(blood);
            p.setActive(true);

            if (rnd.nextBoolean()) {
                p.setChronicConditions(chronicConditionsPool[rnd.nextInt(chronicConditionsPool.length)]);
            }
            String ins = insurancePool[rnd.nextInt(insurancePool.length)];
            if (!ins.isBlank()) {
                p.setInsuranceProvider(ins);
                p.setInsurancePolicyNumber("POL-" + (100000 + rnd.nextInt(900000)));
            }

            p.setGuardianName(isMale ? "Mrs. " + last : "Mr. " + last);
            p.setGuardianRelationship(isMale ? "Spouse" : "Spouse");
            p.setGuardianPhone(phone);

            batch.add(p);
        }

        // 5 Intentional Near-Duplicate Pairs for testing detection and merge
        // Pair 1: Exact NIC match
        Patient dup1A = batch.get(10);
        Patient dup1B = new Patient();
        dup1B.setMrn("MRN-2026-000501");
        dup1B.setNic(dup1A.getNic());
        dup1B.setFirstName(dup1A.getFirstName());
        dup1B.setLastName(dup1A.getLastName());
        dup1B.setDateOfBirth(dup1A.getDateOfBirth());
        dup1B.setSex(dup1A.getSex());
        dup1B.setPhone("+94779998811");
        dup1B.setDistrict(dup1A.getDistrict());
        dup1B.setActive(true);
        batch.add(dup1B);

        // Pair 2: Same DOB, same phone, same family name
        Patient dup2A = batch.get(20);
        Patient dup2B = new Patient();
        dup2B.setMrn("MRN-2026-000502");
        dup2B.setNic(null);
        dup2B.setFirstName("Sunil");
        dup2B.setLastName(dup2A.getLastName());
        dup2B.setDateOfBirth(dup2A.getDateOfBirth());
        dup2B.setSex(dup2A.getSex());
        dup2B.setPhone(dup2A.getPhone());
        dup2B.setDistrict(dup2A.getDistrict());
        dup2B.setActive(true);
        batch.add(dup2B);

        // Pair 3: Same DOB, same phone, different first name
        Patient dup3A = batch.get(30);
        Patient dup3B = new Patient();
        dup3B.setMrn("MRN-2026-000503");
        dup3B.setNic("198512345678");
        dup3B.setFirstName("Mahesh");
        dup3B.setLastName(dup3A.getLastName());
        dup3B.setDateOfBirth(dup3A.getDateOfBirth());
        dup3B.setSex(dup3A.getSex());
        dup3B.setPhone(dup3A.getPhone());
        dup3B.setDistrict(dup3A.getDistrict());
        dup3B.setActive(true);
        batch.add(dup3B);

        // Pair 4: Same DOB and family name
        Patient dup4A = batch.get(40);
        Patient dup4B = new Patient();
        dup4B.setMrn("MRN-2026-000504");
        dup4B.setNic(null);
        dup4B.setFirstName("Ajith");
        dup4B.setLastName(dup4A.getLastName());
        dup4B.setDateOfBirth(dup4A.getDateOfBirth());
        dup4B.setSex(dup4A.getSex());
        dup4B.setPhone("+94779998822");
        dup4B.setDistrict(dup4A.getDistrict());
        dup4B.setActive(true);
        batch.add(dup4B);

        // Pair 5: Same NIC formatted differently
        Patient dup5A = batch.get(50);
        Patient dup5B = new Patient();
        dup5B.setMrn("MRN-2026-000505");
        dup5B.setNic(dup5A.getNic().toLowerCase());
        dup5B.setFirstName(dup5A.getFirstName());
        dup5B.setLastName(dup5A.getLastName());
        dup5B.setDateOfBirth(dup5A.getDateOfBirth());
        dup5B.setSex(dup5A.getSex());
        dup5B.setPhone("+94779998833");
        dup5B.setDistrict(dup5A.getDistrict());
        dup5B.setActive(true);
        batch.add(dup5B);

        patients.saveAll(batch);

        // Seed allergies on a subset of patients (~25%)
        List<PatientAllergy> allergyList = new ArrayList<>();
        for (int i = 0; i < batch.size(); i += 4) {
            Patient p = batch.get(i);
            String[] a = allergyPool[rnd.nextInt(allergyPool.length)];
            allergyList.add(new PatientAllergy(p, a[0], a[1], AllergySeverity.valueOf(a[2]), "Documented during triage"));
        }
        allergies.saveAll(allergyList);
    }

    private User seedUser(String username, String email, String first, String last, String phone, RoleCode code) {
        return users.findByUsernameIgnoreCase(username).orElseGet(() -> {
            User user = new User();
            user.setUsername(username);
            user.setEmail(email);
            user.setFirstName(first);
            user.setLastName(last);
            user.setPhone(phone);
            user.setRole(roles.findWithPermissionsByCode(code).orElseThrow());
            user.setPasswordHash(passwordEncoder.encode("Welcome@12345"));
            user.setForcePasswordChange(false);
            user.setActive(true);
            return users.save(user);
        });
    }

    private Department seedDept(String code, String name, String desc, String phone, String location) {
        return departments.findByCodeIgnoreCase(code).orElseGet(() ->
                departments.save(new Department(code, name, desc, phone, location))
        );
    }

    private void seedDoc(User user, Department dept, String spec, String qual, String slmc, double fee) {
        if (!doctors.existsByUserId(user.getId())) {
            Doctor doc = new Doctor();
            doc.setUser(user);
            doc.setDepartment(dept);
            doc.setSpecialization(spec);
            doc.setQualification(qual);
            doc.setSlmcRegNumber(slmc);
            doc.setConsultationFee(BigDecimal.valueOf(fee));
            doc.setActive(true);
            doctors.save(doc);
        }
    }

    private void seedEmp(User user, Department dept, String empNum, String desig, String phone) {
        if (!employees.existsByEmployeeNumberIgnoreCase(empNum)) {
            Employee emp = new Employee();
            emp.setUser(user);
            emp.setDepartment(dept);
            emp.setEmployeeNumber(empNum);
            emp.setDesignation(desig);
            emp.setContactPhone(phone);
            emp.setActive(true);
            employees.save(emp);
        }
    }

    private Map<RoleCode, EnumSet<PermissionCode>> matrix() {
        Map<RoleCode, EnumSet<PermissionCode>> m = new EnumMap<>(RoleCode.class);
        m.put(RoleCode.ADMIN, EnumSet.allOf(PermissionCode.class));
        m.put(RoleCode.DOCTOR, EnumSet.of(PermissionCode.PATIENT_READ, PermissionCode.PATIENT_WRITE, PermissionCode.APPOINTMENT_READ, PermissionCode.APPOINTMENT_WRITE, PermissionCode.EMR_READ, PermissionCode.EMR_WRITE, PermissionCode.VITALS_WRITE, PermissionCode.LAB_READ, PermissionCode.PHARMACY_READ, PermissionCode.INVOICE_READ, PermissionCode.REPORT_READ, PermissionCode.REPORT_EXPORT));
        m.put(RoleCode.NURSE, EnumSet.of(PermissionCode.PATIENT_READ, PermissionCode.APPOINTMENT_READ, PermissionCode.EMR_READ, PermissionCode.VITALS_WRITE, PermissionCode.LAB_READ, PermissionCode.LAB_WRITE, PermissionCode.PHARMACY_READ));
        m.put(RoleCode.RECEPTIONIST, EnumSet.of(PermissionCode.PATIENT_READ, PermissionCode.PATIENT_WRITE, PermissionCode.APPOINTMENT_READ, PermissionCode.APPOINTMENT_WRITE, PermissionCode.INVOICE_READ));
        m.put(RoleCode.LAB_STAFF, EnumSet.of(PermissionCode.PATIENT_READ, PermissionCode.LAB_READ, PermissionCode.LAB_WRITE));
        m.put(RoleCode.PHARMACIST, EnumSet.of(PermissionCode.PATIENT_READ, PermissionCode.PHARMACY_READ, PermissionCode.PHARMACY_WRITE, PermissionCode.INVOICE_READ));
        m.put(RoleCode.ACCOUNTANT, EnumSet.of(PermissionCode.PATIENT_READ, PermissionCode.INVOICE_READ, PermissionCode.INVOICE_WRITE, PermissionCode.INVOICE_VOID, PermissionCode.REPORT_READ, PermissionCode.REPORT_EXPORT));
        return m;
    }

    private String title(RoleCode code) { return code.name().replace('_', ' ').toLowerCase().replaceFirst("^.", code.name().substring(0, 1)); }
    private String description(Enum<?> code) { return code.name().replace('_', ' ').toLowerCase(); }
}
