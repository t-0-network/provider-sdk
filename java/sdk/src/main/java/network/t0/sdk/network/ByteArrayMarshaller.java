package network.t0.sdk.network;

import io.grpc.MethodDescriptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A marshaller that handles raw byte arrays without additional serialization.
 *
 * <p>This marshaller is used to send pre-serialized protobuf messages to avoid
 * double-encoding. This is critical for signature verification - the exact bytes
 * that are signed must be the same bytes sent over the wire.
 *
 * <p>See: https://github.com/grpc/grpc-java/blob/master/examples/src/main/java/io/grpc/examples/preserialized/
 */
public enum ByteArrayMarshaller implements MethodDescriptor.Marshaller<byte[]> {
    INSTANCE;

    @Override
    public byte[] parse(InputStream stream) {
        try {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read bytes from stream", e);
        }
    }

    @Override
    public InputStream stream(byte[] value) {
        return new ByteArrayInputStream(value);
    }
}
