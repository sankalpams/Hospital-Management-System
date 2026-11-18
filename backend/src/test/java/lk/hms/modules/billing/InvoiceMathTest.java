package lk.hms.modules.billing;

import lk.hms.modules.billing.service.InvoiceMath;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceMathTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void totalIsSubtotalMinusDiscountPlusTaxOnTheDiscountedAmount() {
        InvoiceMath.Totals t = InvoiceMath.totals(List.of(d("2500.00"), d("1200.00")), d("200.00"), d("8"));
        assertThat(t.subtotal()).isEqualByComparingTo("3700.00");
        assertThat(t.tax()).isEqualByComparingTo("280.00");          // 8% of 3500
        assertThat(t.total()).isEqualByComparingTo("3780.00");
        assertThat(t.total()).isEqualByComparingTo(t.subtotal().subtract(t.discount()).add(t.tax()));
    }

    @Test
    void roundsHalfUpToCents() {
        assertThat(InvoiceMath.lineTotal(d("3"), d("3.335"))).isEqualByComparingTo("10.01");
        assertThat(InvoiceMath.totals(List.of(d("0.05")), BigDecimal.ZERO, d("10")).tax()).isEqualByComparingTo("0.01");
    }

    @Test
    void zeroTaxAndNoDiscount() {
        InvoiceMath.Totals t = InvoiceMath.totals(List.of(d("100")), null, BigDecimal.ZERO);
        assertThat(t.total()).isEqualByComparingTo("100.00");
    }

    @Test
    void discountCannotExceedSubtotalOrBeNegative() {
        assertThatThrownBy(() -> InvoiceMath.totals(List.of(d("100")), d("100.01"), BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InvoiceMath.totals(List.of(d("100")), d("-1"), BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }
}
