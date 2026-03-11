package network.t0.sdk.proto.tzero.v1.payment_intent.recipient;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 **
 * NetworkService is used by recipient to create a payment intents
 * </pre>
 */
@io.grpc.stub.annotations.GrpcGenerated
public final class NetworkServiceGrpc {

  private NetworkServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "tzero.v1.payment_intent.recipient.NetworkService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.CreatePaymentIntentRequest,
      network.t0.sdk.proto.tzero.v1.payment_intent.recipient.CreatePaymentIntentResponse> getCreatePaymentIntentMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "CreatePaymentIntent",
      requestType = network.t0.sdk.proto.tzero.v1.payment_intent.recipient.CreatePaymentIntentRequest.class,
      responseType = network.t0.sdk.proto.tzero.v1.payment_intent.recipient.CreatePaymentIntentResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.CreatePaymentIntentRequest,
      network.t0.sdk.proto.tzero.v1.payment_intent.recipient.CreatePaymentIntentResponse> getCreatePaymentIntentMethod() {
    io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.CreatePaymentIntentRequest, network.t0.sdk.proto.tzero.v1.payment_intent.recipient.CreatePaymentIntentResponse> getCreatePaymentIntentMethod;
    if ((getCreatePaymentIntentMethod = NetworkServiceGrpc.getCreatePaymentIntentMethod) == null) {
      synchronized (NetworkServiceGrpc.class) {
        if ((getCreatePaymentIntentMethod = NetworkServiceGrpc.getCreatePaymentIntentMethod) == null) {
          NetworkServiceGrpc.getCreatePaymentIntentMethod = getCreatePaymentIntentMethod =
              io.grpc.MethodDescriptor.<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.CreatePaymentIntentRequest, network.t0.sdk.proto.tzero.v1.payment_intent.recipient.CreatePaymentIntentResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "CreatePaymentIntent"))
              .setIdempotent(true)
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment_intent.recipient.CreatePaymentIntentRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment_intent.recipient.CreatePaymentIntentResponse.getDefaultInstance()))
              .setSchemaDescriptor(new NetworkServiceMethodDescriptorSupplier("CreatePaymentIntent"))
              .build();
        }
      }
    }
    return getCreatePaymentIntentMethod;
  }

  private static volatile io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.GetQuoteRequest,
      network.t0.sdk.proto.tzero.v1.payment_intent.recipient.GetQuoteResponse> getGetQuoteMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetQuote",
      requestType = network.t0.sdk.proto.tzero.v1.payment_intent.recipient.GetQuoteRequest.class,
      responseType = network.t0.sdk.proto.tzero.v1.payment_intent.recipient.GetQuoteResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.GetQuoteRequest,
      network.t0.sdk.proto.tzero.v1.payment_intent.recipient.GetQuoteResponse> getGetQuoteMethod() {
    io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.GetQuoteRequest, network.t0.sdk.proto.tzero.v1.payment_intent.recipient.GetQuoteResponse> getGetQuoteMethod;
    if ((getGetQuoteMethod = NetworkServiceGrpc.getGetQuoteMethod) == null) {
      synchronized (NetworkServiceGrpc.class) {
        if ((getGetQuoteMethod = NetworkServiceGrpc.getGetQuoteMethod) == null) {
          NetworkServiceGrpc.getGetQuoteMethod = getGetQuoteMethod =
              io.grpc.MethodDescriptor.<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.GetQuoteRequest, network.t0.sdk.proto.tzero.v1.payment_intent.recipient.GetQuoteResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetQuote"))
              .setSafe(true)
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment_intent.recipient.GetQuoteRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment_intent.recipient.GetQuoteResponse.getDefaultInstance()))
              .setSchemaDescriptor(new NetworkServiceMethodDescriptorSupplier("GetQuote"))
              .build();
        }
      }
    }
    return getGetQuoteMethod;
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
   * NetworkService is used by recipient to create a payment intents
   * </pre>
   */
  public interface AsyncService {

    /**
     */
    default void createPaymentIntent(network.t0.sdk.proto.tzero.v1.payment_intent.recipient.CreatePaymentIntentRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.CreatePaymentIntentResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getCreatePaymentIntentMethod(), responseObserver);
    }

    /**
     */
    default void getQuote(network.t0.sdk.proto.tzero.v1.payment_intent.recipient.GetQuoteRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.GetQuoteResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetQuoteMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service NetworkService.
   * <pre>
   **
   * NetworkService is used by recipient to create a payment intents
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
   * NetworkService is used by recipient to create a payment intents
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
     */
    public void createPaymentIntent(network.t0.sdk.proto.tzero.v1.payment_intent.recipient.CreatePaymentIntentRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.CreatePaymentIntentResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getCreatePaymentIntentMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void getQuote(network.t0.sdk.proto.tzero.v1.payment_intent.recipient.GetQuoteRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.GetQuoteResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetQuoteMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service NetworkService.
   * <pre>
   **
   * NetworkService is used by recipient to create a payment intents
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
     */
    public network.t0.sdk.proto.tzero.v1.payment_intent.recipient.CreatePaymentIntentResponse createPaymentIntent(network.t0.sdk.proto.tzero.v1.payment_intent.recipient.CreatePaymentIntentRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getCreatePaymentIntentMethod(), getCallOptions(), request);
    }

    /**
     */
    public network.t0.sdk.proto.tzero.v1.payment_intent.recipient.GetQuoteResponse getQuote(network.t0.sdk.proto.tzero.v1.payment_intent.recipient.GetQuoteRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getGetQuoteMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service NetworkService.
   * <pre>
   **
   * NetworkService is used by recipient to create a payment intents
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
     */
    public network.t0.sdk.proto.tzero.v1.payment_intent.recipient.CreatePaymentIntentResponse createPaymentIntent(network.t0.sdk.proto.tzero.v1.payment_intent.recipient.CreatePaymentIntentRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCreatePaymentIntentMethod(), getCallOptions(), request);
    }

    /**
     */
    public network.t0.sdk.proto.tzero.v1.payment_intent.recipient.GetQuoteResponse getQuote(network.t0.sdk.proto.tzero.v1.payment_intent.recipient.GetQuoteRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetQuoteMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service NetworkService.
   * <pre>
   **
   * NetworkService is used by recipient to create a payment intents
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
     */
    public com.google.common.util.concurrent.ListenableFuture<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.CreatePaymentIntentResponse> createPaymentIntent(
        network.t0.sdk.proto.tzero.v1.payment_intent.recipient.CreatePaymentIntentRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getCreatePaymentIntentMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.GetQuoteResponse> getQuote(
        network.t0.sdk.proto.tzero.v1.payment_intent.recipient.GetQuoteRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetQuoteMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_CREATE_PAYMENT_INTENT = 0;
  private static final int METHODID_GET_QUOTE = 1;

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
          serviceImpl.createPaymentIntent((network.t0.sdk.proto.tzero.v1.payment_intent.recipient.CreatePaymentIntentRequest) request,
              (io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.CreatePaymentIntentResponse>) responseObserver);
          break;
        case METHODID_GET_QUOTE:
          serviceImpl.getQuote((network.t0.sdk.proto.tzero.v1.payment_intent.recipient.GetQuoteRequest) request,
              (io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.recipient.GetQuoteResponse>) responseObserver);
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
              network.t0.sdk.proto.tzero.v1.payment_intent.recipient.CreatePaymentIntentRequest,
              network.t0.sdk.proto.tzero.v1.payment_intent.recipient.CreatePaymentIntentResponse>(
                service, METHODID_CREATE_PAYMENT_INTENT)))
        .addMethod(
          getGetQuoteMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              network.t0.sdk.proto.tzero.v1.payment_intent.recipient.GetQuoteRequest,
              network.t0.sdk.proto.tzero.v1.payment_intent.recipient.GetQuoteResponse>(
                service, METHODID_GET_QUOTE)))
        .build();
  }

  private static abstract class NetworkServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    NetworkServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return network.t0.sdk.proto.tzero.v1.payment_intent.recipient.RecipientProto.getDescriptor();
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
              .addMethod(getCreatePaymentIntentMethod())
              .addMethod(getGetQuoteMethod())
              .build();
        }
      }
    }
    return result;
  }
}
