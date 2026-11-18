-- =====================================================================
-- V10  Billing adjustments
--  * A charge on a VOID invoice must be billable again. charge_items.invoice_id already
--    points at the one live invoice, so invoice_items keeps history and no longer needs
--    a unique charge_item_id.
--  * Starter service catalogue (prices are examples in LKR; edit to the hospital's tariff).
-- =====================================================================

ALTER TABLE invoice_items ADD INDEX ix_invoice_items_charge (charge_item_id);
ALTER TABLE invoice_items DROP INDEX uk_invoice_items_charge;

INSERT INTO service_catalogue (code, name, category, price) VALUES
 ('PROC-DRESS', 'Wound dressing',            'PROCEDURE', 1500.00),
 ('PROC-SUTURE', 'Suturing (simple)',        'PROCEDURE', 3500.00),
 ('NURS-NEB',   'Nebulisation',              'NURSING',    800.00),
 ('NURS-INJ',   'Injection administration',  'NURSING',    300.00),
 ('IMG-XRAY',   'X-ray (single view)',       'IMAGING',   2500.00),
 ('IMG-ECG',    'ECG',                       'IMAGING',   1200.00),
 ('AMB-LOCAL',  'Ambulance (within 10 km)',  'AMBULANCE', 4000.00);
