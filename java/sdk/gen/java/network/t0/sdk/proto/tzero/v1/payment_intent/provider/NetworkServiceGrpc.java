package network.t0.sdk.proto.tzero.v1.payment_intent.provider;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 **
 * NetworkService is used by provider in order to notify network on payment intent updates
 * </pre>
 */
@io.grpc.stub.annotations.GrpcGenerated
public final class NetworkServiceGrpc {

  private NetworkServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "tzero.v1.payment_intent.provider.NetworkService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPaymentRequest,
      network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPaymentResponse> getConfirmPaymentMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ConfirmPayment",
      requestType = network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPaymentRequest.class,
      responseType = network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPaymentResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPaymentRequest,
      network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPaymentResponse> getConfirmPaymentMethod() {
    io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPaymentRequest, network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPaymentResponse> getConfirmPaymentMethod;
    if ((getConfirmPaymentMethod = NetworkServiceGrpc.getConfirmPaymentMethod) == null) {
      synchronized (NetworkServiceGrpc.class) {
        if ((getConfirmPaymentMethod = NetworkServiceGrpc.getConfirmPaymentMethod) == null) {
          NetworkServiceGrpc.getConfirmPaymentMethod = getConfirmPaymentMethod =
              io.grpc.MethodDescriptor.<network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPaymentRequest, network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPaymentResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ConfirmPayment"))
              .setIdempotent(true)
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPaymentRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPaymentResponse.getDefaultInstance()))
              .setSchemaDescriptor(new NetworkServiceMethodDescriptorSupplier("ConfirmPayment"))
              .build();
        }
      }
    }
    return getConfirmPaymentMethod;
  }

  private static volatile io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.provider.RejectPaymentIntentRequest,
      network.t0.sdk.proto.tzero.v1.payment_intent.provider.RejectPaymentIntentResponse> getRejectPaymentIntentMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "RejectPaymentIntent",
      requestType = network.t0.sdk.proto.tzero.v1.payment_intent.provider.RejectPaymentIntentRequest.class,
      responseType = network.t0.sdk.proto.tzero.v1.payment_intent.provider.RejectPaymentIntentResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.provider.RejectPaymentIntentRequest,
      network.t0.sdk.proto.tzero.v1.payment_intent.provider.RejectPaymentIntentResponse> getRejectPaymentIntentMethod() {
    io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.provider.RejectPaymentIntentRequest, network.t0.sdk.proto.tzero.v1.payment_intent.provider.RejectPaymentIntentResponse> getRejectPaymentIntentMethod;
    if ((getRejectPaymentIntentMethod = NetworkServiceGrpc.getRejectPaymentIntentMethod) == null) {
      synchronized (NetworkServiceGrpc.class) {
        if ((getRejectPaymentIntentMethod = NetworkServiceGrpc.getRejectPaymentIntentMethod) == null) {
          NetworkServiceGrpc.getRejectPaymentIntentMethod = getRejectPaymentIntentMethod =
              io.grpc.MethodDescriptor.<network.t0.sdk.proto.tzero.v1.payment_intent.provider.RejectPaymentIntentRequest, network.t0.sdk.proto.tzero.v1.payment_intent.provider.RejectPaymentIntentResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "RejectPaymentIntent"))
              .setIdempotent(true)
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment_intent.provider.RejectPaymentIntentRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment_intent.provider.RejectPaymentIntentResponse.getDefaultInstance()))
              .setSchemaDescriptor(new NetworkServiceMethodDescriptorSupplier("RejectPaymentIntent"))
              .build();
        }
      }
    }
    return getRejectPaymentIntentMethod;
  }

  private static volatile io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmSettlementRequest,
      network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmSettlementResponse> getConfirmSettlementMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ConfirmSettlement",
      requestType = network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmSettlementRequest.class,
      responseType = network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmSettlementResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmSettlementRequest,
      network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmSettlementResponse> getConfirmSettlementMethod() {
    io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmSettlementRequest, network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmSettlementResponse> getConfirmSettlementMethod;
    if ((getConfirmSettlementMethod = NetworkServiceGrpc.getConfirmSettlementMethod) == null) {
      synchronized (NetworkServiceGrpc.class) {
        if ((getConfirmSettlementMethod = NetworkServiceGrpc.getConfirmSettlementMethod) == null) {
          NetworkServiceGrpc.getConfirmSettlementMethod = getConfirmSettlementMethod =
              io.grpc.MethodDescriptor.<network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmSettlementRequest, network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmSettlementResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ConfirmSettlement"))
              .setIdempotent(true)
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmSettlementRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmSettlementResponse.getDefaultInstance()))
              .setSchemaDescriptor(new NetworkServiceMethodDescriptorSupplier("ConfirmSettlement"))
              .build();
        }
      }
    }
    return getConfirmSettlementMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static NetworkServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<NetworkServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<NetworkServiceStub>() {
        @java.lang.Override
        public NetworkServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new NetworkServiceStub(channel, callOptions);
        }
      };
    return NetworkServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static NetworkServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<NetworkServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<NetworkServiceBlockingV2Stub>() {
        @java.lang.Override
        public NetworkServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new NetworkServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return NetworkServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static NetworkServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<NetworkServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<NetworkServiceBlockingStub>() {
        @java.lang.Override
        public NetworkServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new NetworkServiceBlockingStub(channel, callOptions);
        }
      };
    return NetworkServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static NetworkServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<NetworkServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<NetworkServiceFutureStub>() {
        @java.lang.Override
        public NetworkServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new NetworkServiceFutureStub(channel, callOptions);
        }
      };
    return NetworkServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   **
   * NetworkService is used by provider in order to notify network on payment intent updates
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     **
     * Notify network about a successful payment for the corresponding payment intent
     * </pre>
     */
    default void confirmPayment(network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPaymentRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPaymentResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getConfirmPaymentMethod(), responseObserver);
    }

    /**
     * <pre>
     **
     * Notify network about a payment failure for the corresponding payment intent
     * </pre>
     */
    default void rejectPaymentIntent(network.t0.sdk.proto.tzero.v1.payment_intent.provider.RejectPaymentIntentRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.provider.RejectPaymentIntentResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRejectPaymentIntentMethod(), responseObserver);
    }

    /**
     * <pre>
     **
     * Notify network about relation between payment intent and settlement transaction.
     * This method is not essential but helps to keep track of payment flow
     * </pre>
     */
    default void confirmSettlement(network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmSettlementRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmSettlementResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getConfirmSettlementMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service NetworkService.
   * <pre>
   **
   * NetworkService is used by provider in order to notify network on payment intent updates
   * </pre>
   */
  public static abstract class NetworkServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return NetworkServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service NetworkService.
   * <pre>
   **
   * NetworkService is used by provider in order to notify network on payment intent updates
   * </pre>
   */
  public static final class NetworkServiceStub
      extends io.grpc.stub.AbstractAsyncStub<NetworkServiceStub> {
    private NetworkServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected NetworkServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new NetworkServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     **
     * Notify network about a successful payment for the corresponding payment intent
     * </pre>
     */
    public void confirmPayment(network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPaymentRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPaymentResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getConfirmPaymentMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     **
     * Notify network about a payment failure for the corresponding payment intent
     * </pre>
     */
    public void rejectPaymentIntent(network.t0.sdk.proto.tzero.v1.payment_intent.provider.RejectPaymentIntentRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.provider.RejectPaymentIntentResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getRejectPaymentIntentMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     **
     * Notify network about relation between payment intent and settlement transaction.
     * This method is not essential but helps to keep track of payment flow
     * </pre>
     */
    public void confirmSettlement(network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmSettlementRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmSettlementResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getConfirmSettlementMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service NetworkService.
   * <pre>
   **
   * NetworkService is used by provider in order to notify network on payment intent updates
   * </pre>
   */
  public static final class NetworkServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<NetworkServiceBlockingV2Stub> {
    private NetworkServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected NetworkServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new NetworkServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     * <pre>
     **
     * Notify network about a successful payment for the corresponding payment intent
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPaymentResponse confirmPayment(network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPaymentRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getConfirmPaymentMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     **
     * Notify network about a payment failure for the corresponding payment intent
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment_intent.provider.RejectPaymentIntentResponse rejectPaymentIntent(network.t0.sdk.proto.tzero.v1.payment_intent.provider.RejectPaymentIntentRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getRejectPaymentIntentMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     **
     * Notify network about relation between payment intent and settlement transaction.
     * This method is not essential but helps to keep track of payment flow
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmSettlementResponse confirmSettlement(network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmSettlementRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getConfirmSettlementMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service NetworkService.
   * <pre>
   **
   * NetworkService is used by provider in order to notify network on payment intent updates
   * </pre>
   */
  public static final class NetworkServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<NetworkServiceBlockingStub> {
    private NetworkServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected NetworkServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new NetworkServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     **
     * Notify network about a successful payment for the corresponding payment intent
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPaymentResponse confirmPayment(network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPaymentRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getConfirmPaymentMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     **
     * Notify network about a payment failure for the corresponding payment intent
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment_intent.provider.RejectPaymentIntentResponse rejectPaymentIntent(network.t0.sdk.proto.tzero.v1.payment_intent.provider.RejectPaymentIntentRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRejectPaymentIntentMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     **
     * Notify network about relation between payment intent and settlement transaction.
     * This method is not essential but helps to keep track of payment flow
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmSettlementResponse confirmSettlement(network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmSettlementRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getConfirmSettlementMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service NetworkService.
   * <pre>
   **
   * NetworkService is used by provider in order to notify network on payment intent updates
   * </pre>
   */
  public static final class NetworkServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<NetworkServiceFutureStub> {
    private NetworkServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected NetworkServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new NetworkServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     **
     * Notify network about a successful payment for the corresponding payment intent
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPaymentResponse> confirmPayment(
        network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPaymentRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getConfirmPaymentMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     **
     * Notify network about a payment failure for the corresponding payment intent
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<network.t0.sdk.proto.tzero.v1.payment_intent.provider.RejectPaymentIntentResponse> rejectPaymentIntent(
        network.t0.sdk.proto.tzero.v1.payment_intent.provider.RejectPaymentIntentRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getRejectPaymentIntentMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     **
     * Notify network about relation between payment intent and settlement transaction.
     * This method is not essential but helps to keep track of payment flow
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmSettlementResponse> confirmSettlement(
        network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmSettlementRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getConfirmSettlementMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_CONFIRM_PAYMENT = 0;
  private static final int METHODID_REJECT_PAYMENT_INTENT = 1;
  private static final int METHODID_CONFIRM_SETTLEMENT = 2;

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
        case METHODID_CONFIRM_PAYMENT:
          serviceImpl.confirmPayment((network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPaymentRequest) request,
              (io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPaymentResponse>) responseObserver);
          break;
        case METHODID_REJECT_PAYMENT_INTENT:
          serviceImpl.rejectPaymentIntent((network.t0.sdk.proto.tzero.v1.payment_intent.provider.RejectPaymentIntentRequest) request,
              (io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.provider.RejectPaymentIntentResponse>) responseObserver);
          break;
        case METHODID_CONFIRM_SETTLEMENT:
          serviceImpl.confirmSettlement((network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmSettlementRequest) request,
              (io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmSettlementResponse>) responseObserver);
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
          getConfirmPaymentMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPaymentRequest,
              network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmPaymentResponse>(
                service, METHODID_CONFIRM_PAYMENT)))
        .addMethod(
          getRejectPaymentIntentMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              network.t0.sdk.proto.tzero.v1.payment_intent.provider.RejectPaymentIntentRequest,
              network.t0.sdk.proto.tzero.v1.payment_intent.provider.RejectPaymentIntentResponse>(
                service, METHODID_REJECT_PAYMENT_INTENT)))
        .addMethod(
          getConfirmSettlementMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmSettlementRequest,
              network.t0.sdk.proto.tzero.v1.payment_intent.provider.ConfirmSettlementResponse>(
                service, METHODID_CONFIRM_SETTLEMENT)))
        .build();
  }

  private static abstract class NetworkServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    NetworkServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return network.t0.sdk.proto.tzero.v1.payment_intent.provider.ProviderProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("NetworkService");
    }
  }

  private static final class NetworkServiceFileDescriptorSupplier
      extends NetworkServiceBaseDescriptorSupplier {
    NetworkServiceFileDescriptorSupplier() {}
  }

  private static final class NetworkServiceMethodDescriptorSupplier
      extends NetworkServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    NetworkServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (NetworkServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new NetworkServiceFileDescriptorSupplier())
              .addMethod(getConfirmPaymentMethod())
              .addMethod(getRejectPaymentIntentMethod())
              .addMethod(getConfirmSettlementMethod())
              .build();
        }
      }
    }
    return result;
  }
}
