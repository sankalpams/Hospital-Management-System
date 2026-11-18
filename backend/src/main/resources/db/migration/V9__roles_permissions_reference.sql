-- =====================================================================
-- V9  Reference data required in every environment:
--     roles, permissions, role grants (docs/05-roles-permissions.md),
--     leave types, system settings.
-- No user accounts here: the first Super Admin is created at startup from
-- HMS_BOOTSTRAP_ADMIN_* environment variables (never hard-coded).
-- =====================================================================

INSERT INTO roles (code, name, description) VALUES
 ('SUPER_ADMIN',    'Super Admin',            'System owner: settings, roles, all administration'),
 ('HOSPITAL_ADMIN', 'Hospital Administrator', 'Day-to-day administration: users, departments, doctors, wards, catalogues'),
 ('DOCTOR',         'Doctor',                 'Consultations, diagnoses, prescriptions, lab orders and review'),
 ('NURSE',          'Nurse',                  'Vital signs, nursing notes, ward patient care'),
 ('RECEPTIONIST',   'Receptionist',           'Registration, appointments, admissions desk, front-desk billing'),
 ('LAB_STAFF',      'Laboratory Staff',       'Sample collection, result entry, lab catalogue'),
 ('PHARMACIST',     'Pharmacist',             'Dispensing, medicine inventory, suppliers'),
 ('ACCOUNTANT',     'Accountant',             'Invoices, payments, refunds, financial reports'),
 ('HR_MANAGER',     'HR / Staff Manager',     'Employees, attendance, leave');

INSERT INTO permissions (code, module, description) VALUES
 ('user:read','users','View user accounts'),
 ('user:create','users','Create user accounts'),
 ('user:update','users','Edit user accounts and roles'),
 ('user:deactivate','users','Activate or deactivate user accounts'),
 ('user:reset-password','users','Issue a temporary password'),
 ('role:manage','users','Edit role permissions'),
 ('settings:manage','system','Manage hospital settings'),
 ('audit:read','system','View audit and login logs'),
 ('department:read','departments','View departments'),
 ('department:manage','departments','Manage departments'),
 ('patient:read','patients','Search and view patients'),
 ('patient:create','patients','Register patients'),
 ('patient:update','patients','Update patient details'),
 ('patient:document:read','patients','Download patient documents'),
 ('patient:document:upload','patients','Upload patient documents'),
 ('doctor:read','doctors','View doctors and availability'),
 ('doctor:manage','doctors','Manage doctor profiles'),
 ('schedule:manage','doctors','Manage doctor schedules'),
 ('appointment:read','appointments','View appointments'),
 ('appointment:create','appointments','Book appointments'),
 ('appointment:update','appointments','Confirm, reschedule, cancel appointments'),
 ('appointment:checkin','appointments','Check patients in'),
 ('appointment:consult','appointments','Start and complete consultations'),
 ('emr:read','emr','View medical records'),
 ('emr:write','emr','Record diagnoses and doctor notes'),
 ('vitals:record','emr','Record vital signs'),
 ('nursing-note:write','emr','Write nursing notes'),
 ('allergy:write','emr','Record allergies'),
 ('prescription:create','emr','Write prescriptions'),
 ('admission:read','inpatient','View admissions and bed board'),
 ('admission:request','inpatient','Request admissions'),
 ('admission:manage','inpatient','Admit, transfer, discharge'),
 ('ward:manage','inpatient','Manage wards, rooms, beds'),
 ('lab:request:create','laboratory','Order lab tests'),
 ('lab:request:read','laboratory','View lab requests and results'),
 ('lab:sample:collect','laboratory','Record sample collection'),
 ('lab:result:enter','laboratory','Enter lab results'),
 ('lab:result:review','laboratory','Review lab results'),
 ('lab:catalogue:manage','laboratory','Manage lab test catalogue'),
 ('prescription:read','pharmacy','View prescriptions'),
 ('pharmacy:dispense','pharmacy','Dispense medicines'),
 ('medicine:read','pharmacy','View medicines and stock'),
 ('medicine:manage','pharmacy','Manage medicines'),
 ('stock:manage','pharmacy','Receive and adjust stock'),
 ('supplier:manage','pharmacy','Manage suppliers'),
 ('billing:read','billing','View billing records'),
 ('charge:create','billing','Add service charges'),
 ('invoice:create','billing','Create and issue invoices'),
 ('invoice:void','billing','Void invoices'),
 ('payment:record','billing','Record payments'),
 ('payment:refund','billing','Record refunds'),
 ('employee:read','staff','View employees'),
 ('employee:manage','staff','Manage employees'),
 ('attendance:manage','staff','Manage attendance'),
 ('leave:approve','staff','Approve leave'),
 ('report:operational','reports','Operational analytics'),
 ('report:financial','reports','Financial analytics'),
 ('report:staff','reports','Staff reports');

