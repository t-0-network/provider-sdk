package network.t0.sdk.proto.tzero.v1.payment_intent.provider;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 **
 * ProviderService is implemented by provider to provide pay-in details fpr payment intents
 * </pre>
 */
@io.grpc.stub.annotations.GrpcGenerated
public final class ProviderServiceGrpc {

  private ProviderServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "tzero.v1.payment_intent.provider.ProviderService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.provider.CreatePaymentIntentRequest,
      network.t0.sdk.proto.tzero.v1.payment_intent.provider.CreatePaymentIntentResponse> getCreatePaymentIntentMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "CreatePaymentIntent",
      requestType = network.t0.sdk.proto.tzero.v1.payment_intent.provider.CreatePaymentIntentRequest.class,
      responseType = network.t0.sdk.proto.tzero.v1.payment_intent.provider.CreatePaymentIntentResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.provider.CreatePaymentIntentRequest,
      network.t0.sdk.proto.tzero.v1.payment_intent.provider.CreatePaymentIntentResponse> getCreatePaymentIntentMethod() {
    io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.provider.CreatePaymentIntentRequest, network.t0.sdk.proto.tzero.v1.payment_intent.provider.CreatePaymentIntentResponse> getCreatePaymentIntentMethod;
    if ((getCreatePaymentIntentMethod = ProviderServiceGrpc.getCreatePaymentIntentMethod) == null) {
      synchronized (ProviderServiceGrpc.class) {
        if ((getCreatePaymentIntentMethod = ProviderServiceGrpc.getCreatePaymentIntentMethod) == null) {
          ProviderServiceGrpc.getCreatePaymentIntentMethod = getCreatePaymentIntentMethod =
              io.grpc.MethodDescriptor.<network.t0.sdk.proto.tzero.v1.payment_intent.provider.CreatePaymentIntentRequest, network.t0.sdk.proto.tzero.v1.payment_intent.provider.CreatePaymentIntentResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "CreatePaymentIntent"))
              .setIdempotent(true)
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment_intent.provider.CreatePaymentIntentRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment_intent.provider.CreatePaymentIntentResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ProviderServiceMethodDescriptorSupplier("CreatePaymentIntent"))
              .build();
        }
      }
    }
    return getCreatePaymentIntentMethod;
  }

  private static volatile io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPayoutRequest,
      network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPayoutResponse> getConfirmPayoutMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ConfirmPayout",
      requestType = network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPayoutRequest.class,
      responseType = network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPayoutResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPayoutRequest,
      network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPayoutResponse> getConfirmPayoutMethod() {
    io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPayoutRequest, network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPayoutResponse> getConfirmPayoutMethod;
    if ((getConfirmPayoutMethod = ProviderServiceGrpc.getConfirmPayoutMethod) == null) {
      synchronized (ProviderServiceGrpc.class) {
        if ((getConfirmPayoutMethod = ProviderServiceGrpc.getConfirmPayoutMethod) == null) {
          ProviderServiceGrpc.getConfirmPayoutMethod = getConfirmPayoutMethod =
              io.grpc.MethodDescriptor.<network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPayoutRequest, network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPayoutResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ConfirmPayout"))
              .setIdempotent(true)
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPayoutRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPayoutResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ProviderServiceMethodDescriptorSupplier("ConfirmPayout"))
              .build();
        }
      }
    }
    return getConfirmPayoutMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static ProviderServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ProviderServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ProviderServiceStub>() {
        @java.lang.Override
        public ProviderServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ProviderServiceStub(channel, callOptions);
        }
      };
    return ProviderServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static ProviderServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ProviderServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ProviderServiceBlockingV2Stub>() {
        @java.lang.Override
        public ProviderServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ProviderServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return ProviderServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static ProviderServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ProviderServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ProviderServiceBlockingStub>() {
        @java.lang.Override
        public ProviderServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ProviderServiceBlockingStub(channel, callOptions);
        }
      };
    return ProviderServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static ProviderServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ProviderServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ProviderServiceFutureStub>() {
        @java.lang.Override
        public ProviderServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ProviderServiceFutureStub(channel, callOptions);
        }
      };
    return ProviderServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   **
   * ProviderService is implemented by provider to provide pay-in details fpr payment intents
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     **
     * Network instructs provider to create payment details for the payment intent. Provide should return
     * a list of supported payment method along with URL where payer should be redirected.
     * </pre>
     */
    default void createPaymentIntent(network.t0.sdk.proto.tzero.v1.payment_intent.provider.CreatePaymentIntentRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.provider.CreatePaymentIntentResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getCreatePaymentIntentMethod(), responseObserver);
    }

    /**
     * <pre>
     **
     * Network notifies provider about successful payout for the corresponding payment intent
     * </pre>
     */
    default void confirmPayout(network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPayoutRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPayoutResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getConfirmPayoutMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service ProviderService.
   * <pre>
   **
   * ProviderService is implemented by provider to provide pay-in details fpr payment intents
   * </pre>
   */
  public static abstract class ProviderServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return ProviderServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service ProviderService.
   * <pre>
   **
   * ProviderService is implemented by provider to provide pay-in details fpr payment intents
   * </pre>
   */
  public static final class ProviderServiceStub
      extends io.grpc.stub.AbstractAsyncStub<ProviderServiceStub> {
    private ProviderServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ProviderServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ProviderServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     **
     * Network instructs provider to create payment details for the payment intent. Provide should return
     * a list of supported payment method along with URL where payer should be redirected.
     * </pre>
     */
    public void createPaymentIntent(network.t0.sdk.proto.tzero.v1.payment_intent.provider.CreatePaymentIntentRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.provider.CreatePaymentIntentResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getCreatePaymentIntentMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     **
     * Network notifies provider about successful payout for the corresponding payment intent
     * </pre>
     */
    public void confirmPayout(network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPayoutRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPayoutResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getConfirmPayoutMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service ProviderService.
   * <pre>
   **
   * ProviderService is implemented by provider to provide pay-in details fpr payment intents
   * </pre>
   */
  public static final class ProviderServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<ProviderServiceBlockingV2Stub> {
    private ProviderServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ProviderServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ProviderServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     * <pre>
     **
     * Network instructs provider to create payment details for the payment intent. Provide should return
     * a list of supported payment method along with URL where payer should be redirected.
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment_intent.provider.CreatePaymentIntentResponse createPaymentIntent(network.t0.sdk.proto.tzero.v1.payment_intent.provider.CreatePaymentIntentRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getCreatePaymentIntentMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     **
     * Network notifies provider about successful payout for the corresponding payment intent
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPayoutResponse confirmPayout(network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPayoutRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getConfirmPayoutMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service ProviderService.
   * <pre>
   **
   * ProviderService is implemented by provider to provide pay-in details fpr payment intents
   * </pre>
   */
  public static final class ProviderServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<ProviderServiceBlockingStub> {
    private ProviderServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ProviderServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ProviderServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     **
     * Network instructs provider to create payment details for the payment intent. Provide should return
     * a list of supported payment method along with URL where payer should be redirected.
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment_intent.provider.CreatePaymentIntentResponse createPaymentIntent(network.t0.sdk.proto.tzero.v1.payment_intent.provider.CreatePaymentIntentRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCreatePaymentIntentMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     **
     * Network notifies provider about successful payout for the corresponding payment intent
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPayoutResponse confirmPayout(network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPayoutRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getConfirmPayoutMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service ProviderService.
   * <pre>
   **
   * ProviderService is implemented by provider to provide pay-in details fpr payment intents
   * </pre>
   */
  public static final class ProviderServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<ProviderServiceFutureStub> {
    private ProviderServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ProviderServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ProviderServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     **
     * Network instructs provider to create payment details for the payment intent. Provide should return
     * a list of supported payment method along with URL where payer should be redirected.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<network.t0.sdk.proto.tzero.v1.payment_intent.provider.CreatePaymentIntentResponse> createPaymentIntent(
        network.t0.sdk.proto.tzero.v1.payment_intent.provider.CreatePaymentIntentRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getCreatePaymentIntentMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     **
     * Network notifies provider about successful payout for the corresponding payment intent
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPayoutResponse> confirmPayout(
        network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPayoutRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getConfirmPayoutMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_CREATE_PAYMENT_INTENT = 0;
  private static final int METHODID_CONFIRM_PAYOUT = 1;

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
        case METHODID_CREATE_PAYMENT_INTENT:
          serviceImpl.createPaymentIntent((network.t0.sdk.proto.tzero.v1.payment_intent.provider.CreatePaymentIntentRequest) request,
              (io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.provider.CreatePaymentIntentResponse>) responseObserver);
          break;
        case METHODID_CONFIRM_PAYOUT:
          serviceImpl.confirmPayout((network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPayoutRequest) request,
              (io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPayoutResponse>) responseObserver);
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
          getCreatePaymentIntentMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              network.t0.sdk.proto.tzero.v1.payment_intent.provider.CreatePaymentIntentRequest,
              network.t0.sdk.proto.tzero.v1.payment_intent.provider.CreatePaymentIntentResponse>(
                service, METHODID_CREATE_PAYMENT_INTENT)))
        .addMethod(
          getConfirmPayoutMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPayoutRequest,
              network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPayoutResponse>(
                service, METHODID_CONFIRM_PAYOUT)))
        .build();
  }

  private static abstract class ProviderServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    ProviderServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return network.t0.sdk.proto.tzero.v1.payment_intent.provider.ProviderProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("ProviderService");
    }
  }

  private static final class ProviderServiceFileDescriptorSupplier
      extends ProviderServiceBaseDescriptorSupplier {
    ProviderServiceFileDescriptorSupplier() {}
  }

  private static final class ProviderServiceMethodDescriptorSupplier
      extends ProviderServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    ProviderServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (ProviderServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new ProviderServiceFileDescriptorSupplier())
              .addMethod(getCreatePaymentIntentMethod())
              .addMethod(getConfirmPayoutMethod())
              .build();
        }
      }
    }
    return result;
  }
}
