package network.t0.sdk.provider;

import network.t0.sdk.proto.tzero.v1.common.Decimal;
import network.t0.sdk.proto.tzero.v1.payment.PayoutResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidateTest {

    @Test
    @DisplayName("Valid message is returned as-is (same instance)")
    void validMessageReturnsSameInstance() {
        Decimal valid = Decimal.newBuilder().setUnscaled(12345).setExponent(2).build();
        Decimal returned = Validate.check(valid);
        assertThat(returned).isSameAs(valid);
    }

    @Test
    @DisplayName("Valid PayoutResponse passes through")
    void validPayoutResponsePassesThrough() {
        PayoutResponse valid = PayoutResponse.newBuilder()
                .setAccepted(PayoutResponse.Accepted.getDefaultInstance())
                .build();
        assertThat(Validate.check(valid)).isSameAs(valid);
    }

    @Test
    @DisplayName("Invalid Decimal throws ResponseValidationException with prefixed message")
    void invalidDecimalThrows() {
        Decimal invalid = Decimal.newBuilder().setExponent(100).build();
        assertThatThrownBy(() -> Validate.check(invalid))
                .isInstanceOf(ResponseValidationException.class)
                .hasMessageStartingWith("response validation failed");
    }

    @Test
    @DisplayName("ResponseValidationException exposes violations without the prefix")
    void violationsAreExposed() {
        Decimal invalid = Decimal.newBuilder().setExponent(100).build();
        ResponseValidationException ex = null;
        try {
            Validate.check(invalid);
        } catch (ResponseValidationException e) {
            ex = e;
        }
        assertThat(ex).isNotNull();
        assertThat(ex.getViolations()).isNotEmpty();
        assertThat(ex.getViolations()).doesNotStartWith("response validation failed");
        assertThat(ex.getMessage()).isEqualTo("response validation failed: " + ex.getViolations());
    }

    @Test
    @DisplayName("Null input is returned unchanged")
    void nullPassesThrough() {
        assertThat(Validate.<Decimal>check(null)).isNull();
    }
}