-- Helper pattern: grant a list of permission codes to a role code.
-- SUPER_ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
  ON p.code IN ('user:read','user:create','user:update','user:deactivate','user:reset-password','role:manage',
                'settings:manage','audit:read','department:read','department:manage',
                'patient:read','patient:create','patient:update','patient:document:read','patient:document:upload',
                'doctor:read','doctor:manage','schedule:manage',
                'appointment:read','appointment:create','appointment:update','appointment:checkin',
                'admission:read','admission:manage','ward:manage','lab:catalogue:manage',
                'medicine:read','medicine:manage','supplier:manage','billing:read',
                'employee:read','employee:manage','attendance:manage','leave:approve',
                'report:operational','report:financial','report:staff')
WHERE r.code = 'SUPER_ADMIN';

-- HOSPITAL_ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
  ON p.code IN ('user:read','user:create','user:update','user:deactivate','user:reset-password',
                'audit:read','department:read','department:manage',
                'patient:read','patient:create','patient:update','patient:document:read','patient:document:upload',
                'doctor:read','doctor:manage','schedule:manage',
                'appointment:read','appointment:create','appointment:update','appointment:checkin',
                'admission:read','admission:manage','ward:manage','lab:catalogue:manage',
                'medicine:read','medicine:manage','supplier:manage','billing:read','employee:read',
                'report:operational','report:financial','report:staff')
WHERE r.code = 'HOSPITAL_ADMIN';

-- DOCTOR
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
  ON p.code IN ('department:read','patient:read','patient:document:read','patient:document:upload','doctor:read',
                'appointment:read','appointment:consult',
                'emr:read','emr:write','vitals:record','allergy:write','prescription:create',
                'admission:read','admission:request',
                'lab:request:create','lab:request:read','lab:result:review',
                'prescription:read','medicine:read')
WHERE r.code = 'DOCTOR';

-- NURSE
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
  ON p.code IN ('department:read','patient:read','patient:document:read','doctor:read','appointment:read',
                'emr:read','vitals:record','nursing-note:write','allergy:write',
                'admission:read','lab:request:read','prescription:read','medicine:read')
WHERE r.code = 'NURSE';

-- RECEPTIONIST
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
  ON p.code IN ('department:read','patient:read','patient:create','patient:update','patient:document:read','patient:document:upload',
                'doctor:read','appointment:read','appointment:create','appointment:update','appointment:checkin',
                'admission:read','admission:request','admission:manage',
                'billing:read','charge:create','invoice:create','payment:record')
WHERE r.code = 'RECEPTIONIST';

-- LAB_STAFF
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
  ON p.code IN ('department:read','patient:read','patient:document:upload','doctor:read',
                'lab:request:read','lab:sample:collect','lab:result:enter','lab:catalogue:manage')
WHERE r.code = 'LAB_STAFF';

-- PHARMACIST
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
  ON p.code IN ('department:read','patient:read','doctor:read','prescription:read','pharmacy:dispense',
                'medicine:read','medicine:manage','stock:manage','supplier:manage')
WHERE r.code = 'PHARMACIST';

-- ACCOUNTANT
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
  ON p.code IN ('department:read','patient:read','doctor:read','appointment:read','admission:read',
                'billing:read','charge:create','invoice:create','invoice:void','payment:record','payment:refund',
                'report:financial')
WHERE r.code = 'ACCOUNTANT';

-- HR_MANAGER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
  ON p.code IN ('user:read','department:read','doctor:read','employee:read','employee:manage',
                'attendance:manage','leave:approve','report:staff')
WHERE r.code = 'HR_MANAGER';

INSERT INTO leave_types (code, name, days_per_year, paid) VALUES
 ('ANNUAL',    'Annual leave',    14,   TRUE),
 ('CASUAL',    'Casual leave',    7,    TRUE),
 ('SICK',      'Sick leave',      21,   TRUE),
 ('MATERNITY', 'Maternity leave', 84,   TRUE),
 ('NO_PAY',    'No-pay leave',    NULL, FALSE);

INSERT INTO system_settings (setting_key, setting_value, value_type, description) VALUES
 ('hospital.name',                 'HMS General Hospital', 'STRING',  'Name printed on receipts and reports'),
 ('hospital.timezone',             'Asia/Colombo',         'STRING',  'Timezone for "today", slot times and reports'),
 ('hospital.currency',             'LKR',                  'STRING',  'Currency code'),
 ('billing.tax_rate_percent',      '0.00',                 'DECIMAL', 'Tax applied to invoice subtotal after discount'),
 ('pharmacy.expiry_alert_days',    '90',                   'INT',     'Batches expiring within this many days are flagged'),
 ('security.max_failed_logins',    '5',                    'INT',     'Failed logins before temporary lock'),
 ('security.lock_minutes',         '15',                   'INT',     'Lock duration after too many failed logins');
