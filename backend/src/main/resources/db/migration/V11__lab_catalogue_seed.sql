-- =====================================================================
-- V11  Starter laboratory test catalogue (adult reference ranges; prices are example LKR
--      values). Lab staff with lab:catalogue:manage adjust ranges and prices to the local lab.
-- =====================================================================
INSERT INTO lab_tests (code, name, category, sample_type, result_type, unit, ref_range_low, ref_range_high, ref_range_text, turnaround_hours, price) VALUES
 ('HB',    'Haemoglobin',                'HAEMATOLOGY',  'BLOOD', 'NUMERIC', 'g/dL',     12.0,  16.0,  NULL, 4,  450.00),
 ('WBC',   'White blood cell count',     'HAEMATOLOGY',  'BLOOD', 'NUMERIC', '10^9/L',    4.0,  11.0,  NULL, 4,  450.00),
 ('PLT',   'Platelet count',             'HAEMATOLOGY',  'BLOOD', 'NUMERIC', '10^9/L',  150.0, 400.0,  NULL, 4,  450.00),
 ('FBS',   'Fasting blood sugar',        'BIOCHEMISTRY', 'BLOOD', 'NUMERIC', 'mmol/L',    3.9,   5.5,  NULL, 2,  400.00),
 ('HBA1C', 'HbA1c',                      'BIOCHEMISTRY', 'BLOOD', 'NUMERIC', '%',         4.0,   5.6,  NULL, 24, 2200.00),
 ('CREAT', 'Serum creatinine',           'BIOCHEMISTRY', 'BLOOD', 'NUMERIC', 'umol/L',   60.0, 110.0,  NULL, 6,  900.00),
 ('CHOL',  'Total cholesterol',          'BIOCHEMISTRY', 'BLOOD', 'NUMERIC', 'mmol/L',    0.0,   5.2,  NULL, 6,  850.00),
 ('CRP',   'C-reactive protein',         'IMMUNOLOGY',   'BLOOD', 'NUMERIC', 'mg/L',      0.0,   5.0,  NULL, 6, 1500.00),
 ('TSH',   'Thyroid stimulating hormone','ENDOCRINOLOGY','BLOOD', 'NUMERIC', 'mIU/L',     0.4,   4.0,  NULL, 24, 2800.00),
 ('UFR',   'Urine full report',          'MICROBIOLOGY', 'URINE', 'TEXT',    NULL,       NULL,  NULL,  'No pus cells, RBC or organisms', 4, 500.00);
