package network.t0.sdk.proto.tzero.v1.payment_intent;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 **
 * PayInProviderService must be implemented by pay-in providers to participate
 * in the Payment Intent flow.
 * Pay-in providers are those who:
 * - Receive fiat payments from end-users
 * - Publish payment intent (pay-in) quotes to the network
 * - Confirm when payments are received via ConfirmFundsReceived
 * - Settles periodically with the beneficiary provider
 * The network calls this service to obtain payment details that will be
 * presented to end-users for making payments.
 * </pre>
 */
@io.grpc.stub.annotations.GrpcGenerated
public final class PayInProviderServiceGrpc {

  private PayInProviderServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "tzero.v1.payment_intent.PayInProviderService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.GetPaymentDetailsRequest,
      network.t0.sdk.proto.tzero.v1.payment_intent.GetPaymentDetailsResponse> getGetPaymentDetailsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetPaymentDetails",
      requestType = network.t0.sdk.proto.tzero.v1.payment_intent.GetPaymentDetailsRequest.class,
      responseType = network.t0.sdk.proto.tzero.v1.payment_intent.GetPaymentDetailsResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.GetPaymentDetailsRequest,
      network.t0.sdk.proto.tzero.v1.payment_intent.GetPaymentDetailsResponse> getGetPaymentDetailsMethod() {
    io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.GetPaymentDetailsRequest, network.t0.sdk.proto.tzero.v1.payment_intent.GetPaymentDetailsResponse> getGetPaymentDetailsMethod;
    if ((getGetPaymentDetailsMethod = PayInProviderServiceGrpc.getGetPaymentDetailsMethod) == null) {
      synchronized (PayInProviderServiceGrpc.class) {
        if ((getGetPaymentDetailsMethod = PayInProviderServiceGrpc.getGetPaymentDetailsMethod) == null) {
          PayInProviderServiceGrpc.getGetPaymentDetailsMethod = getGetPaymentDetailsMethod =
              io.grpc.MethodDescriptor.<network.t0.sdk.proto.tzero.v1.payment_intent.GetPaymentDetailsRequest, network.t0.sdk.proto.tzero.v1.payment_intent.GetPaymentDetailsResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetPaymentDetails"))
              .setIdempotent(true)
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment_intent.GetPaymentDetailsRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment_intent.GetPaymentDetailsResponse.getDefaultInstance()))
              .setSchemaDescriptor(new PayInProviderServiceMethodDescriptorSupplier("GetPaymentDetails"))
              .build();
        }
      }
    }
    return getGetPaymentDetailsMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static PayInProviderServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PayInProviderServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PayInProviderServiceStub>() {
        @java.lang.Override
        public PayInProviderServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PayInProviderServiceStub(channel, callOptions);
        }
      };
    return PayInProviderServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static PayInProviderServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PayInProviderServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PayInProviderServiceBlockingV2Stub>() {
        @java.lang.Override
        public PayInProviderServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PayInProviderServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return PayInProviderServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static PayInProviderServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PayInProviderServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PayInProviderServiceBlockingStub>() {
        @java.lang.Override
        public PayInProviderServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PayInProviderServiceBlockingStub(channel, callOptions);
        }
      };
    return PayInProviderServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static PayInProviderServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PayInProviderServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PayInProviderServiceFutureStub>() {
        @java.lang.Override
        public PayInProviderServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PayInProviderServiceFutureStub(channel, callOptions);
        }
      };
    return PayInProviderServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   **
   * PayInProviderService must be implemented by pay-in providers to participate
   * in the Payment Intent flow.
   * Pay-in providers are those who:
   * - Receive fiat payments from end-users
   * - Publish payment intent (pay-in) quotes to the network
   * - Confirm when payments are received via ConfirmFundsReceived
   * - Settles periodically with the beneficiary provider
   * The network calls this service to obtain payment details that will be
   * presented to end-users for making payments.
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     **
     * GetPaymentDetails returns payment details for the end-user.
     * Called by the network during CreatePaymentIntent processing.
     * The provider should return payment details (bank accounts, mobile money info, etc.)
     * that the end-user can use to send funds. The payment details should contain payment reference,
     * so that on receiving payment from a payer, the pay-in provider can identify which payment intent this payment belongs to
     * </pre>
     */
    default void getPaymentDetails(network.t0.sdk.proto.tzero.v1.payment_intent.GetPaymentDetailsRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.GetPaymentDetailsResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetPaymentDetailsMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service PayInProviderService.
   * <pre>
   **
   * PayInProviderService must be implemented by pay-in providers to participate
   * in the Payment Intent flow.
   * Pay-in providers are those who:
   * - Receive fiat payments from end-users
   * - Publish payment intent (pay-in) quotes to the network
   * - Confirm when payments are received via ConfirmFundsReceived
   * - Settles periodically with the beneficiary provider
   * The network calls this service to obtain payment details that will be
   * presented to end-users for making payments.
   * </pre>
   */
  public static abstract class PayInProviderServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return PayInProviderServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service PayInProviderService.
   * <pre>
   **
   * PayInProviderService must be implemented by pay-in providers to participate
   * in the Payment Intent flow.
   * Pay-in providers are those who:
   * - Receive fiat payments from end-users
   * - Publish payment intent (pay-in) quotes to the network
   * - Confirm when payments are received via ConfirmFundsReceived
   * - Settles periodically with the beneficiary provider
   * The network calls this service to obtain payment details that will be
   * presented to end-users for making payments.
   * </pre>
   */
  public static final class PayInProviderServiceStub
      extends io.grpc.stub.AbstractAsyncStub<PayInProviderServiceStub> {
    private PayInProviderServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PayInProviderServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PayInProviderServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     **
     * GetPaymentDetails returns payment details for the end-user.
     * Called by the network during CreatePaymentIntent processing.
     * The provider should return payment details (bank accounts, mobile money info, etc.)
     * that the end-user can use to send funds. The payment details should contain payment reference,
     * so that on receiving payment from a payer, the pay-in provider can identify which payment intent this payment belongs to
     * </pre>
     */
    public void getPaymentDetails(network.t0.sdk.proto.tzero.v1.payment_intent.GetPaymentDetailsRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.GetPaymentDetailsResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetPaymentDetailsMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service PayInProviderService.
   * <pre>
   **
   * PayInProviderService must be implemented by pay-in providers to participate
   * in the Payment Intent flow.
   * Pay-in providers are those who:
   * - Receive fiat payments from end-users
   * - Publish payment intent (pay-in) quotes to the network
   * - Confirm when payments are received via ConfirmFundsReceived
   * - Settles periodically with the beneficiary provider
   * The network calls this service to obtain payment details that will be
   * presented to end-users for making payments.
   * </pre>
   */
  public static final class PayInProviderServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<PayInProviderServiceBlockingV2Stub> {
    private PayInProviderServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PayInProviderServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PayInProviderServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     * <pre>
     **
     * GetPaymentDetails returns payment details for the end-user.
     * Called by the network during CreatePaymentIntent processing.
     * The provider should return payment details (bank accounts, mobile money info, etc.)
     * that the end-user can use to send funds. The payment details should contain payment reference,
     * so that on receiving payment from a payer, the pay-in provider can identify which payment intent this payment belongs to
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment_intent.GetPaymentDetailsResponse getPaymentDetails(network.t0.sdk.proto.tzero.v1.payment_intent.GetPaymentDetailsRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getGetPaymentDetailsMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service PayInProviderService.
   * <pre>
   **
   * PayInProviderService must be implemented by pay-in providers to participate
   * in the Payment Intent flow.
   * Pay-in providers are those who:
   * - Receive fiat payments from end-users
   * - Publish payment intent (pay-in) quotes to the network
   * - Confirm when payments are received via ConfirmFundsReceived
   * - Settles periodically with the beneficiary provider
   * The network calls this service to obtain payment details that will be
   * presented to end-users for making payments.
   * </pre>
   */
  public static final class PayInProviderServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<PayInProviderServiceBlockingStub> {
    private PayInProviderServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PayInProviderServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PayInProviderServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     **
     * GetPaymentDetails returns payment details for the end-user.
     * Called by the network during CreatePaymentIntent processing.
     * The provider should return payment details (bank accounts, mobile money info, etc.)
     * that the end-user can use to send funds. The payment details should contain payment reference,
     * so that on receiving payment from a payer, the pay-in provider can identify which payment intent this payment belongs to
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment_intent.GetPaymentDetailsResponse getPaymentDetails(network.t0.sdk.proto.tzero.v1.payment_intent.GetPaymentDetailsRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetPaymentDetailsMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service PayInProviderService.
   * <pre>
   **
   * PayInProviderService must be implemented by pay-in providers to participate
   * in the Payment Intent flow.
   * Pay-in providers are those who:
   * - Receive fiat payments from end-users
   * - Publish payment intent (pay-in) quotes to the network
   * - Confirm when payments are received via ConfirmFundsReceived
   * - Settles periodically with the beneficiary provider
   * The network calls this service to obtain payment details that will be
   * presented to end-users for making payments.
   * </pre>
   */
  public static final class PayInProviderServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<PayInProviderServiceFutureStub> {
    private PayInProviderServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PayInProviderServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PayInProviderServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     **
     * GetPaymentDetails returns payment details for the end-user.
     * Called by the network during CreatePaymentIntent processing.
     * The provider should return payment details (bank accounts, mobile money info, etc.)
     * that the end-user can use to send funds. The payment details should contain payment reference,
     * so that on receiving payment from a payer, the pay-in provider can identify which payment intent this payment belongs to
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<network.t0.sdk.proto.tzero.v1.payment_intent.GetPaymentDetailsResponse> getPaymentDetails(
        network.t0.sdk.proto.tzero.v1.payment_intent.GetPaymentDetailsRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetPaymentDetailsMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_GET_PAYMENT_DETAILS = 0;

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
        case METHODID_GET_PAYMENT_DETAILS:
          serviceImpl.getPaymentDetails((network.t0.sdk.proto.tzero.v1.payment_intent.GetPaymentDetailsRequest) request,
              (io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.GetPaymentDetailsResponse>) responseObserver);
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
          getGetPaymentDetailsMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              network.t0.sdk.proto.tzero.v1.payment_intent.GetPaymentDetailsRequest,
              network.t0.sdk.proto.tzero.v1.payment_intent.GetPaymentDetailsResponse>(
                service, METHODID_GET_PAYMENT_DETAILS)))
        .build();
  }

  private static abstract class PayInProviderServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    PayInProviderServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return network.t0.sdk.proto.tzero.v1.payment_intent.PayInProviderProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("PayInProviderService");
    }
  }

  private static final class PayInProviderServiceFileDescriptorSupplier
      extends PayInProviderServiceBaseDescriptorSupplier {
    PayInProviderServiceFileDescriptorSupplier() {}
  }

  private static final class PayInProviderServiceMethodDescriptorSupplier
      extends PayInProviderServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    PayInProviderServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (PayInProviderServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new PayInProviderServiceFileDescriptorSupplier())
              .addMethod(getGetPaymentDetailsMethod())
              .build();
        }
      }
    }
    return result;
  }
}
