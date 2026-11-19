package lk.hms.demo;

import lk.hms.common.time.HospitalTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.*;
import java.util.*;

/**
 * Demo data: about a year of synthetic hospital activity so the analytics have real rows to aggregate.
 * Runs once when started with {@code hms.demo.seed=true} (env HMS_DEMO_SEED=true), sized by
 * {@code hms.demo.patients} (default 3000; appointments and admissions scale with it). A marker in
 * system_settings stops a second run, and it refuses any database that already holds patients, so it
 * can never mix invented records into real ones. It writes straight to the tables (the services
 * refuse past dates) but keeps every rule the services keep: numbers come from the sequences table,
 * the stock ledger sums to each batch's on-hand, invoices satisfy their arithmetic checks, a bed
 * holds one patient at a time and a patient has one live admission. Deterministic (fixed seed).
 */
@Component
@ConditionalOnProperty(name = "hms.demo.seed", havingValue = "true")
@Order(Ordered.LOWEST_PRECEDENCE - 1)   // after BootstrapAdminRunner (first admin), before StaffAccountSeeder (needs employees)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    static final String MARKER = "demo.seeded_at";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final HospitalTime time;
    private final int patientTarget;

    public DemoDataSeeder(JdbcTemplate jdbc, TransactionTemplate tx, HospitalTime time,
                          @Value("${hms.demo.patients:3000}") int patientTarget) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.time = time;
        this.patientTarget = Math.max(100, Math.min(patientTarget, 20000));
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer done = jdbc.queryForObject("SELECT COUNT(*) FROM system_settings WHERE setting_key = ?", Integer.class, MARKER);
        if (done != null && done > 0) {
            log.info("Demo data already seeded; nothing to do.");
            return;
        }
        Integer users = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        if (users == null || users == 0) {
            log.warn("Demo data NOT seeded: no user exists yet. Set HMS_BOOTSTRAP_ADMIN_USERNAME / _PASSWORD (or sign in once) and restart.");
            return;
        }
        Integer existingPatients = jdbc.queryForObject("SELECT COUNT(*) FROM patients", Integer.class);
        if (existingPatients != null && existingPatients > 0) {
            log.warn("Demo data NOT seeded: this database already has {} patient(s). The seeder only runs on an empty hospital.", existingPatients);
            return;
        }
        long t0 = System.currentTimeMillis();
        Map<String, Integer> counts = tx.execute(s -> new Run().seed());
        log.info("Demo data seeded in {} s: {}", (System.currentTimeMillis() - t0) / 1000, counts);
    }

    // =====================================================================================

    private record Pt(long id, String gender, LocalDate dob, LocalDate reg) {
        int age(LocalDate on) {
            return Period.between(dob, on).getYears();
        }
    }

    private record Doc(long id, long deptId, String dept, String name, BigDecimal fee, Set<DayOfWeek> days) {
    }

    private record Med(long id, String code, String name, BigDecimal price, String freq, int perDay, int days,
                       String dose, String route, int reorder) {
    }

    private static final class Batch {
        long id;
        long medId;
        LocalDate expiry;
        int onHand;
        String no;
    }

    private static final class BedS {
        long id;
        String label;
        BigDecimal rate;
        LocalDateTime freeAt = LocalDateTime.MIN;   // hospital-local time
        boolean live;
    }

    private record WardS(String code, long id, long deptId, String gender, String type, List<BedS> beds) {
    }

    private record Charge(String category, String description, String sourceType, long sourceId, Long deptId,
                          BigDecimal qty, BigDecimal price, LocalDateTime at) {
        BigDecimal amount() {
            return qty.multiply(price).setScale(2, RoundingMode.HALF_UP);
        }
    }

    private record Dx(String code, String description, String complaint) {
    }

    private record Test(long id, String code, BigDecimal price, boolean numeric, BigDecimal low, BigDecimal high,
                        String unit, String refText, int turnaround) {
    }

    private static final String[] FIRST_M = {"Nimal", "Kamal", "Sunil", "Ruwan", "Chaminda", "Asanka", "Dinesh", "Tharindu", "Kasun",
            "Lahiru", "Mohamed", "Rizwan", "Suresh", "Kumar", "Arjun", "Pradeep", "Nuwan", "Sampath", "Mahesh", "Janaka", "Ishara",
            "Dilan", "Harsha", "Gayan", "Roshan", "Thilina"};
    private static final String[] FIRST_F = {"Nimali", "Kumari", "Sanduni", "Dilini", "Chathuri", "Ishani", "Tharushi", "Fathima",
            "Priya", "Shalini", "Anusha", "Malsha", "Hiruni", "Nadeesha", "Sachini", "Ruwani", "Gayathri", "Thilini", "Madushani",
            "Kavindi", "Sewwandi", "Nethmi"};
    private static final String[] LAST = {"Perera", "Fernando", "Silva", "Jayasinghe", "Bandara", "Wickramasinghe", "Rajapaksa",
            "Gunawardena", "Dissanayake", "Herath", "Ratnayake", "Karunaratne", "Senanayake", "Weerasinghe", "Abeysekera", "Mendis",
            "Rathnayake", "Kumara", "Sivakumar", "Rahman", "Nadarajah", "Hettiarachchi", "Wijesinghe", "Ekanayake", "Samarasinghe"};
    private static final String[] DISTRICTS = {"Colombo", "Colombo", "Colombo", "Colombo", "Gampaha", "Gampaha", "Gampaha", "Kalutara",
            "Kalutara", "Kandy", "Kurunegala", "Galle", "Ratnapura", "Kegalle", "Matara", "Puttalam", "Anuradhapura"};

    private static final Map<String, List<Dx>> DX = Map.of(
            "GMED", List.of(new Dx("J06.9", "Acute upper respiratory infection", "Cough and sore throat"),
                    new Dx("A90", "Dengue fever", "High fever and body aches"),
                    new Dx("I10", "Essential hypertension", "Hypertension review"),
                    new Dx("E11.9", "Type 2 diabetes mellitus", "Diabetes review"),
                    new Dx("K29.7", "Gastritis", "Epigastric pain"),
                    new Dx("J45.9", "Asthma", "Wheezing and shortness of breath"),
                    new Dx("R50.9", "Fever, unspecified", "Fever for two days"),
                    new Dx("N39.0", "Urinary tract infection", "Burning on urination")),
            "PAED", List.of(new Dx("J06.9", "Acute upper respiratory infection", "Cough and runny nose"),
                    new Dx("A09", "Gastroenteritis", "Vomiting and loose stools"),
                    new Dx("J21.9", "Acute bronchiolitis", "Noisy breathing"),
                    new Dx("H66.9", "Otitis media", "Ear pain"),
                    new Dx("R50.9", "Fever, unspecified", "Fever")),
            "OBG", List.of(new Dx("Z34.9", "Supervision of normal pregnancy", "Antenatal visit"),
                    new Dx("N92.0", "Menorrhagia", "Heavy periods"),
                    new Dx("O21.0", "Hyperemesis gravidarum", "Persistent vomiting in pregnancy"),
                    new Dx("N76.0", "Acute vaginitis", "Vaginal discharge")),
            "SURG", List.of(new Dx("K35.8", "Acute appendicitis", "Right lower abdominal pain"),
                    new Dx("K40.9", "Inguinal hernia", "Groin swelling"),
                    new Dx("L02.9", "Cutaneous abscess", "Painful swelling"),
                    new Dx("K80.2", "Cholelithiasis", "Upper abdominal pain after meals")),
            "CARD", List.of(new Dx("I10", "Essential hypertension", "High blood pressure"),
                    new Dx("I20.9", "Angina pectoris", "Chest pain on exertion"),
                    new Dx("I48.9", "Atrial fibrillation", "Palpitations"),
                    new Dx("I50.9", "Heart failure", "Breathlessness and ankle swelling")),
            "ORTH", List.of(new Dx("M54.5", "Low back pain", "Back pain"),
                    new Dx("S52.5", "Fracture of distal radius", "Wrist injury after a fall"),
                    new Dx("M17.9", "Osteoarthritis of knee", "Knee pain"),
                    new Dx("S93.4", "Sprain of ankle", "Ankle injury")));

    private static final Map<String, List<String>> LAB_BY_DEPT = Map.of(
            "GMED", List.of("HB", "WBC", "PLT", "FBS", "CRP", "UFR", "CREAT"),
            "PAED", List.of("HB", "WBC", "PLT", "CRP", "UFR"),
            "OBG", List.of("HB", "UFR", "FBS", "TSH"),
            "SURG", List.of("HB", "WBC", "CRP", "CREAT"),
            "CARD", List.of("CHOL", "FBS", "CREAT", "HBA1C"),
            "ORTH", List.of("CRP", "HB"));
    private static final Map<String, Double> LAB_CHANCE = Map.of("GMED", .35, "PAED", .25, "OBG", .3, "SURG", .35, "CARD", .45, "ORTH", .15);
    private static final Map<String, List<String>> RX_BY_DEPT = Map.of(
            "GMED", List.of("PARA500", "AMOX500", "OMEP20", "CETI10", "MET500", "AMLO5", "LOS50", "VITB", "AZI500", "SALB-INH", "CIPRO500"),
            "PAED", List.of("PARA500", "AMOX500", "CHLOR4", "ORS", "CETI10", "VITB"),
            "OBG", List.of("FOLIC5", "IRON200", "PARA500", "DOMP10", "VITB"),
            "SURG", List.of("AMOXCL625", "DICLO50", "PARA500", "OMEP20", "CIPRO500"),
            "CARD", List.of("ASP75", "ATOR20", "AMLO5", "LOS50", "MET500", "GLIC80"),
            "ORTH", List.of("DICLO50", "PARA500", "OMEP20", "PRED5"));

    // =====================================================================================

    private final class Run {
        final Random rnd = new Random(20261118L);
        // activity scales with the number of patients (3000 = the full year this seeder was tuned for)
        final double scale = patientTarget / 3000.0;
        final ZoneId zone = time.zone();
        final LocalDate today = time.today();
        final LocalDate start = today.minusDays(365);
        final LocalDateTime nowLocal = LocalDateTime.ofInstant(time.now(), zone);
        final long admin = jdbc.queryForObject("SELECT MIN(id) FROM users", Long.class);
        final BigDecimal taxRate = jdbc.query("SELECT setting_value FROM system_settings WHERE setting_key = 'billing.tax_rate_percent'",
                rs -> rs.next() ? new BigDecimal(rs.getString(1)) : BigDecimal.ZERO);

        final Map<String, Long> seq = new HashMap<>();
        final Map<String, Long> dept = new HashMap<>();
        final List<Pt> patients = new ArrayList<>();
        final List<Doc> doctors = new ArrayList<>();
        final Map<String, Med> meds = new LinkedHashMap<>();
        final Map<Long, List<Batch>> batches = new HashMap<>();
        final List<Batch> allBatches = new ArrayList<>();
        final List<Long> suppliers = new ArrayList<>();
        final Map<String, Test> tests = new HashMap<>();
        final List<WardS> wards = new ArrayList<>();
        final Map<Long, LocalDateTime> inpatientUntil = new HashMap<>();
        final Map<String, Integer> counts = new TreeMap<>();
        final List<Long> seededEmployees = new ArrayList<>();
        final List<Object[]> diagnoses = new ArrayList<>();
        final List<Object[]> invoiceItems = new ArrayList<>();
        final List<Object[]> stockTx = new ArrayList<>();
        final List<Object[]> labResults = new ArrayList<>();
        int nicSerial = 61000;
        int batchSerial = 1;

        Map<String, Integer> seed() {
            reference();
            doctors();
            patients();
            medicines();
            wards();
            for (LocalDate d = start; !d.isAfter(today.plusDays(13)); d = d.plusDays(1)) {
                outpatientDay(d);
                if (d.isBefore(today)) {
                    inpatientDay(d);
                }
            }
            waitingAdmissions();
            slowMovingStock();
            staff();
            flush();
            jdbc.update("INSERT INTO system_settings (setting_key, setting_value, value_type, description) VALUES (?, ?, 'STRING', ?)",
                    MARKER, time.now().toString(), "Set by the dev demo-data seeder; delete to allow another run on an empty database");
            return counts;
        }

        // ------------------------------------------------------------------ helpers

        int between(int lo, int hi) {
            return lo + rnd.nextInt(hi - lo + 1);
        }

        boolean chance(double p) {
            return rnd.nextDouble() < p;
        }

        <T> T pick(List<T> list) {
            return list.get(rnd.nextInt(list.size()));
        }

        String pick(String[] a) {
            return a[rnd.nextInt(a.length)];
        }

        LocalDateTime utc(LocalDateTime local) {
            return local.atZone(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        }

        void count(String what) {
            counts.merge(what, 1, Integer::sum);
        }

        long insert(String sql, Object... args) {
            KeyHolder kh = new GeneratedKeyHolder();
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                for (int i = 0; i < args.length; i++) {
                    ps.setObject(i + 1, args[i]);
                }
                return ps;
            }, kh);
            return Objects.requireNonNull(kh.getKey()).longValue();
        }

        long nextSeq(String key) {
            long cur = seq.computeIfAbsent(key, k -> jdbc.query("SELECT next_value FROM sequences WHERE seq_key = ?",
                    rs -> rs.next() ? rs.getLong(1) : 1L, k));
            seq.put(key, cur + 1);
            return cur;
        }

        String number(String prefix, int year) {
            return String.format("%s-%d-%06d", prefix, year, nextSeq(prefix + "-" + year));
        }

        BigDecimal money(double v) {
            return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
        }

        String nic(LocalDate dob, String gender) {
            int day = dob.getDayOfYear() + ("FEMALE".equals(gender) ? 500 : 0);
            return String.format("%04d%03d%05d", dob.getYear(), day, nicSerial++);
        }

        String phone() {
            return "07" + between(0, 8) + String.format("%07d", rnd.nextInt(10_000_000));
        }

        // ------------------------------------------------------------------ reference data

        void reference() {
            String[][] depts = {{"GMED", "General Medicine", "CLINICAL"}, {"PAED", "Paediatrics", "CLINICAL"},
                    {"OBG", "Obstetrics and Gynaecology", "CLINICAL"}, {"SURG", "General Surgery", "CLINICAL"},
                    {"CARD", "Cardiology", "CLINICAL"}, {"ORTH", "Orthopaedics", "CLINICAL"}, {"LABS", "Laboratory", "DIAGNOSTIC"},
                    {"PHAR", "Pharmacy", "SUPPORT"}, {"NURS", "Nursing Services", "SUPPORT"}, {"FIN", "Finance", "ADMINISTRATIVE"},
                    {"ADMN", "Administration", "ADMINISTRATIVE"}};
            for (String[] d : depts) {
                Long id = jdbc.query("SELECT id FROM departments WHERE code = ? OR name = ? ORDER BY code = ? DESC LIMIT 1",
                        rs -> rs.next() ? rs.getLong(1) : null, d[0], d[1], d[0]);
                if (id == null) {
                    id = insert("INSERT INTO departments (code, name, type, location) VALUES (?, ?, ?, ?)", d[0], d[1], d[2], "Main building");
                    count("departments");
                }
                dept.put(d[0], id);
            }
            jdbc.query("SELECT id, code, price, result_type, ref_range_low, ref_range_high, unit, ref_range_text, turnaround_hours FROM lab_tests WHERE active",
                    rs -> {
                        tests.put(rs.getString(2), new Test(rs.getLong(1), rs.getString(2), rs.getBigDecimal(3), "NUMERIC".equals(rs.getString(4)),
                                rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getString(7), rs.getString(8), rs.getInt(9)));
                    });
        }

        long employee(String category, String deptCode, String designation, String gender, LocalDate join, String status, LocalDate leaveDate) {
            String first = "FEMALE".equals(gender) ? pick(FIRST_F) : pick(FIRST_M);
            LocalDate dob = join.minusYears(between(23, 45)).minusDays(between(0, 364));
            long id = insert("""
                    INSERT INTO employees (employee_no, department_id, first_name, last_name, nic, gender, date_of_birth, phone, designation,
                                           staff_category, employment_type, join_date, leave_date, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    String.format("EMP-%06d", nextSeq("EMP")), dept.get(deptCode), first, pick(LAST), nic(dob, gender), gender, dob, phone(),
                    designation, category, chance(.85) ? "PERMANENT" : "CONTRACT", join, leaveDate, status);
            count("employees");
            seededEmployees.add(id);
            return id;
        }

        void doctors() {
            Object[][] plan = {{"GMED", 3, "General Physician"}, {"PAED", 2, "Paediatrician"}, {"OBG", 2, "Obstetrician and Gynaecologist"},
                    {"SURG", 2, "General Surgeon"}, {"CARD", 2, "Cardiologist"}, {"ORTH", 1, "Orthopaedic Surgeon"}};
            DayOfWeek[] week = {DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY};
            int n = 0;
            for (Object[] p : plan) {
                for (int i = 0; i < (int) p[1]; i++, n++) {
                    String gender = chance(.4) ? "FEMALE" : "MALE";
                    long emp = employee("DOCTOR", (String) p[0], "Consultant " + p[2], gender, start.minusYears(between(1, 9)), "ACTIVE", null);
                    BigDecimal fee = money(between(20, 35) * 100);
                    long id = insert("""
                            INSERT INTO doctors (employee_id, registration_no, specialization, qualifications, consultation_fee, accepting_patients)
                            VALUES (?, ?, ?, ?, ?, TRUE)""", emp, "SLMC-D" + (24000 + n), p[2], "MBBS, MD", fee);
                    String name = jdbc.queryForObject("SELECT CONCAT('Dr. ', first_name, ' ', last_name) FROM employees WHERE id = ?", String.class, emp);
                    Set<DayOfWeek> days = EnumSet.of(week[n % 5], week[(n + 2) % 5], week[(n + 4) % 5]);
                    for (DayOfWeek d : days) {
                        jdbc.update("""
                                INSERT INTO doctor_schedules (doctor_id, day_of_week, start_time, end_time, slot_minutes, max_patients, room_label, effective_from)
                                VALUES (?, ?, '08:00', '12:00', 15, 16, ?, ?)""", id, d.getValue(), "OPD room " + (n + 1), start.minusMonths(1));
                    }
                    doctors.add(new Doc(id, dept.get((String) p[0]), (String) p[0], name, fee, days));
                    count("doctors");
                }
            }
        }

        void patients() {
            int oldPatients = (int) Math.round(patientTarget * 1700 / 3000.0);
            for (int i = 0; i < patientTarget; i++) {
                LocalDate reg = i < oldPatients
                        ? start.minusDays(between(1, 4 * 365))
                        // within the year, slowly growing: sqrt skews registrations towards recent months
                        : start.plusDays((long) (365 * Math.sqrt(rnd.nextDouble())));
                if (reg.isAfter(today.minusDays(1))) {
                    reg = today.minusDays(1);
                }
                String gender = chance(.53) ? "FEMALE" : "MALE";
                int age = chance(.18) ? between(0, 13) : chance(.2) ? between(60, 88) : between(14, 59);
                LocalDate dob = reg.minusYears(age).minusDays(between(0, 364));
                LocalDateTime created = utc(reg.atTime(between(7, 17), between(0, 59)));
                String mrn = number("HMS", reg.getYear());
                long id = insert("""
                        INSERT INTO patients (mrn, first_name, last_name, nic, gender, date_of_birth, blood_group, marital_status, phone,
                                              city, district, status, registered_by, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)""",
                        mrn, "FEMALE".equals(gender) ? pick(FIRST_F) : pick(FIRST_M), pick(LAST), age >= 16 ? nic(dob, gender) : null, gender, dob,
                        chance(.7) ? pick(List.of("O+", "O+", "B+", "A+", "AB+", "O-", "A-", "B-")) : null,
                        age >= 18 ? (chance(.65) ? "MARRIED" : "SINGLE") : null, phone(), null, pick(DISTRICTS), admin, created, created);
                patients.add(new Pt(id, gender, dob, reg));
                count("patients");
            }
            patients.sort(Comparator.comparing(Pt::reg));
        }

        Pt patientOn(LocalDate day) {
            int hi = 0;
            while (hi < patients.size() && !patients.get(hi).reg().isAfter(day)) {
                hi++;
            }
            return patients.get(rnd.nextInt(Math.max(1, hi)));
        }

        // ------------------------------------------------------------------ pharmacy

        void medicines() {
            for (String s : new String[]{"State Pharmaceuticals Corporation", "Hemas Pharmaceuticals", "Emerchemie NB"}) {
                Long id = jdbc.query("SELECT id FROM suppliers WHERE name = ?", rs -> rs.next() ? rs.getLong(1) : null, s);
                suppliers.add(id != null ? id : insert("INSERT INTO suppliers (name, phone) VALUES (?, ?)", s, "0112" + between(100000, 999999)));
            }
            Object[][] list = {
                    // code, generic, strength, form, unit, price, category, freq, perDay, days, dose, route, reorder
                    {"PARA500", "Paracetamol", "500 mg", "TABLET", "tablet", 3.50, "Analgesics", "QDS", 4, 3, "1 tablet", "ORAL", 500},
                    {"DICLO50", "Diclofenac sodium", "50 mg", "TABLET", "tablet", 3.80, "Analgesics", "BD", 2, 5, "1 tablet", "ORAL", 200},
                    {"AMOX500", "Amoxicillin", "500 mg", "CAPSULE", "capsule", 12.00, "Antibiotics", "TDS", 3, 5, "1 capsule", "ORAL", 300},
                    {"AMOXCL625", "Amoxicillin and clavulanic acid", "625 mg", "TABLET", "tablet", 45.00, "Antibiotics", "BD", 2, 5, "1 tablet", "ORAL", 150},
                    {"AZI500", "Azithromycin", "500 mg", "TABLET", "tablet", 38.00, "Antibiotics", "OD", 1, 3, "1 tablet", "ORAL", 100},
                    {"CIPRO500", "Ciprofloxacin", "500 mg", "TABLET", "tablet", 15.00, "Antibiotics", "BD", 2, 5, "1 tablet", "ORAL", 150},
                    {"MET500", "Metformin", "500 mg", "TABLET", "tablet", 4.25, "Antidiabetics", "BD", 2, 30, "1 tablet", "ORAL", 400},
                    {"GLIC80", "Gliclazide", "80 mg", "TABLET", "tablet", 6.00, "Antidiabetics", "BD", 2, 30, "1 tablet", "ORAL", 200},
                    {"AMLO5", "Amlodipine", "5 mg", "TABLET", "tablet", 5.50, "Cardiovascular", "OD", 1, 30, "1 tablet", "ORAL", 300},
                    {"LOS50", "Losartan", "50 mg", "TABLET", "tablet", 9.00, "Cardiovascular", "OD", 1, 30, "1 tablet", "ORAL", 300},
                    {"ATOR20", "Atorvastatin", "20 mg", "TABLET", "tablet", 14.00, "Cardiovascular", "OD", 1, 30, "1 tablet", "ORAL", 300},
                    {"ASP75", "Aspirin", "75 mg", "TABLET", "tablet", 2.00, "Cardiovascular", "OD", 1, 30, "1 tablet", "ORAL", 300},
                    {"OMEP20", "Omeprazole", "20 mg", "CAPSULE", "capsule", 7.50, "Gastrointestinal", "BD", 2, 14, "1 capsule", "ORAL", 300},
                    {"DOMP10", "Domperidone", "10 mg", "TABLET", "tablet", 4.00, "Gastrointestinal", "TDS", 3, 5, "1 tablet", "ORAL", 200},
                    {"ORS", "Oral rehydration salts", "1 sachet", "POWDER", "sachet", 35.00, "Gastrointestinal", "TDS", 3, 3, "1 sachet", "ORAL", 100},
                    {"SALB-INH", "Salbutamol", "100 mcg/dose", "INHALER", "inhaler", 850.00, "Respiratory", "PRN", 0, 30, "2 puffs", "INHALED", 30},
                    {"CETI10", "Cetirizine", "10 mg", "TABLET", "tablet", 3.00, "Antihistamines", "OD", 1, 5, "1 tablet", "ORAL", 200},
                    {"CHLOR4", "Chlorphenamine", "4 mg", "TABLET", "tablet", 1.50, "Antihistamines", "TDS", 3, 5, "1 tablet", "ORAL", 200},
                    {"PRED5", "Prednisolone", "5 mg", "TABLET", "tablet", 2.80, "Antihistamines", "TDS", 3, 5, "1 tablet", "ORAL", 150},
                    {"VITB", "Vitamin B complex", "-", "TABLET", "tablet", 2.50, "Vitamins and supplements", "OD", 1, 30, "1 tablet", "ORAL", 300},
                    {"FOLIC5", "Folic acid", "5 mg", "TABLET", "tablet", 1.20, "Vitamins and supplements", "OD", 1, 30, "1 tablet", "ORAL", 200},
                    {"IRON200", "Ferrous sulphate", "200 mg", "TABLET", "tablet", 2.20, "Vitamins and supplements", "OD", 1, 30, "1 tablet", "ORAL", 300},
                    {"ADR1", "Adrenaline", "1 mg/mL", "INJECTION", "ampoule", 180.00, "Emergency", "STAT", 0, 1, "0.5 mL", "IM", 20},
                    {"HYDRO100", "Hydrocortisone", "100 mg", "INJECTION", "vial", 420.00, "Emergency", "STAT", 0, 1, "100 mg", "IV", 20},
                    {"ATRO06", "Atropine", "0.6 mg/mL", "INJECTION", "ampoule", 95.00, "Emergency", "STAT", 0, 1, "0.6 mg", "IV", 10}};
            for (Object[] m : list) {
                String cat = (String) m[6];
                Long catId = jdbc.query("SELECT id FROM medicine_categories WHERE name = ?", rs -> rs.next() ? rs.getLong(1) : null, cat);
                if (catId == null) {
                    catId = insert("INSERT INTO medicine_categories (name) VALUES (?)", cat);
                }
                Long id = jdbc.query("SELECT id FROM medicines WHERE code = ?", rs -> rs.next() ? rs.getLong(1) : null, m[0]);
                if (id == null) {
                    id = insert("""
                            INSERT INTO medicines (code, generic_name, category_id, dosage_form, strength, unit, unit_price, reorder_level, prescription_required)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""", m[0], m[1], catId, m[3], m[2], m[4], money((double) m[5]), m[12], !"Analgesics".equals(cat));
                    count("medicines");
                }
                BigDecimal price = jdbc.queryForObject("SELECT unit_price FROM medicines WHERE id = ?", BigDecimal.class, id);
                meds.put((String) m[0], new Med(id, (String) m[0], m[1] + " " + m[2], price, (String) m[7], (int) m[8], (int) m[9],
                        (String) m[10], (String) m[11], (int) m[12]));
            }
        }

        Batch receive(Med m, LocalDate day, int qty, LocalDate expiry) {
            Batch b = new Batch();
            b.medId = m.id();
            b.no = "D" + day.getYear() % 100 + "-" + String.format("%04d", batchSerial++);
            b.expiry = expiry;
            b.onHand = qty;
            b.id = insert("""
                    INSERT INTO medicine_batches (medicine_id, supplier_id, batch_no, manufacture_date, expiry_date, received_date,
                                                  quantity_received, quantity_on_hand, cost_price, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", m.id(), pick(suppliers), b.no, day.minusDays(between(60, 200)), expiry, day,
                    qty, qty, m.price().multiply(new BigDecimal("0.6")).setScale(2, RoundingMode.HALF_UP), utc(day.atTime(8, 0)));
            stockTx.add(new Object[]{b.id, m.id(), "RECEIPT", qty, qty, "GRN", b.id, null, admin, utc(day.atTime(8, 0))});
            batches.computeIfAbsent(m.id(), k -> new ArrayList<>()).add(b);
            allBatches.add(b);
            count("stock receipts");
            return b;
        }

        /** FEFO over this seeder's own batches, receiving stock when short (the store reorders on demand). */
        List<int[]> take(Med m, int qty, LocalDate day) {
            List<Batch> list = batches.computeIfAbsent(m.id(), k -> new ArrayList<>());
            int usable = list.stream().filter(b -> b.expiry.isAfter(day)).mapToInt(b -> b.onHand).sum();
            if (usable < qty) {
                receive(m, day, Math.max(qty, m.reorder() * 3 + between(0, m.reorder() * 2)), day.plusDays(between(540, 900)));
            }
            List<int[]> taken = new ArrayList<>();   // [batchIndex in allBatches, qty]
            int need = qty;
            for (Batch b : list.stream().filter(x -> x.expiry.isAfter(day) && x.onHand > 0).sorted(Comparator.comparing(x -> x.expiry)).toList()) {
                int t = Math.min(need, b.onHand);
                b.onHand -= t;
                taken.add(new int[]{allBatches.indexOf(b), t, b.onHand});
                need -= t;
                if (need == 0) {
                    break;
                }
            }
            return taken;
        }

        /** Emergency drugs nobody prescribes in clinic: one nearly expiring, one expired, one fine. */
        void slowMovingStock() {
            receive(meds.get("ADR1"), today.minusMonths(17), 40, today.plusDays(45));
            receive(meds.get("ATRO06"), today.minusMonths(20), 15, today.minusDays(10));
            receive(meds.get("HYDRO100"), today.minusMonths(4), 60, today.plusDays(400));
        }

        // ------------------------------------------------------------------ outpatients

        void outpatientDay(LocalDate d) {
            boolean past = d.isBefore(today);
            // a gentle seasonal wave (monsoon peaks) on top of slow growth
            double season = 1 + .15 * Math.sin((d.getDayOfYear() / 365.0) * 2 * Math.PI * 2);
            double growth = .85 + .3 * (d.toEpochDay() - start.toEpochDay()) / 365.0;
            for (Doc doc : doctors) {
                if (!doc.days().contains(d.getDayOfWeek())) {
                    continue;
                }
                int booked = past ? Math.max(1, (int) Math.round(between(7, 14) * season * growth * scale))
                        : Math.max(0, (int) Math.round((13 - (d.toEpochDay() - today.toEpochDay()) + between(-2, 1)) * scale));
                booked = Math.min(16, booked);
                List<Integer> slots = new ArrayList<>();
                for (int i = 0; i < 16; i++) {
                    slots.add(i);
                }
                Collections.shuffle(slots, rnd);
                for (int s : slots.subList(0, booked)) {
                    appointment(doc, d, s, past);
                }
            }
        }

        void appointment(Doc doc, LocalDate d, int slot, boolean past) {
            Pt p = patientOn(d);
            LocalDateTime slotLocal = d.atTime(8, 0).plusMinutes(15L * slot);
            LocalDateTime booked = slotLocal.minusDays(between(0, 12)).minusHours(between(1, 6));
            String type = past && chance(.1) ? "WALK_IN" : chance(.22) ? "FOLLOW_UP" : "CONSULTATION";
            if ("WALK_IN".equals(type)) {
                booked = slotLocal.minusMinutes(between(10, 60));
            }
            if (booked.isAfter(nowLocal)) {
                booked = nowLocal.minusMinutes(between(5, 600));
            }
            String status;
            String cancelReason = null;
            LocalDateTime confirmed = chance(.8) ? booked.plusMinutes(between(5, 300)) : null;
            LocalDateTime checkedIn = null, started = null, completed = null, cancelled = null;
            if (past) {
                double r = rnd.nextDouble();
                if (r < .09) {
                    status = "CANCELLED";
                    cancelReason = pick(List.of("Patient request", "Patient request", "Feeling better", "Doctor unavailable", "Clash with work", "Travel"));
                    cancelled = slotLocal.minusHours(between(1, 48));
                    if (cancelled.isBefore(booked)) {
                        cancelled = booked.plusMinutes(30);
                    }
                } else if (r < .16) {
                    status = "NO_SHOW";
                } else {
                    status = "COMPLETED";
                    checkedIn = slotLocal.minusMinutes(between(0, 25));
                    started = slotLocal.plusMinutes(between(0, 30) + (slot > 10 ? between(0, 20) : 0));
                    completed = started.plusMinutes(between(7, 24));
                }
            } else {
                status = confirmed != null ? "CONFIRMED" : "PENDING";
            }
            if (confirmed != null && (confirmed.isAfter(slotLocal) || (!past && confirmed.isAfter(nowLocal)))) {
                confirmed = booked.plusMinutes(5);
            }
            long id = insert("""
                    INSERT INTO appointments (appointment_no, patient_id, doctor_id, department_id, slot_start, slot_end, token_no, type, status,
                                              reason, cancel_reason, booked_by, confirmed_at, checked_in_at, started_at, completed_at, cancelled_at,
                                              created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    number("APT", d.getYear()), p.id(), doc.id(), doc.deptId(), utc(slotLocal), utc(slotLocal.plusMinutes(15)), slot + 1,
                    type, status, null, cancelReason, admin, n(confirmed), n(checkedIn), n(started), n(completed), n(cancelled), utc(booked), utc(booked));
            count("appointments");
            if ("COMPLETED".equals(status)) {
                visit(id, doc, p, started, completed);
            }
        }

        LocalDateTime n(LocalDateTime local) {
            return local == null ? null : utc(local);
        }

        void visit(long appointmentId, Doc doc, Pt p, LocalDateTime started, LocalDateTime completed) {
            LocalDate d = started.toLocalDate();
            Dx dx = pick(DX.get(doc.dept()));
            long enc = insert("""
                    INSERT INTO encounters (encounter_no, patient_id, doctor_id, department_id, type, appointment_id, chief_complaint, status,
                                            started_at, closed_at, created_at)
                    VALUES (?, ?, ?, ?, 'OPD', ?, ?, 'CLOSED', ?, ?, ?)""",
                    number("ENC", d.getYear()), p.id(), doc.id(), doc.deptId(), appointmentId, dx.complaint(), utc(started), utc(completed), utc(started));
            count("encounters");
            diagnoses.add(new Object[]{enc, p.id(), dx.code(), dx.description(), "PRIMARY", chance(.7) ? "CONFIRMED" : "PROVISIONAL", doc.id(), utc(completed)});

            List<Charge> charges = new ArrayList<>();
            charges.add(new Charge("CONSULTATION", "Consultation - " + doc.name(), "APPOINTMENT", appointmentId, doc.deptId(),
                    BigDecimal.ONE, doc.fee(), completed));
            if (chance(LAB_CHANCE.get(doc.dept()))) {
                lab(enc, doc, p, started.plusMinutes(between(3, 10)), charges);
            }
            LocalDateTime visitEnd = completed.plusMinutes(between(45, 60));   // after the pharmacy
            if (chance(.62)) {
                prescription(enc, doc, p, completed, charges);
            }
            boolean recent = !d.isBefore(today.minusDays(2));
            bill(p, charges, visitEnd, null, recent && chance(.45));
        }

        void lab(long enc, Doc doc, Pt p, LocalDateTime requested, List<Charge> charges) {
            String priority = chance(.05) ? "STAT" : chance(.15) ? "URGENT" : "ROUTINE";
            long req = insert("""
                    INSERT INTO lab_requests (request_no, patient_id, encounter_id, requested_by, department_id, priority, requested_at, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", number("LAB", requested.getYear()), p.id(), enc, doc.id(), doc.deptId(), priority,
                    utc(requested), utc(requested));
            count("lab requests");
            List<String> codes = new ArrayList<>(LAB_BY_DEPT.get(doc.dept()));
            Collections.shuffle(codes, rnd);
            for (String code : codes.subList(0, Math.min(codes.size(), between(1, 3)))) {
                Test t = tests.get(code);
                if (t == null) {
                    continue;
                }
                // speed depends on priority; about one in eight runs past its target
                double factor = ("ROUTINE".equals(priority) ? .5 : .25) + rnd.nextDouble() * (chance(.12) ? 1.6 : .45);
                LocalDateTime collected = requested.plusMinutes(between(5, "ROUTINE".equals(priority) ? 90 : 20));
                LocalDateTime processing = collected.plusMinutes(between(5, 30));
                LocalDateTime done = collected.plusMinutes(Math.max(20, (long) (t.turnaround() * 60 * factor)));
                LocalDateTime reviewed = done.plusMinutes(between(20, 600));
                String status = "REVIEWED";
                if (done.isAfter(nowLocal)) {
                    status = processing.isAfter(nowLocal) ? (collected.isAfter(nowLocal) ? "REQUESTED" : "SAMPLE_COLLECTED") : "PROCESSING";
                } else if (reviewed.isAfter(nowLocal)) {
                    status = "COMPLETED";
                }
                boolean hasSample = !"REQUESTED".equals(status);
                boolean hasResult = "COMPLETED".equals(status) || "REVIEWED".equals(status);
                long item = insert("""
                        INSERT INTO lab_request_items (lab_request_id, lab_test_id, status, price, sample_barcode, sample_collected_at, sample_collected_by,
                                                       processing_started_at, completed_at, reviewed_at, reviewed_by, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", req, t.id(), status, t.price(),
                        hasSample ? number("SMP", collected.getYear()) : null, hasSample ? utc(collected) : null, hasSample ? admin : null,
                        hasSample && !"SAMPLE_COLLECTED".equals(status) ? utc(processing) : null, hasResult ? utc(done) : null,
                        "REVIEWED".equals(status) ? utc(reviewed) : null, "REVIEWED".equals(status) ? doc.id() : null, utc(requested));
                count("lab tests");
                if (hasResult) {
                    result(item, t, done);
                }
                charges.add(new Charge("LABORATORY", "Lab: " + code, "LAB_REQUEST_ITEM", item, doc.deptId(), BigDecimal.ONE, t.price(), requested));
            }
        }

        void result(long item, Test t, LocalDateTime at) {
            if (!t.numeric()) {
                boolean normal = chance(.8);
                labResults.add(new Object[]{item, null, normal ? t.refText() : "Pus cells 10-15/hpf, organisms seen", null, null, null, null, admin, utc(at)});
                return;
            }
            double low = t.low() == null ? 0 : t.low().doubleValue();
            double high = t.high() == null ? low * 2 + 1 : t.high().doubleValue();
            double v;
            double r = rnd.nextDouble();
            if (r < .72 || low == 0 && r < .8) {
                v = low + (high - low) * rnd.nextDouble();
            } else if (r < .86 && low > 0) {
                v = low * (.65 + .34 * rnd.nextDouble());
            } else {
                v = high * (1.02 + .5 * rnd.nextDouble());
            }
            BigDecimal value = BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP);
            String flag = t.low() != null && value.compareTo(t.low()) < 0 ? "L" : t.high() != null && value.compareTo(t.high()) > 0 ? "H" : "N";
            labResults.add(new Object[]{item, value, null, t.unit(), t.low(), t.high(), flag, admin, utc(at)});
        }

        void prescription(long enc, Doc doc, Pt p, LocalDateTime at, List<Charge> charges) {
            List<String> codes = new ArrayList<>(RX_BY_DEPT.get(doc.dept()));
            Collections.shuffle(codes, rnd);
            List<Med> chosen = codes.subList(0, between(1, 3)).stream().map(meds::get).toList();
            LocalDate d = at.toLocalDate();
            boolean lastDay = d.equals(today.minusDays(1)) && at.getHour() >= 11;
            boolean declined = !lastDay && chance(.05);
            String status = lastDay ? "ACTIVE" : declined ? "CANCELLED" : "DISPENSED";
            long rx = insert("""
                    INSERT INTO prescriptions (prescription_no, encounter_id, patient_id, doctor_id, status, notes, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", number("RX", d.getYear()), enc, p.id(), doc.id(), status,
                    declined ? "Patient chose to buy outside" : null, utc(at), utc(at));
            count("prescriptions");
            Long dsp = null;
            LocalDateTime dispensedAt = at.plusMinutes(between(8, 40));
            if ("DISPENSED".equals(status)) {
                dsp = insert("INSERT INTO dispensations (dispensation_no, prescription_id, patient_id, dispensed_by, dispensed_at) VALUES (?, ?, ?, ?, ?)",
                        number("DSP", d.getYear()), rx, p.id(), admin, utc(dispensedAt));
                count("dispensations");
            }
            for (Med m : chosen) {
                int qty = m.perDay() == 0 ? 1 : m.perDay() * m.days();
                boolean give = dsp != null;
                long item = insert("""
                        INSERT INTO prescription_items (prescription_id, medicine_id, dose, frequency, route, duration_days, quantity_prescribed, quantity_dispensed)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", rx, m.id(), m.dose(), m.freq(), m.route(), m.days(), qty, give ? qty : 0);
                if (!give) {
                    continue;
                }
                for (int[] t : take(m, qty, d)) {
                    Batch b = allBatches.get(t[0]);
                    long di = insert("INSERT INTO dispensation_items (dispensation_id, prescription_item_id, batch_id, quantity, unit_price) VALUES (?, ?, ?, ?, ?)",
                            dsp, item, b.id, t[1], m.price());
                    stockTx.add(new Object[]{b.id, m.id(), "DISPENSE", -t[1], t[2], "DISPENSATION", dsp, null, admin, utc(dispensedAt)});
                    charges.add(new Charge("PHARMACY", m.name() + " (batch " + b.no + ")", "DISPENSATION_ITEM", di, null,
                            BigDecimal.valueOf(t[1]), m.price(), dispensedAt));
                }
            }
        }

        // ------------------------------------------------------------------ billing

        /** One invoice for a visit or a stay; most are paid on the spot, some partly, a few not at all. */
        void bill(Pt p, List<Charge> charges, LocalDateTime at, Long admissionId, boolean leaveUnbilled) {
            if (leaveUnbilled) {
                for (Charge c : charges) {
                    insertCharge(p, c, null);
                }
                return;
            }
            BigDecimal subtotal = charges.stream().map(Charge::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal discount = chance(.04) ? subtotal.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal tax = subtotal.subtract(discount).multiply(taxRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal total = subtotal.subtract(discount).add(tax);
            double r = rnd.nextDouble();
            String status;
            BigDecimal paid;
            if (r < .9) {
                status = "PAID";
                paid = total;
            } else if (r < .95) {
                status = "PARTIALLY_PAID";
                paid = total.multiply(BigDecimal.valueOf(.3 + .4 * rnd.nextDouble())).setScale(-2, RoundingMode.DOWN).setScale(2, RoundingMode.UNNECESSARY).max(new BigDecimal("100.00")).min(total);
                if (paid.compareTo(total) >= 0) {
                    status = "PAID";
                    paid = total;
                }
            } else {
                status = "ISSUED";
                paid = BigDecimal.ZERO;
            }
            if (total.signum() == 0) {
                status = "PAID";
                paid = BigDecimal.ZERO;
            }
            long inv = insert("""
                    INSERT INTO invoices (invoice_no, patient_id, admission_id, status, subtotal, discount_amount, discount_reason, tax_amount,
                                          total_amount, amount_paid, issued_at, created_by, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", number("INV", at.getYear()), p.id(), admissionId, status, subtotal, discount,
                    discount.signum() > 0 ? "Senior citizen concession" : null, tax, total, paid, utc(at), admin, utc(at), utc(at));
            count("invoices");
            for (Charge c : charges) {
                long id = insertCharge(p, c, inv);
                invoiceItems.add(new Object[]{inv, id, c.category(), c.description(), c.qty(), c.price(), c.amount()});
            }
            if (paid.signum() > 0) {
                String method = r < .5 ? "CASH" : pick(List.of("CASH", "CARD", "CARD", "CARD", "BANK_TRANSFER", "CHEQUE"));
                String ref = switch (method) {
                    case "CARD" -> "APPR " + between(100000, 999999);
                    case "BANK_TRANSFER" -> "TRF" + between(10000000, 99999999);
                    case "CHEQUE" -> "CHQ " + between(100000, 999999);
                    default -> null;
                };
                LocalDateTime received = at.plusMinutes(between(0, 8));
                insert("""
                        INSERT INTO payments (receipt_no, invoice_id, amount, method, reference_no, received_by, received_at, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", number("RCP", received.getYear()), inv, paid, method, ref, admin, utc(received), utc(received));
                count("payments");
            }
        }

        long insertCharge(Pt p, Charge c, Long invoiceId) {
            count("charges");
            return insert("""
                    INSERT INTO charge_items (patient_id, category, description, source_type, source_id, department_id, quantity, unit_price, amount,
                                              invoice_id, service_date, created_by, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", p.id(), c.category(), c.description(), c.sourceType(), c.sourceId(), c.deptId(),
                    c.qty(), c.price(), c.amount(), invoiceId, utc(c.at()), admin, utc(c.at()));
        }

        // ------------------------------------------------------------------ inpatients

        void wards() {
            Object[][] plan = {
                    // code, name, dept, type, gender, floor, roomPrefix, rooms, bedsPerRoom, roomType, rate
                    {"GMW", "Medical Ward (Male)", "GMED", "GENERAL", "MALE", "2", "A", 2, 6, "SHARED", 4500},
                    {"GFW", "Medical Ward (Female)", "GMED", "GENERAL", "FEMALE", "2", "B", 2, 6, "SHARED", 4500},
                    {"PDW", "Paediatric Ward", "PAED", "PAEDIATRIC", "MIXED", "3", "P", 1, 6, "SHARED", 3500},
                    {"SGW", "Surgical Ward", "SURG", "SURGICAL", "MIXED", "3", "S", 1, 6, "SHARED", 5000},
                    {"ICU", "Intensive Care Unit", "CARD", "ICU", "MIXED", "1", "ICU", 1, 4, "ICU", 25000},
                    {"PVT", "Private Wing", "GMED", "PRIVATE", "MIXED", "4", "40", 4, 1, "PRIVATE", 12000}};
            for (Object[] w : plan) {
                if (jdbc.queryForObject("SELECT COUNT(*) FROM wards WHERE code = ?", Integer.class, w[0]) > 0) {
                    continue;
                }
                long wardId = insert("INSERT INTO wards (code, name, department_id, ward_type, gender_policy, floor) VALUES (?, ?, ?, ?, ?, ?)",
                        w[0], w[1], dept.get((String) w[2]), w[3], w[4], w[5]);
                List<BedS> beds = new ArrayList<>();
                for (int r = 1; r <= (int) w[7]; r++) {
                    String roomNo = w[6] + String.valueOf(r);
                    long room = insert("INSERT INTO rooms (ward_id, room_no, room_type, daily_rate) VALUES (?, ?, ?, ?)", wardId, roomNo, w[9], money((int) w[10]));
                    for (int b = 0; b < (int) w[8]; b++) {
                        BedS bed = new BedS();
                        bed.label = roomNo + "-" + (char) ('A' + b);
                        bed.rate = money((int) w[10]);
                        bed.id = insert("INSERT INTO beds (room_id, bed_no) VALUES (?, ?)", room, String.valueOf((char) ('A' + b)));
                        beds.add(bed);
                    }
                }
                wards.add(new WardS((String) w[0], wardId, dept.get((String) w[2]), (String) w[4], (String) w[3], beds));
                count("wards");
            }
        }

        void inpatientDay(LocalDate d) {
            if (wards.isEmpty()) {
                return;
            }
            int n = (int) Math.round(between(3, 8) * Math.min(1.0, scale * 1.5));
            for (int i = 0; i < n; i++) {
                double r = rnd.nextDouble();
                WardS ward = wards.get(r < .25 ? 0 : r < .5 ? 1 : r < .63 ? 2 : r < .78 ? 3 : r < .87 ? 4 : 5);
                LocalDateTime admitted = d.atTime(between(7, 22), between(0, 59));
                BedS bed = ward.beds().stream().filter(b -> !b.freeAt.isAfter(admitted)).findFirst().orElse(null);
                if (bed == null) {
                    continue;
                }
                Pt p = null;
                for (int tries = 0; tries < 30 && p == null; tries++) {
                    Pt c = patientOn(d);
                    int age = c.age(d);
                    boolean fits = switch (ward.type()) {
                        case "PAEDIATRIC" -> age < 14;
                        default -> age >= 14 && ("MIXED".equals(ward.gender()) || ward.gender().equals(c.gender()));
                    };
                    if (fits && inpatientUntil.getOrDefault(c.id(), LocalDateTime.MIN).isBefore(admitted.minusDays(1))) {
                        p = c;
                    }
                }
                if (p == null) {
                    continue;
                }
                admit(ward, bed, p, admitted);
            }
        }

        void admit(WardS ward, BedS bed, Pt p, LocalDateTime admitted) {
            String deptCode = switch (ward.code()) {
                case "PDW" -> "PAED";
                case "SGW" -> chance(.7) ? "SURG" : "ORTH";
                case "ICU" -> chance(.6) ? "CARD" : "GMED";
                default -> "GMED";
            };
            Doc doc = pick(doctors.stream().filter(x -> x.dept().equals(deptCode)).toList());
            long hours = "ICU".equals(ward.code()) ? between(40, 140) : 18 + (long) (-Math.log(1 - rnd.nextDouble()) * 80);
            hours = Math.min(hours, 14 * 24);
            LocalDateTime discharged = admitted.plusHours(hours).withMinute(between(0, 59));
            boolean live = discharged.isAfter(nowLocal);
            Dx dx = pick(DX.get(deptCode));
            LocalDateTime requested = admitted.minusMinutes(between(20, 180));
            String admissionType = chance(.6) ? "EMERGENCY" : chance(.9) ? "ELECTIVE" : "TRANSFER_IN";
            String dischargeType = chance(.95) ? "ROUTINE" : chance(.6) ? "AGAINST_ADVICE" : "TRANSFERRED";
            long adm = insert("""
                    INSERT INTO admissions (admission_no, patient_id, attending_doctor_id, department_id, admission_type, status, reason, requested_by,
                                            requested_at, admitted_at, discharged_at, discharge_type, discharge_summary, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", number("ADM", admitted.getYear()), p.id(), doc.id(), doc.deptId(),
                    admissionType, live ? "ADMITTED" : "DISCHARGED", dx.description(), admin, utc(requested), utc(admitted),
                    live ? null : utc(discharged), live ? null : dischargeType,
                    live ? null : "Treated for " + dx.description().toLowerCase(Locale.ROOT) + ". Condition improved; discharged with medication and clinic follow-up.",
                    utc(requested), utc(admitted));
            count("admissions");
            long stay = insert("""
                    INSERT INTO bed_assignments (admission_id, bed_id, assigned_at, released_at, release_reason, assigned_by)
                    VALUES (?, ?, ?, ?, ?, ?)""", adm, bed.id, utc(admitted), live ? null : utc(discharged), live ? null : "DISCHARGE", admin);
            long enc = insert("""
                    INSERT INTO encounters (encounter_no, patient_id, doctor_id, department_id, type, admission_id, chief_complaint, status,
                                            started_at, closed_at, created_at)
                    VALUES (?, ?, ?, ?, 'INPATIENT', ?, ?, ?, ?, ?, ?)""", number("ENC", admitted.getYear()), p.id(), doc.id(), doc.deptId(), adm,
                    dx.complaint(), live ? "OPEN" : "CLOSED", utc(admitted), live ? null : utc(discharged), utc(admitted));
            count("encounters");
            diagnoses.add(new Object[]{enc, p.id(), dx.code(), dx.description(), "PRIMARY", "CONFIRMED", doc.id(), utc(admitted.plusHours(1))});
            bed.freeAt = discharged.plusHours(3);   // cleaning
            bed.live = live;
            inpatientUntil.put(p.id(), live ? LocalDateTime.MAX : discharged);
            if (!live) {
                long days = Math.max(1, (hours + 23) / 24);
                Charge c = new Charge("ADMISSION", "Bed " + bed.label + " (" + days + " day" + (days == 1 ? "" : "s") + ")", "BED_ASSIGNMENT", stay,
                        doc.deptId(), BigDecimal.valueOf(days), bed.rate, discharged);
                LocalDateTime billed = discharged.plusMinutes(between(10, 90));
                bill(p, List.of(c), billed, adm, billed.isAfter(nowLocal) || discharged.toLocalDate().isAfter(today.minusDays(2)) && chance(.5));
            }
        }

        void waitingAdmissions() {
            for (WardS w : wards) {
                for (BedS b : w.beds()) {
                    String status = b.live ? "OCCUPIED" : b.freeAt.isAfter(nowLocal) ? "CLEANING" : "AVAILABLE";
                    jdbc.update("UPDATE beds SET status = ? WHERE id = ?", status, b.id);
                }
            }
            if (!wards.isEmpty()) {
                BedS spare = wards.get(3).beds().stream().filter(b -> !b.live).findFirst().orElse(null);
                if (spare != null && spare.freeAt.isBefore(nowLocal)) {
                    jdbc.update("UPDATE beds SET status = 'MAINTENANCE' WHERE id = ?", spare.id);
                }
            }
            for (int i = 0; i < 3; i++) {
                Pt p = patientOn(today);
                if (p.age(today) < 14 || inpatientUntil.containsKey(p.id())) {
                    continue;
                }
                Doc doc = pick(doctors);
                LocalDateTime at = nowLocal.minusMinutes(between(10, 120));
                insert("""
                        INSERT INTO admissions (admission_no, patient_id, attending_doctor_id, department_id, admission_type, status, reason,
                                                requested_by, requested_at, created_at, updated_at)
                        VALUES (?, ?, ?, ?, 'EMERGENCY', 'REQUESTED', ?, ?, ?, ?, ?)""", number("ADM", today.getYear()), p.id(), doc.id(), doc.deptId(),
                        pick(DX.get(doc.dept())).description(), admin, utc(at), utc(at), utc(at));
                inpatientUntil.put(p.id(), LocalDateTime.MAX);
                count("admissions");
            }
        }

        // ------------------------------------------------------------------ staff

        void staff() {
            Object[][] plan = {
                    {"NURSE", "NURS", 28, new String[]{"Nursing Officer", "Nursing Officer", "Nursing Officer", "Senior Nursing Officer", "Ward Sister"}},
                    {"LAB", "LABS", 6, new String[]{"Medical Laboratory Technologist"}},
                    {"PHARMACY", "PHAR", 5, new String[]{"Pharmacist", "Dispenser"}},
                    {"ADMIN", "ADMN", 8, new String[]{"Receptionist", "Management Assistant", "Medical Records Officer"}},
                    {"FINANCE", "FIN", 4, new String[]{"Accountant", "Accounts Clerk", "Cashier"}},
                    {"SUPPORT", "ADMN", 12, new String[]{"Hospital Attendant", "Hospital Attendant", "Driver", "Cleaner"}}};
            int resigned = 0;
            for (Object[] p : plan) {
                for (int i = 0; i < (int) p[2]; i++) {
                    LocalDate join = today.minusDays(between(120, 3650));
                    LocalDate leave = null;
                    String status = "ACTIVE";
                    if (resigned < 6 && chance(.08)) {
                        leave = start.plusDays(between(30, 330));
                        if (leave.isBefore(join)) {
                            leave = join.plusDays(60);
                        }
                        status = "RESIGNED";
                        resigned++;
                    }
                    String designation = pick((String[]) p[3]);
                    String gender = "NURSE".equals(p[0]) ? (chance(.85) ? "FEMALE" : "MALE") : chance(.5) ? "FEMALE" : "MALE";
                    employee((String) p[0], (String) p[1], designation, gender, join, status, leave);
                }
            }
            leaveAndAttendance();
        }

        void leaveAndAttendance() {
            List<Object[]> attendance = new ArrayList<>();
            Map<String, Long> leaveTypes = new HashMap<>();
            jdbc.query("SELECT code, id FROM leave_types", rs -> {
                leaveTypes.put(rs.getString(1), rs.getLong(2));
            });
            List<Object[]> emps = new ArrayList<>();
            for (long id : seededEmployees) {
                emps.add(jdbc.queryForObject("SELECT id, join_date, leave_date FROM employees WHERE id = ?",
                        (rs, i) -> new Object[]{rs.getLong(1), rs.getObject(2, LocalDate.class), rs.getObject(3, LocalDate.class)}, id));
            }
            for (Object[] e : emps) {
                long id = (long) e[0];
                LocalDate from = ((LocalDate) e[1]).isAfter(start) ? (LocalDate) e[1] : start;
                LocalDate until = e[2] != null ? (LocalDate) e[2] : today.plusDays(30);
                if (!from.isBefore(until)) {
                    continue;
                }
                Map<LocalDate, Boolean> onLeave = new HashMap<>();
                Map<String, Integer> used = new HashMap<>();
                String[][] wanted = {{"ANNUAL", "3"}, {"CASUAL", "3"}, {"SICK", "2"}};
                for (String[] w : wanted) {
                    int requests = between(1, Integer.parseInt(w[1]));
                    for (int r = 0; r < requests; r++) {
                        int len = "ANNUAL".equals(w[0]) ? between(1, 4) : "SICK".equals(w[0]) ? between(1, 3) : 1;
                        long span = until.toEpochDay() - from.toEpochDay() - len;
                        if (span <= 0) {
                            continue;
                        }
                        LocalDate s = from.plusDays((long) (rnd.nextDouble() * span));
                        if (s.getDayOfWeek().getValue() > 5) {
                            s = s.plusDays(8 - s.getDayOfWeek().getValue());
                        }
                        LocalDate en = s.plusDays(len - 1);
                        if (s.getYear() != en.getYear() || en.isAfter(until)) {
                            continue;
                        }
                        boolean clash = false;
                        for (LocalDate x = s; !x.isAfter(en); x = x.plusDays(1)) {
                            clash |= onLeave.containsKey(x);
                        }
                        String key = w[0] + s.getYear();
                        int cap = Map.of("ANNUAL", 14, "CASUAL", 7, "SICK", 21).get(w[0]);
                        if (clash || used.getOrDefault(key, 0) + len > cap) {
                            continue;
                        }
                        boolean future = s.isAfter(today);
                        String status = future ? (chance(.6) ? "PENDING" : "APPROVED") : chance(.88) ? "APPROVED" : chance(.65) ? "REJECTED" : "CANCELLED";
                        LocalDateTime created = s.minusDays("SICK".equals(w[0]) ? 0 : between(3, 25)).atTime(9, 0);
                        if (created.isAfter(nowLocal)) {
                            created = nowLocal.minusHours(2);
                        }
                        boolean decided = !"PENDING".equals(status) && !"CANCELLED".equals(status);
                        jdbc.update("""
                                INSERT INTO leave_requests (employee_id, leave_type_id, start_date, end_date, days, reason, status, decided_by, decided_at,
                                                            decision_note, created_at, updated_at)
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", id, leaveTypes.get(w[0]), s, en, BigDecimal.valueOf(len),
                                switch (w[0]) { case "SICK" -> "Unwell"; case "CASUAL" -> "Personal matter"; default -> "Family holiday"; },
                                status, decided ? admin : null, decided ? utc(created.plusDays(1)) : null,
                                "REJECTED".equals(status) ? "Ward is short-staffed on those dates" : null, utc(created), utc(created));
                        count("leave requests");
                        if ("APPROVED".equals(status) || "PENDING".equals(status)) {
                            used.merge(key, len, Integer::sum);
                        }
                        if ("APPROVED".equals(status)) {
                            for (LocalDate x = s; !x.isAfter(en); x = x.plusDays(1)) {
                                onLeave.put(x, true);
                            }
                        }
                    }
                }
                LocalDate last = e[2] != null && ((LocalDate) e[2]).isBefore(today) ? (LocalDate) e[2] : today.minusDays(1);
                for (LocalDate x = from; !x.isAfter(last); x = x.plusDays(1)) {
                    if (x.getDayOfWeek().getValue() > 5) {
                        continue;
                    }
                    String status;
                    LocalDateTime in = null, out = null;
                    if (onLeave.containsKey(x)) {
                        status = "ON_LEAVE";
                    } else {
                        double r = rnd.nextDouble();
                        status = r < .03 ? "ABSENT" : r < .05 ? "HALF_DAY" : "PRESENT";
                        if (!"ABSENT".equals(status)) {
                            in = x.atTime(7, 30).plusMinutes(between(0, 55));
                            out = "HALF_DAY".equals(status) ? in.plusHours(4) : x.atTime(16, 0).plusMinutes(between(0, 90));
                        }
                    }
                    attendance.add(new Object[]{id, x, n(in), n(out), status, admin});
                }
            }
            jdbc.batchUpdate("INSERT INTO attendance (employee_id, work_date, clock_in, clock_out, status, recorded_by) VALUES (?, ?, ?, ?, ?, ?)", attendance);
            counts.put("attendance rows", attendance.size());
        }

        // ------------------------------------------------------------------ batched rows and sequences

        void flush() {
            jdbc.batchUpdate("""
                    INSERT INTO diagnoses (encounter_id, patient_id, icd10_code, description, type, certainty, diagnosed_by, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", diagnoses);
            jdbc.batchUpdate("""
                    INSERT INTO invoice_items (invoice_id, charge_item_id, category, description, quantity, unit_price, line_total)
                    VALUES (?, ?, ?, ?, ?, ?, ?)""", invoiceItems);
            jdbc.batchUpdate("""
                    INSERT INTO lab_results (lab_request_item_id, numeric_value, text_value, unit, ref_range_low, ref_range_high, flag, entered_by, entered_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""", labResults);
            jdbc.batchUpdate("""
                    INSERT INTO stock_transactions (batch_id, medicine_id, type, quantity, balance_after, reference_type, reference_id, note, performed_by, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", stockTx);
            jdbc.batchUpdate("UPDATE medicine_batches SET quantity_on_hand = ? WHERE id = ?",
                    allBatches.stream().map(b -> new Object[]{b.onHand, b.id}).toList());
            seq.forEach((k, v) -> jdbc.update("""
                    INSERT INTO sequences (seq_key, next_value) VALUES (?, ?)
                    ON DUPLICATE KEY UPDATE next_value = GREATEST(next_value, ?)""", k, v, v));
            counts.put("stock transactions", stockTx.size());
        }
    }
}
