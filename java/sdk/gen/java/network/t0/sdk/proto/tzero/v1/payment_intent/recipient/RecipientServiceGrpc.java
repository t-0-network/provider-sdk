package network.t0.sdk.proto.tzero.v1.payment_intent.recipient;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 **
 * RecipientService is implemented by recipient in order to get updates on payment intents
 * </pre>
 */
@io.grpc.stub.annotations.GrpcGenerated
public final class RecipientServiceGrpc {

  private RecipientServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "tzero.v1.payment_intent.recipient.RecipientService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPayInRequest,
      network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPayInResponse> getConfirmPayInMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ConfirmPayIn",
      requestType = network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPayInRequest.class,
      responseType = network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPayInResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPayInRequest,
      network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPayInResponse> getConfirmPayInMethod() {
    io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPayInRequest, network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPayInResponse> getConfirmPayInMethod;
    if ((getConfirmPayInMethod = RecipientServiceGrpc.getConfirmPayInMethod) == null) {
      synchronized (RecipientServiceGrpc.class) {
        if ((getConfirmPayInMethod = RecipientServiceGrpc.getConfirmPayInMethod) == null) {
          RecipientServiceGrpc.getConfirmPayInMethod = getConfirmPayInMethod =
              io.grpc.MethodDescriptor.<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPayInRequest, network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPayInResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ConfirmPayIn"))
              .setIdempotent(true)
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPayInRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPayInResponse.getDefaultInstance()))
              .setSchemaDescriptor(new RecipientServiceMethodDescriptorSupplier("ConfirmPayIn"))
              .build();
        }
      }
    }
    return getConfirmPayInMethod;
  }

  private static volatile io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPaymentRequest,
      network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPaymentResponse> getConfirmPaymentMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ConfirmPayment",
      requestType = network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPaymentRequest.class,
      responseType = network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPaymentResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPaymentRequest,
      network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPaymentResponse> getConfirmPaymentMethod() {
    io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPaymentRequest, network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPaymentResponse> getConfirmPaymentMethod;
    if ((getConfirmPaymentMethod = RecipientServiceGrpc.getConfirmPaymentMethod) == null) {
      synchronized (RecipientServiceGrpc.class) {
        if ((getConfirmPaymentMethod = RecipientServiceGrpc.getConfirmPaymentMethod) == null) {
          RecipientServiceGrpc.getConfirmPaymentMethod = getConfirmPaymentMethod =
              io.grpc.MethodDescriptor.<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPaymentRequest, network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPaymentResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ConfirmPayment"))
              .setIdempotent(true)
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPaymentRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPaymentResponse.getDefaultInstance()))
              .setSchemaDescriptor(new RecipientServiceMethodDescriptorSupplier("ConfirmPayment"))
              .build();
        }
      }
    }
    return getConfirmPaymentMethod;
  }

  private static volatile io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.RejectPaymentIntentRequest,
      network.t0.sdk.proto.tzero.v1.payment_intent.recipient.RejectPaymentIntentResponse> getRejectPaymentIntentMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "RejectPaymentIntent",
      requestType = network.t0.sdk.proto.tzero.v1.payment_intent.recipient.RejectPaymentIntentRequest.class,
      responseType = network.t0.sdk.proto.tzero.v1.payment_intent.recipient.RejectPaymentIntentResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.RejectPaymentIntentRequest,
      network.t0.sdk.proto.tzero.v1.payment_intent.recipient.RejectPaymentIntentResponse> getRejectPaymentIntentMethod() {
    io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.RejectPaymentIntentRequest, network.t0.sdk.proto.tzero.v1.payment_intent.recipient.RejectPaymentIntentResponse> getRejectPaymentIntentMethod;
    if ((getRejectPaymentIntentMethod = RecipientServiceGrpc.getRejectPaymentIntentMethod) == null) {
      synchronized (RecipientServiceGrpc.class) {
        if ((getRejectPaymentIntentMethod = RecipientServiceGrpc.getRejectPaymentIntentMethod) == null) {
          RecipientServiceGrpc.getRejectPaymentIntentMethod = getRejectPaymentIntentMethod =
              io.grpc.MethodDescriptor.<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.RejectPaymentIntentRequest, network.t0.sdk.proto.tzero.v1.payment_intent.recipient.RejectPaymentIntentResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "RejectPaymentIntent"))
              .setIdempotent(true)
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment_intent.recipient.RejectPaymentIntentRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment_intent.recipient.RejectPaymentIntentResponse.getDefaultInstance()))
              .setSchemaDescriptor(new RecipientServiceMethodDescriptorSupplier("RejectPaymentIntent"))
              .build();
        }
      }
    }
    return getRejectPaymentIntentMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static RecipientServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<RecipientServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<RecipientServiceStub>() {
        @java.lang.Override
        public RecipientServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new RecipientServiceStub(channel, callOptions);
        }
      };
    return RecipientServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static RecipientServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<RecipientServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<RecipientServiceBlockingV2Stub>() {
        @java.lang.Override
        public RecipientServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new RecipientServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return RecipientServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static RecipientServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<RecipientServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<RecipientServiceBlockingStub>() {
        @java.lang.Override
        public RecipientServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new RecipientServiceBlockingStub(channel, callOptions);
        }
      };
    return RecipientServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static RecipientServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<RecipientServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<RecipientServiceFutureStub>() {
        @java.lang.Override
        public RecipientServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new RecipientServiceFutureStub(channel, callOptions);
        }
      };
    return RecipientServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   **
   * RecipientService is implemented by recipient in order to get updates on payment intents
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     **
     * notifies recipient that pay-in providers received payment from payer
     * </pre>
     */
    default void confirmPayIn(network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPayInRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPayInResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getConfirmPayInMethod(), responseObserver);
    }

    /**
     * <pre>
     **
     * notifies recipient about successful payment
     * </pre>
     */
    default void confirmPayment(network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPaymentRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPaymentResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getConfirmPaymentMethod(), responseObserver);
    }

    /**
     * <pre>
     **
     * notifies recipient about failed payment
     * </pre>
     */
    default void rejectPaymentIntent(network.t0.sdk.proto.tzero.v1.payment_intent.recipient.RejectPaymentIntentRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.RejectPaymentIntentResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRejectPaymentIntentMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service RecipientService.
   * <pre>
   **
   * RecipientService is implemented by recipient in order to get updates on payment intents
   * </pre>
   */
  public static abstract class RecipientServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return RecipientServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service RecipientService.
   * <pre>
   **
   * RecipientService is implemented by recipient in order to get updates on payment intents
   * </pre>
   */
  public static final class RecipientServiceStub
      extends io.grpc.stub.AbstractAsyncStub<RecipientServiceStub> {
    private RecipientServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected RecipientServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new RecipientServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     **
     * notifies recipient that pay-in providers received payment from payer
     * </pre>
     */
    public void confirmPayIn(network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPayInRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPayInResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getConfirmPayInMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     **
     * notifies recipient about successful payment
     * </pre>
     */
    public void confirmPayment(network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPaymentRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPaymentResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getConfirmPaymentMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     **
     * notifies recipient about failed payment
     * </pre>
     */
    public void rejectPaymentIntent(network.t0.sdk.proto.tzero.v1.payment_intent.recipient.RejectPaymentIntentRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.RejectPaymentIntentResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getRejectPaymentIntentMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service RecipientService.
   * <pre>
   **
   * RecipientService is implemented by recipient in order to get updates on payment intents
   * </pre>
   */
  public static final class RecipientServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<RecipientServiceBlockingV2Stub> {
    private RecipientServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected RecipientServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new RecipientServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     * <pre>
     **
     * notifies recipient that pay-in providers received payment from payer
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPayInResponse confirmPayIn(network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPayInRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getConfirmPayInMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     **
     * notifies recipient about successful payment
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPaymentResponse confirmPayment(network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPaymentRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getConfirmPaymentMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     **
     * notifies recipient about failed payment
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment_intent.recipient.RejectPaymentIntentResponse rejectPaymentIntent(network.t0.sdk.proto.tzero.v1.payment_intent.recipient.RejectPaymentIntentRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getRejectPaymentIntentMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service RecipientService.
   * <pre>
   **
   * RecipientService is implemented by recipient in order to get updates on payment intents
   * </pre>
   */
  public static final class RecipientServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<RecipientServiceBlockingStub> {
    private RecipientServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected RecipientServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new RecipientServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     **
     * notifies recipient that pay-in providers received payment from payer
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPayInResponse confirmPayIn(network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPayInRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getConfirmPayInMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     **
     * notifies recipient about successful payment
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPaymentResponse confirmPayment(network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPaymentRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getConfirmPaymentMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     **
     * notifies recipient about failed payment
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment_intent.recipient.RejectPaymentIntentResponse rejectPaymentIntent(network.t0.sdk.proto.tzero.v1.payment_intent.recipient.RejectPaymentIntentRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRejectPaymentIntentMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service RecipientService.
   * <pre>
   **
   * RecipientService is implemented by recipient in order to get updates on payment intents
   * </pre>
   */
  public static final class RecipientServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<RecipientServiceFutureStub> {
    private RecipientServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected RecipientServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new RecipientServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     **
     * notifies recipient that pay-in providers received payment from payer
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPayInResponse> confirmPayIn(
        network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPayInRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getConfirmPayInMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     **
     * notifies recipient about successful payment
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPaymentResponse> confirmPayment(
        network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPaymentRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getConfirmPaymentMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     **
     * notifies recipient about failed payment
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.RejectPaymentIntentResponse> rejectPaymentIntent(
        network.t0.sdk.proto.tzero.v1.payment_intent.recipient.RejectPaymentIntentRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getRejectPaymentIntentMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_CONFIRM_PAY_IN = 0;
  private static final int METHODID_CONFIRM_PAYMENT = 1;
  private static final int METHODID_REJECT_PAYMENT_INTENT = 2;

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
        case METHODID_CONFIRM_PAY_IN:
          serviceImpl.confirmPayIn((network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPayInRequest) request,
              (io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPayInResponse>) responseObserver);
          break;
        case METHODID_CONFIRM_PAYMENT:
          serviceImpl.confirmPayment((network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPaymentRequest) request,
              (io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPaymentResponse>) responseObserver);
          break;
        case METHODID_REJECT_PAYMENT_INTENT:
          serviceImpl.rejectPaymentIntent((network.t0.sdk.proto.tzero.v1.payment_intent.recipient.RejectPaymentIntentRequest) request,
              (io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.RejectPaymentIntentResponse>) responseObserver);
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
          getConfirmPayInMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPayInRequest,
              network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPayInResponse>(
                service, METHODID_CONFIRM_PAY_IN)))
        .addMethod(
          getConfirmPaymentMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPaymentRequest,
              network.t0.sdk.proto.tzero.v1.payment_intent.recipient.ConfirmPaymentResponse>(
                service, METHODID_CONFIRM_PAYMENT)))
        .addMethod(
          getRejectPaymentIntentMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              network.t0.sdk.proto.tzero.v1.payment_intent.recipient.RejectPaymentIntentRequest,
              network.t0.sdk.proto.tzero.v1.payment_intent.recipient.RejectPaymentIntentResponse>(
                service, METHODID_REJECT_PAYMENT_INTENT)))
        .build();
  }

  private static abstract class RecipientServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    RecipientServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return network.t0.sdk.proto.tzero.v1.payment_intent.recipient.RecipientProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("RecipientService");
    }
  }

  private static final class RecipientServiceFileDescriptorSupplier
      extends RecipientServiceBaseDescriptorSupplier {
    RecipientServiceFileDescriptorSupplier() {}
  }

  private static final class RecipientServiceMethodDescriptorSupplier
      extends RecipientServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    RecipientServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (RecipientServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new RecipientServiceFileDescriptorSupplier())
              .addMethod(getConfirmPayInMethod())
              .addMethod(getConfirmPaymentMethod())
              .addMethod(getRejectPaymentIntentMethod())
              .build();
        }
      }
    }
    return result;
  }
}
