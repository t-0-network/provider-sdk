package network.t0.sdk.provider;

import build.buf.protovalidate.Validator;
import build.buf.protovalidate.ValidatorFactory;

/**
 * Internal holder for a process-wide {@link Validator} singleton used by the
 * {@link ResponseValidationInterceptor} and the public {@link Validate} helper.
 *
 * <p>The validator instance is thread-safe and reasonably expensive to construct,
 * so we share a single instance across both call sites.
 */
final class Validators {

    private static final Validator INSTANCE = ValidatorFactory.newBuilder().build();

    private Validators() {}

    static Validator shared() {
        return INSTANCE;
    }
}
