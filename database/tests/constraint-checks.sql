-- =====================================================================
-- Database-level integrity checks (the last line of defence behind the services).
-- Run against an EMPTY scratch schema that has had all Flyway migrations applied,
-- with mysql --force so every statement runs. Each "expect" line states the result.
-- See docs/07-testing.md for the runner command. Never run against a real database.
-- =====================================================================
INSERT INTO users(username,password_hash,full_name) VALUES ('u1','x','U One');
INSERT INTO departments(code,name,type) VALUES ('GEN','General Medicine','CLINICAL');
INSERT INTO employees(employee_no,department_id,first_name,last_name,gender,phone,designation,staff_category,employment_type,join_date)
  VALUES ('EMP-1',1,'A','B','MALE','0771234567','Physician','DOCTOR','PERMANENT','2020-01-01');
INSERT INTO doctors(employee_id,registration_no,specialization,consultation_fee) VALUES (1,'SLMC1','General',2500.00);
INSERT INTO patients(mrn,first_name,last_name,gender,date_of_birth,phone)
  VALUES ('HMS-2026-000001','P','Q','FEMALE','1990-01-01','0711111111'),('HMS-2026-000002','R','S','MALE','1985-01-01','0722222222');
INSERT INTO appointments(appointment_no,patient_id,doctor_id,department_id,slot_start,slot_end,token_no)
  VALUES ('A1',1,1,1,'2026-12-01 04:00:00','2026-12-01 04:15:00',1);

SELECT 'T1 double booking of a doctor slot -> expect ERROR 1062' AS test;
INSERT INTO appointments(appointment_no,patient_id,doctor_id,department_id,slot_start,slot_end,token_no)
  VALUES ('A2',2,1,1,'2026-12-01 04:00:00','2026-12-01 04:15:00',2);

UPDATE appointments SET status='CANCELLED', cancel_reason='test' WHERE id=1;
SELECT 'T2 cancelled slot can be rebooked -> expect rows=2' AS test;
INSERT INTO appointments(appointment_no,patient_id,doctor_id,department_id,slot_start,slot_end,token_no)
  VALUES ('A3',2,1,1,'2026-12-01 04:00:00','2026-12-01 04:15:00',2);
SELECT CONCAT('   rows=',COUNT(*)) AS result FROM appointments;

SELECT 'T3 cancellation without reason -> expect ERROR 3819' AS test;
UPDATE appointments SET status='CANCELLED' WHERE appointment_no='A3';

INSERT INTO wards(code,name,department_id,ward_type) VALUES ('W1','Ward 1',1,'GENERAL');
INSERT INTO rooms(ward_id,room_no,room_type,daily_rate) VALUES (1,'101','SHARED',5000);
INSERT INTO beds(room_id,bed_no) VALUES (1,'A');
INSERT INTO admissions(admission_no,patient_id,attending_doctor_id,department_id,admission_type,status,reason,requested_at)
  VALUES ('AD1',1,1,1,'EMERGENCY','ADMITTED','x',NOW()),('AD2',2,1,1,'EMERGENCY','ADMITTED','y',NOW());
INSERT INTO bed_assignments(admission_id,bed_id,assigned_at) VALUES (1,1,NOW());

SELECT 'T4 second patient in an occupied bed -> expect ERROR 1062' AS test;
INSERT INTO bed_assignments(admission_id,bed_id,assigned_at) VALUES (2,1,NOW());

SELECT 'T5 second open admission for one patient -> expect ERROR 1062' AS test;
INSERT INTO admissions(admission_no,patient_id,attending_doctor_id,department_id,admission_type,status,reason,requested_at)
  VALUES ('AD3',1,1,1,'EMERGENCY','REQUESTED','z',NOW());

INSERT INTO medicine_categories(name) VALUES ('Analgesic');
INSERT INTO medicines(code,generic_name,category_id,dosage_form,strength,unit,unit_price)
  VALUES ('M1','Paracetamol',1,'TABLET','500 mg','tablet',5.00);
INSERT INTO medicine_batches(medicine_id,batch_no,expiry_date,received_date,quantity_received,quantity_on_hand,cost_price)
  VALUES (1,'B1','2027-12-31','2026-01-01',100,10,2.00);

SELECT 'T6 dispensing more than stock on hand -> expect ERROR 3819' AS test;
UPDATE medicine_batches SET quantity_on_hand = quantity_on_hand - 11 WHERE id=1;

INSERT INTO invoices(invoice_no,patient_id,status,subtotal,total_amount) VALUES ('I1',1,'ISSUED',1000.00,1000.00);
SELECT 'T7 paying more than the invoice total -> expect ERROR 3819' AS test;
UPDATE invoices SET amount_paid=1000.01 WHERE id=1;

SELECT 'T8 total != subtotal - discount + tax -> expect ERROR 3819' AS test;
UPDATE invoices SET total_amount=900.00 WHERE id=1;

SELECT 'T9 systolic lower than diastolic -> expect ERROR 3819' AS test;
INSERT INTO vital_signs(patient_id,recorded_by,recorded_at,systolic_mmhg,diastolic_mmhg) VALUES (1,1,NOW(),80,120);

SELECT 'T10 refund not linked to an original payment -> expect ERROR 3819' AS test;
INSERT INTO payments(receipt_no,invoice_id,amount,method,received_by,received_at) VALUES ('R1',1,-50.00,'CASH',1,NOW());

UPDATE invoices SET amount_paid=400.00 WHERE id=1;
SELECT CONCAT('T11 generated balance_due=', balance_due, ' -> expect 600.00') AS result FROM invoices;
