package network.t0.sdk.provider;

import build.buf.protovalidate.exceptions.ValidationException;
import com.google.protobuf.Message;
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
        assertThat(ex.getResponseType()).isEqualTo("tzero.v1.common.Decimal");
    }

    @Test
    @DisplayName("A rule protovalidate cannot evaluate throws 'response validation error: <cause>'")
    void unevaluableRuleIsAValidationError() {
        Message msg = UnevaluableRuleMessage.message();
        String cause = UnevaluableRuleMessage.cause();
        assertThat(cause).contains("divisor_divides");

        assertThatThrownBy(() -> Validate.check(msg))
                .isInstanceOfSatisfying(ResponseValidationException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo("response validation error: " + cause);
                    assertThat(e.getViolations()).isEqualTo(cause);
                    assertThat(e.getResponseType()).isEqualTo(UnevaluableRuleMessage.TYPE_NAME);
                    assertThat(e.getCause()).isInstanceOf(ValidationException.class).hasMessage(cause);
                });
    }

    @Test
    @DisplayName("Null input is returned unchanged")
    void nullPassesThrough() {
        assertThat(Validate.<Decimal>check(null)).isNull();
    }
}
