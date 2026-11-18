package lk.hms.modules.billing.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Invoice arithmetic (docs/01 §2I): BigDecimal, scale 2, HALF_UP. Tax applies to the subtotal
 * after discount. Matches the database CHECK total = subtotal - discount + tax.
 */
public final class InvoiceMath {

    private InvoiceMath() {
    }

    public record Totals(BigDecimal subtotal, BigDecimal discount, BigDecimal tax, BigDecimal total) {
    }

    public static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal lineTotal(BigDecimal quantity, BigDecimal unitPrice) {
        return money(quantity.multiply(unitPrice));
    }

    public static Totals totals(List<BigDecimal> lineTotals, BigDecimal discount, BigDecimal taxPercent) {
        BigDecimal subtotal = money(lineTotals.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal d = money(discount == null ? BigDecimal.ZERO : discount);
        if (d.signum() < 0 || d.compareTo(subtotal) > 0) {
            throw new IllegalArgumentException("Discount must be between 0 and the subtotal.");
        }
        BigDecimal tax = money(subtotal.subtract(d).multiply(taxPercent).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        return new Totals(subtotal, d, tax, subtotal.subtract(d).add(tax));
    }
}
