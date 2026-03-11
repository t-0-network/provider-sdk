package network.t0.sdk.proto.tzero.v1.payment_intent;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 **
 * BeneficiaryService must be implemented by beneficiary providers to receive
 * notifications about payment intent status changes.
 * Beneficiary providers are those who:
 * - Create payment intents via CreatePaymentIntent
 * - Receive settlement (in settlement currency via configured blockchain network)
 * - Need to be notified of payment status changes
 * The network calls this service to notify the beneficiary when:
 * - Funds have been received from the payer by pay-in provider
 * </pre>
 */
@io.grpc.stub.annotations.GrpcGenerated
public final class BeneficiaryServiceGrpc {

  private BeneficiaryServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "tzero.v1.payment_intent.BeneficiaryService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentUpdateRequest,
      network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentUpdateResponse> getPaymentIntentUpdateMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "PaymentIntentUpdate",
      requestType = network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentUpdateRequest.class,
      responseType = network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentUpdateResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentUpdateRequest,
      network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentUpdateResponse> getPaymentIntentUpdateMethod() {
    io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentUpdateRequest, network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentUpdateResponse> getPaymentIntentUpdateMethod;
    if ((getPaymentIntentUpdateMethod = BeneficiaryServiceGrpc.getPaymentIntentUpdateMethod) == null) {
      synchronized (BeneficiaryServiceGrpc.class) {
        if ((getPaymentIntentUpdateMethod = BeneficiaryServiceGrpc.getPaymentIntentUpdateMethod) == null) {
          BeneficiaryServiceGrpc.getPaymentIntentUpdateMethod = getPaymentIntentUpdateMethod =
              io.grpc.MethodDescriptor.<network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentUpdateRequest, network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentUpdateResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "PaymentIntentUpdate"))
              .setIdempotent(true)
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentUpdateRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentUpdateResponse.getDefaultInstance()))
              .setSchemaDescriptor(new BeneficiaryServiceMethodDescriptorSupplier("PaymentIntentUpdate"))
              .build();
        }
      }
    }
    return getPaymentIntentUpdateMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static BeneficiaryServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<BeneficiaryServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<BeneficiaryServiceStub>() {
        @java.lang.Override
        public BeneficiaryServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new BeneficiaryServiceStub(channel, callOptions);
        }
      };
    return BeneficiaryServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static BeneficiaryServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<BeneficiaryServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<BeneficiaryServiceBlockingV2Stub>() {
        @java.lang.Override
        public BeneficiaryServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new BeneficiaryServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return BeneficiaryServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static BeneficiaryServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<BeneficiaryServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<BeneficiaryServiceBlockingStub>() {
        @java.lang.Override
        public BeneficiaryServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new BeneficiaryServiceBlockingStub(channel, callOptions);
        }
      };
    return BeneficiaryServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static BeneficiaryServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<BeneficiaryServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<BeneficiaryServiceFutureStub>() {
        @java.lang.Override
        public BeneficiaryServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new BeneficiaryServiceFutureStub(channel, callOptions);
        }
      };
    return BeneficiaryServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   **
   * BeneficiaryService must be implemented by beneficiary providers to receive
   * notifications about payment intent status changes.
   * Beneficiary providers are those who:
   * - Create payment intents via CreatePaymentIntent
   * - Receive settlement (in settlement currency via configured blockchain network)
   * - Need to be notified of payment status changes
   * The network calls this service to notify the beneficiary when:
   * - Funds have been received from the payer by pay-in provider
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     **
     * PaymentIntentUpdate notifies the beneficiary provider of status changes.
     * Idempotency: This endpoint must be idempotent. The network may retry
     * delivery in case of failures or timeouts.
     * </pre>
     */
    default void paymentIntentUpdate(network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentUpdateRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentUpdateResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getPaymentIntentUpdateMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service BeneficiaryService.
   * <pre>
   **
   * BeneficiaryService must be implemented by beneficiary providers to receive
   * notifications about payment intent status changes.
   * Beneficiary providers are those who:
   * - Create payment intents via CreatePaymentIntent
   * - Receive settlement (in settlement currency via configured blockchain network)
   * - Need to be notified of payment status changes
   * The network calls this service to notify the beneficiary when:
   * - Funds have been received from the payer by pay-in provider
   * </pre>
   */
  public static abstract class BeneficiaryServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return BeneficiaryServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service BeneficiaryService.
   * <pre>
   **
   * BeneficiaryService must be implemented by beneficiary providers to receive
   * notifications about payment intent status changes.
   * Beneficiary providers are those who:
   * - Create payment intents via CreatePaymentIntent
   * - Receive settlement (in settlement currency via configured blockchain network)
   * - Need to be notified of payment status changes
   * The network calls this service to notify the beneficiary when:
   * - Funds have been received from the payer by pay-in provider
   * </pre>
   */
  public static final class BeneficiaryServiceStub
      extends io.grpc.stub.AbstractAsyncStub<BeneficiaryServiceStub> {
    private BeneficiaryServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected BeneficiaryServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new BeneficiaryServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     **
     * PaymentIntentUpdate notifies the beneficiary provider of status changes.
     * Idempotency: This endpoint must be idempotent. The network may retry
     * delivery in case of failures or timeouts.
     * </pre>
     */
    public void paymentIntentUpdate(network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentUpdateRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentUpdateResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getPaymentIntentUpdateMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service BeneficiaryService.
   * <pre>
   **
   * BeneficiaryService must be implemented by beneficiary providers to receive
   * notifications about payment intent status changes.
   * Beneficiary providers are those who:
   * - Create payment intents via CreatePaymentIntent
   * - Receive settlement (in settlement currency via configured blockchain network)
   * - Need to be notified of payment status changes
   * The network calls this service to notify the beneficiary when:
   * - Funds have been received from the payer by pay-in provider
   * </pre>
   */
  public static final class BeneficiaryServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<BeneficiaryServiceBlockingV2Stub> {
    private BeneficiaryServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected BeneficiaryServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new BeneficiaryServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     * <pre>
     **
     * PaymentIntentUpdate notifies the beneficiary provider of status changes.
     * Idempotency: This endpoint must be idempotent. The network may retry
     * delivery in case of failures or timeouts.
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentUpdateResponse paymentIntentUpdate(network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentUpdateRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getPaymentIntentUpdateMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service BeneficiaryService.
   * <pre>
   **
   * BeneficiaryService must be implemented by beneficiary providers to receive
   * notifications about payment intent status changes.
   * Beneficiary providers are those who:
   * - Create payment intents via CreatePaymentIntent
   * - Receive settlement (in settlement currency via configured blockchain network)
   * - Need to be notified of payment status changes
   * The network calls this service to notify the beneficiary when:
   * - Funds have been received from the payer by pay-in provider
   * </pre>
   */
  public static final class BeneficiaryServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<BeneficiaryServiceBlockingStub> {
    private BeneficiaryServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected BeneficiaryServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new BeneficiaryServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     **
     * PaymentIntentUpdate notifies the beneficiary provider of status changes.
     * Idempotency: This endpoint must be idempotent. The network may retry
     * delivery in case of failures or timeouts.
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentUpdateResponse paymentIntentUpdate(network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentUpdateRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getPaymentIntentUpdateMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service BeneficiaryService.
   * <pre>
   **
   * BeneficiaryService must be implemented by beneficiary providers to receive
   * notifications about payment intent status changes.
   * Beneficiary providers are those who:
   * - Create payment intents via CreatePaymentIntent
   * - Receive settlement (in settlement currency via configured blockchain network)
   * - Need to be notified of payment status changes
   * The network calls this service to notify the beneficiary when:
   * - Funds have been received from the payer by pay-in provider
   * </pre>
   */
  public static final class BeneficiaryServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<BeneficiaryServiceFutureStub> {
    private BeneficiaryServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected BeneficiaryServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new BeneficiaryServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     **
     * PaymentIntentUpdate notifies the beneficiary provider of status changes.
     * Idempotency: This endpoint must be idempotent. The network may retry
     * delivery in case of failures or timeouts.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentUpdateResponse> paymentIntentUpdate(
        network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentUpdateRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getPaymentIntentUpdateMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_PAYMENT_INTENT_UPDATE = 0;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_PAYMENT_INTENT_UPDATE:
          serviceImpl.paymentIntentUpdate((network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentUpdateRequest) request,
              (io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentUpdateResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getPaymentIntentUpdateMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentUpdateRequest,
              network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentUpdateResponse>(
                service, METHODID_PAYMENT_INTENT_UPDATE)))
        .build();
  }

  private static abstract class BeneficiaryServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    BeneficiaryServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return network.t0.sdk.proto.tzero.v1.payment_intent.BeneficiaryProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("BeneficiaryService");
    }
  }

  private static final class BeneficiaryServiceFileDescriptorSupplier
      extends BeneficiaryServiceBaseDescriptorSupplier {
    BeneficiaryServiceFileDescriptorSupplier() {}
  }

  private static final class BeneficiaryServiceMethodDescriptorSupplier
      extends BeneficiaryServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    BeneficiaryServiceMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (BeneficiaryServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new BeneficiaryServiceFileDescriptorSupplier())
              .addMethod(getPaymentIntentUpdateMethod())
              .build();
        }
      }
    }
    return result;
  }
}
