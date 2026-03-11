package network.t0.sdk.proto.tzero.v1.payment_intent;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 **
 * PaymentIntentService provides Payment Intent APIs for providers.
 * Payment Intent is a flow where:
 * 1. Beneficiary provider creates a payment intent specifying amount/currency
 * 2. End-user pays via one of the returned payment options
 * 3. Pay-in provider confirms funds received
 * 4. Settlement will happen periodically between providers
 * This service is hosted by the T-0 Network and called by providers.
 * </pre>
 */
@io.grpc.stub.annotations.GrpcGenerated
public final class PaymentIntentServiceGrpc {

  private PaymentIntentServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "tzero.v1.payment_intent.PaymentIntentService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.UpdateQuoteRequest,
      network.t0.sdk.proto.tzero.v1.payment_intent.UpdateQuoteResponse> getUpdateQuoteMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "UpdateQuote",
      requestType = network.t0.sdk.proto.tzero.v1.payment_intent.UpdateQuoteRequest.class,
      responseType = network.t0.sdk.proto.tzero.v1.payment_intent.UpdateQuoteResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.UpdateQuoteRequest,
      network.t0.sdk.proto.tzero.v1.payment_intent.UpdateQuoteResponse> getUpdateQuoteMethod() {
    io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.UpdateQuoteRequest, network.t0.sdk.proto.tzero.v1.payment_intent.UpdateQuoteResponse> getUpdateQuoteMethod;
    if ((getUpdateQuoteMethod = PaymentIntentServiceGrpc.getUpdateQuoteMethod) == null) {
      synchronized (PaymentIntentServiceGrpc.class) {
        if ((getUpdateQuoteMethod = PaymentIntentServiceGrpc.getUpdateQuoteMethod) == null) {
          PaymentIntentServiceGrpc.getUpdateQuoteMethod = getUpdateQuoteMethod =
              io.grpc.MethodDescriptor.<network.t0.sdk.proto.tzero.v1.payment_intent.UpdateQuoteRequest, network.t0.sdk.proto.tzero.v1.payment_intent.UpdateQuoteResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "UpdateQuote"))
              .setIdempotent(true)
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment_intent.UpdateQuoteRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment_intent.UpdateQuoteResponse.getDefaultInstance()))
              .setSchemaDescriptor(new PaymentIntentServiceMethodDescriptorSupplier("UpdateQuote"))
              .build();
        }
      }
    }
    return getUpdateQuoteMethod;
  }

  private static volatile io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.GetQuoteRequest,
      network.t0.sdk.proto.tzero.v1.payment_intent.GetQuoteResponse> getGetQuoteMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetQuote",
      requestType = network.t0.sdk.proto.tzero.v1.payment_intent.GetQuoteRequest.class,
      responseType = network.t0.sdk.proto.tzero.v1.payment_intent.GetQuoteResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.GetQuoteRequest,
      network.t0.sdk.proto.tzero.v1.payment_intent.GetQuoteResponse> getGetQuoteMethod() {
    io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.GetQuoteRequest, network.t0.sdk.proto.tzero.v1.payment_intent.GetQuoteResponse> getGetQuoteMethod;
    if ((getGetQuoteMethod = PaymentIntentServiceGrpc.getGetQuoteMethod) == null) {
      synchronized (PaymentIntentServiceGrpc.class) {
        if ((getGetQuoteMethod = PaymentIntentServiceGrpc.getGetQuoteMethod) == null) {
          PaymentIntentServiceGrpc.getGetQuoteMethod = getGetQuoteMethod =
              io.grpc.MethodDescriptor.<network.t0.sdk.proto.tzero.v1.payment_intent.GetQuoteRequest, network.t0.sdk.proto.tzero.v1.payment_intent.GetQuoteResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetQuote"))
              .setIdempotent(true)
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment_intent.GetQuoteRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment_intent.GetQuoteResponse.getDefaultInstance()))
              .setSchemaDescriptor(new PaymentIntentServiceMethodDescriptorSupplier("GetQuote"))
              .build();
        }
      }
    }
    return getGetQuoteMethod;
  }

  private static volatile io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.CreatePaymentIntentRequest,
      network.t0.sdk.proto.tzero.v1.payment_intent.CreatePaymentIntentResponse> getCreatePaymentIntentMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "CreatePaymentIntent",
      requestType = network.t0.sdk.proto.tzero.v1.payment_intent.CreatePaymentIntentRequest.class,
      responseType = network.t0.sdk.proto.tzero.v1.payment_intent.CreatePaymentIntentResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.CreatePaymentIntentRequest,
      network.t0.sdk.proto.tzero.v1.payment_intent.CreatePaymentIntentResponse> getCreatePaymentIntentMethod() {
    io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.CreatePaymentIntentRequest, network.t0.sdk.proto.tzero.v1.payment_intent.CreatePaymentIntentResponse> getCreatePaymentIntentMethod;
    if ((getCreatePaymentIntentMethod = PaymentIntentServiceGrpc.getCreatePaymentIntentMethod) == null) {
      synchronized (PaymentIntentServiceGrpc.class) {
        if ((getCreatePaymentIntentMethod = PaymentIntentServiceGrpc.getCreatePaymentIntentMethod) == null) {
          PaymentIntentServiceGrpc.getCreatePaymentIntentMethod = getCreatePaymentIntentMethod =
              io.grpc.MethodDescriptor.<network.t0.sdk.proto.tzero.v1.payment_intent.CreatePaymentIntentRequest, network.t0.sdk.proto.tzero.v1.payment_intent.CreatePaymentIntentResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "CreatePaymentIntent"))
              .setIdempotent(true)
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment_intent.CreatePaymentIntentRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment_intent.CreatePaymentIntentResponse.getDefaultInstance()))
              .setSchemaDescriptor(new PaymentIntentServiceMethodDescriptorSupplier("CreatePaymentIntent"))
              .build();
        }
      }
    }
    return getCreatePaymentIntentMethod;
  }

  private static volatile io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.ConfirmFundsReceivedRequest,
      network.t0.sdk.proto.tzero.v1.payment_intent.ConfirmFundsReceivedResponse> getConfirmFundsReceivedMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ConfirmFundsReceived",
      requestType = network.t0.sdk.proto.tzero.v1.payment_intent.ConfirmFundsReceivedRequest.class,
      responseType = network.t0.sdk.proto.tzero.v1.payment_intent.ConfirmFundsReceivedResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.ConfirmFundsReceivedRequest,
      network.t0.sdk.proto.tzero.v1.payment_intent.ConfirmFundsReceivedResponse> getConfirmFundsReceivedMethod() {
    io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment_intent.ConfirmFundsReceivedRequest, network.t0.sdk.proto.tzero.v1.payment_intent.ConfirmFundsReceivedResponse> getConfirmFundsReceivedMethod;
    if ((getConfirmFundsReceivedMethod = PaymentIntentServiceGrpc.getConfirmFundsReceivedMethod) == null) {
      synchronized (PaymentIntentServiceGrpc.class) {
        if ((getConfirmFundsReceivedMethod = PaymentIntentServiceGrpc.getConfirmFundsReceivedMethod) == null) {
          PaymentIntentServiceGrpc.getConfirmFundsReceivedMethod = getConfirmFundsReceivedMethod =
              io.grpc.MethodDescriptor.<network.t0.sdk.proto.tzero.v1.payment_intent.ConfirmFundsReceivedRequest, network.t0.sdk.proto.tzero.v1.payment_intent.ConfirmFundsReceivedResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ConfirmFundsReceived"))
              .setIdempotent(true)
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment_intent.ConfirmFundsReceivedRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment_intent.ConfirmFundsReceivedResponse.getDefaultInstance()))
              .setSchemaDescriptor(new PaymentIntentServiceMethodDescriptorSupplier("ConfirmFundsReceived"))
              .build();
        }
      }
    }
    return getConfirmFundsReceivedMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static PaymentIntentServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PaymentIntentServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PaymentIntentServiceStub>() {
        @java.lang.Override
        public PaymentIntentServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PaymentIntentServiceStub(channel, callOptions);
        }
      };
    return PaymentIntentServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static PaymentIntentServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PaymentIntentServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PaymentIntentServiceBlockingV2Stub>() {
        @java.lang.Override
        public PaymentIntentServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PaymentIntentServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return PaymentIntentServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static PaymentIntentServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PaymentIntentServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PaymentIntentServiceBlockingStub>() {
        @java.lang.Override
        public PaymentIntentServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PaymentIntentServiceBlockingStub(channel, callOptions);
        }
      };
    return PaymentIntentServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static PaymentIntentServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PaymentIntentServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PaymentIntentServiceFutureStub>() {
        @java.lang.Override
        public PaymentIntentServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PaymentIntentServiceFutureStub(channel, callOptions);
        }
      };
    return PaymentIntentServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   **
   * PaymentIntentService provides Payment Intent APIs for providers.
   * Payment Intent is a flow where:
   * 1. Beneficiary provider creates a payment intent specifying amount/currency
   * 2. End-user pays via one of the returned payment options
   * 3. Pay-in provider confirms funds received
   * 4. Settlement will happen periodically between providers
   * This service is hosted by the T-0 Network and called by providers.
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     **
     * Used by the provider to publish payment intent (pay-in) quotes into the network.
     * These quotes include tiered pricing bands and an expiration timestamp.
     * </pre>
     */
    default void updateQuote(network.t0.sdk.proto.tzero.v1.payment_intent.UpdateQuoteRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.UpdateQuoteResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getUpdateQuoteMethod(), responseObserver);
    }

    /**
     * <pre>
     **
     * GetQuote returns available quotes for a given currency and amount.
     * Use this to check indicative rates before creating a payment intent.
     * The returned quotes show which providers can accept pay-ins and their current rates.
     * Note: Quotes are indicative only. The actual rate used for settlement is determined
     * at the time of ConfirmFundsReceived.
     * </pre>
     */
    default void getQuote(network.t0.sdk.proto.tzero.v1.payment_intent.GetQuoteRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.GetQuoteResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetQuoteMethod(), responseObserver);
    }

    /**
     * <pre>
     **
     * CreatePaymentIntent initiates a new payment intent.
     * Called by the beneficiary provider (the one who will receive the settlement).
     * The network finds suitable pay-in providers, retrieves their payment details,
     * and returns available payment options to present to the end-user.
     * The returned payment_intent_id must be stored by the beneficiary provider
     * to correlate with the PaymentIntentUpdate notification received later.
     * Idempotency: Multiple calls with the same external_reference return the same payment_intent_id.
     * </pre>
     */
    default void createPaymentIntent(network.t0.sdk.proto.tzero.v1.payment_intent.CreatePaymentIntentRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.CreatePaymentIntentResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getCreatePaymentIntentMethod(), responseObserver);
    }

    /**
     * <pre>
     **
     * ConfirmFundsReceived confirms that the pay-in provider has received funds from the end-user.
     * </pre>
     */
    default void confirmFundsReceived(network.t0.sdk.proto.tzero.v1.payment_intent.ConfirmFundsReceivedRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.ConfirmFundsReceivedResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getConfirmFundsReceivedMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service PaymentIntentService.
   * <pre>
   **
   * PaymentIntentService provides Payment Intent APIs for providers.
   * Payment Intent is a flow where:
   * 1. Beneficiary provider creates a payment intent specifying amount/currency
   * 2. End-user pays via one of the returned payment options
   * 3. Pay-in provider confirms funds received
   * 4. Settlement will happen periodically between providers
   * This service is hosted by the T-0 Network and called by providers.
   * </pre>
   */
  public static abstract class PaymentIntentServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return PaymentIntentServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service PaymentIntentService.
   * <pre>
   **
   * PaymentIntentService provides Payment Intent APIs for providers.
   * Payment Intent is a flow where:
   * 1. Beneficiary provider creates a payment intent specifying amount/currency
   * 2. End-user pays via one of the returned payment options
   * 3. Pay-in provider confirms funds received
   * 4. Settlement will happen periodically between providers
   * This service is hosted by the T-0 Network and called by providers.
   * </pre>
   */
  public static final class PaymentIntentServiceStub
      extends io.grpc.stub.AbstractAsyncStub<PaymentIntentServiceStub> {
    private PaymentIntentServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PaymentIntentServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PaymentIntentServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     **
     * Used by the provider to publish payment intent (pay-in) quotes into the network.
     * These quotes include tiered pricing bands and an expiration timestamp.
     * </pre>
     */
    public void updateQuote(network.t0.sdk.proto.tzero.v1.payment_intent.UpdateQuoteRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.UpdateQuoteResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getUpdateQuoteMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     **
     * GetQuote returns available quotes for a given currency and amount.
     * Use this to check indicative rates before creating a payment intent.
     * The returned quotes show which providers can accept pay-ins and their current rates.
     * Note: Quotes are indicative only. The actual rate used for settlement is determined
     * at the time of ConfirmFundsReceived.
     * </pre>
     */
    public void getQuote(network.t0.sdk.proto.tzero.v1.payment_intent.GetQuoteRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.GetQuoteResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetQuoteMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     **
     * CreatePaymentIntent initiates a new payment intent.
     * Called by the beneficiary provider (the one who will receive the settlement).
     * The network finds suitable pay-in providers, retrieves their payment details,
     * and returns available payment options to present to the end-user.
     * The returned payment_intent_id must be stored by the beneficiary provider
     * to correlate with the PaymentIntentUpdate notification received later.
     * Idempotency: Multiple calls with the same external_reference return the same payment_intent_id.
     * </pre>
     */
    public void createPaymentIntent(network.t0.sdk.proto.tzero.v1.payment_intent.CreatePaymentIntentRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.CreatePaymentIntentResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getCreatePaymentIntentMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     **
     * ConfirmFundsReceived confirms that the pay-in provider has received funds from the end-user.
     * </pre>
     */
    public void confirmFundsReceived(network.t0.sdk.proto.tzero.v1.payment_intent.ConfirmFundsReceivedRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.ConfirmFundsReceivedResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getConfirmFundsReceivedMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service PaymentIntentService.
   * <pre>
   **
   * PaymentIntentService provides Payment Intent APIs for providers.
   * Payment Intent is a flow where:
   * 1. Beneficiary provider creates a payment intent specifying amount/currency
   * 2. End-user pays via one of the returned payment options
   * 3. Pay-in provider confirms funds received
   * 4. Settlement will happen periodically between providers
   * This service is hosted by the T-0 Network and called by providers.
   * </pre>
   */
  public static final class PaymentIntentServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<PaymentIntentServiceBlockingV2Stub> {
    private PaymentIntentServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PaymentIntentServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PaymentIntentServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     * <pre>
     **
     * Used by the provider to publish payment intent (pay-in) quotes into the network.
     * These quotes include tiered pricing bands and an expiration timestamp.
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment_intent.UpdateQuoteResponse updateQuote(network.t0.sdk.proto.tzero.v1.payment_intent.UpdateQuoteRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getUpdateQuoteMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     **
     * GetQuote returns available quotes for a given currency and amount.
     * Use this to check indicative rates before creating a payment intent.
     * The returned quotes show which providers can accept pay-ins and their current rates.
     * Note: Quotes are indicative only. The actual rate used for settlement is determined
     * at the time of ConfirmFundsReceived.
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment_intent.GetQuoteResponse getQuote(network.t0.sdk.proto.tzero.v1.payment_intent.GetQuoteRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getGetQuoteMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     **
     * CreatePaymentIntent initiates a new payment intent.
     * Called by the beneficiary provider (the one who will receive the settlement).
     * The network finds suitable pay-in providers, retrieves their payment details,
     * and returns available payment options to present to the end-user.
     * The returned payment_intent_id must be stored by the beneficiary provider
     * to correlate with the PaymentIntentUpdate notification received later.
     * Idempotency: Multiple calls with the same external_reference return the same payment_intent_id.
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment_intent.CreatePaymentIntentResponse createPaymentIntent(network.t0.sdk.proto.tzero.v1.payment_intent.CreatePaymentIntentRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getCreatePaymentIntentMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     **
     * ConfirmFundsReceived confirms that the pay-in provider has received funds from the end-user.
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment_intent.ConfirmFundsReceivedResponse confirmFundsReceived(network.t0.sdk.proto.tzero.v1.payment_intent.ConfirmFundsReceivedRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getConfirmFundsReceivedMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service PaymentIntentService.
   * <pre>
   **
   * PaymentIntentService provides Payment Intent APIs for providers.
   * Payment Intent is a flow where:
   * 1. Beneficiary provider creates a payment intent specifying amount/currency
   * 2. End-user pays via one of the returned payment options
   * 3. Pay-in provider confirms funds received
   * 4. Settlement will happen periodically between providers
   * This service is hosted by the T-0 Network and called by providers.
   * </pre>
   */
  public static final class PaymentIntentServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<PaymentIntentServiceBlockingStub> {
    private PaymentIntentServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PaymentIntentServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PaymentIntentServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     **
     * Used by the provider to publish payment intent (pay-in) quotes into the network.
     * These quotes include tiered pricing bands and an expiration timestamp.
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment_intent.UpdateQuoteResponse updateQuote(network.t0.sdk.proto.tzero.v1.payment_intent.UpdateQuoteRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getUpdateQuoteMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     **
     * GetQuote returns available quotes for a given currency and amount.
     * Use this to check indicative rates before creating a payment intent.
     * The returned quotes show which providers can accept pay-ins and their current rates.
     * Note: Quotes are indicative only. The actual rate used for settlement is determined
     * at the time of ConfirmFundsReceived.
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment_intent.GetQuoteResponse getQuote(network.t0.sdk.proto.tzero.v1.payment_intent.GetQuoteRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetQuoteMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     **
     * CreatePaymentIntent initiates a new payment intent.
     * Called by the beneficiary provider (the one who will receive the settlement).
     * The network finds suitable pay-in providers, retrieves their payment details,
     * and returns available payment options to present to the end-user.
     * The returned payment_intent_id must be stored by the beneficiary provider
     * to correlate with the PaymentIntentUpdate notification received later.
     * Idempotency: Multiple calls with the same external_reference return the same payment_intent_id.
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment_intent.CreatePaymentIntentResponse createPaymentIntent(network.t0.sdk.proto.tzero.v1.payment_intent.CreatePaymentIntentRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCreatePaymentIntentMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     **
     * ConfirmFundsReceived confirms that the pay-in provider has received funds from the end-user.
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment_intent.ConfirmFundsReceivedResponse confirmFundsReceived(network.t0.sdk.proto.tzero.v1.payment_intent.ConfirmFundsReceivedRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getConfirmFundsReceivedMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service PaymentIntentService.
   * <pre>
   **
   * PaymentIntentService provides Payment Intent APIs for providers.
   * Payment Intent is a flow where:
   * 1. Beneficiary provider creates a payment intent specifying amount/currency
   * 2. End-user pays via one of the returned payment options
   * 3. Pay-in provider confirms funds received
   * 4. Settlement will happen periodically between providers
   * This service is hosted by the T-0 Network and called by providers.
   * </pre>
   */
  public static final class PaymentIntentServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<PaymentIntentServiceFutureStub> {
    private PaymentIntentServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PaymentIntentServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PaymentIntentServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     **
     * Used by the provider to publish payment intent (pay-in) quotes into the network.
     * These quotes include tiered pricing bands and an expiration timestamp.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<network.t0.sdk.proto.tzero.v1.payment_intent.UpdateQuoteResponse> updateQuote(
        network.t0.sdk.proto.tzero.v1.payment_intent.UpdateQuoteRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getUpdateQuoteMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     **
     * GetQuote returns available quotes for a given currency and amount.
     * Use this to check indicative rates before creating a payment intent.
     * The returned quotes show which providers can accept pay-ins and their current rates.
     * Note: Quotes are indicative only. The actual rate used for settlement is determined
     * at the time of ConfirmFundsReceived.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<network.t0.sdk.proto.tzero.v1.payment_intent.GetQuoteResponse> getQuote(
        network.t0.sdk.proto.tzero.v1.payment_intent.GetQuoteRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetQuoteMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     **
     * CreatePaymentIntent initiates a new payment intent.
     * Called by the beneficiary provider (the one who will receive the settlement).
     * The network finds suitable pay-in providers, retrieves their payment details,
     * and returns available payment options to present to the end-user.
     * The returned payment_intent_id must be stored by the beneficiary provider
     * to correlate with the PaymentIntentUpdate notification received later.
     * Idempotency: Multiple calls with the same external_reference return the same payment_intent_id.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<network.t0.sdk.proto.tzero.v1.payment_intent.CreatePaymentIntentResponse> createPaymentIntent(
        network.t0.sdk.proto.tzero.v1.payment_intent.CreatePaymentIntentRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getCreatePaymentIntentMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     **
     * ConfirmFundsReceived confirms that the pay-in provider has received funds from the end-user.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<network.t0.sdk.proto.tzero.v1.payment_intent.ConfirmFundsReceivedResponse> confirmFundsReceived(
        network.t0.sdk.proto.tzero.v1.payment_intent.ConfirmFundsReceivedRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getConfirmFundsReceivedMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_UPDATE_QUOTE = 0;
  private static final int METHODID_GET_QUOTE = 1;
  private static final int METHODID_CREATE_PAYMENT_INTENT = 2;
  private static final int METHODID_CONFIRM_FUNDS_RECEIVED = 3;

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
        case METHODID_UPDATE_QUOTE:
          serviceImpl.updateQuote((network.t0.sdk.proto.tzero.v1.payment_intent.UpdateQuoteRequest) request,
              (io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.UpdateQuoteResponse>) responseObserver);
          break;
        case METHODID_GET_QUOTE:
          serviceImpl.getQuote((network.t0.sdk.proto.tzero.v1.payment_intent.GetQuoteRequest) request,
              (io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.GetQuoteResponse>) responseObserver);
          break;
        case METHODID_CREATE_PAYMENT_INTENT:
          serviceImpl.createPaymentIntent((network.t0.sdk.proto.tzero.v1.payment_intent.CreatePaymentIntentRequest) request,
              (io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.CreatePaymentIntentResponse>) responseObserver);
          break;
        case METHODID_CONFIRM_FUNDS_RECEIVED:
          serviceImpl.confirmFundsReceived((network.t0.sdk.proto.tzero.v1.payment_intent.ConfirmFundsReceivedRequest) request,
              (io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment_intent.ConfirmFundsReceivedResponse>) responseObserver);
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
          getUpdateQuoteMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              network.t0.sdk.proto.tzero.v1.payment_intent.UpdateQuoteRequest,
              network.t0.sdk.proto.tzero.v1.payment_intent.UpdateQuoteResponse>(
                service, METHODID_UPDATE_QUOTE)))
        .addMethod(
          getGetQuoteMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              network.t0.sdk.proto.tzero.v1.payment_intent.GetQuoteRequest,
              network.t0.sdk.proto.tzero.v1.payment_intent.GetQuoteResponse>(
                service, METHODID_GET_QUOTE)))
        .addMethod(
          getCreatePaymentIntentMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              network.t0.sdk.proto.tzero.v1.payment_intent.CreatePaymentIntentRequest,
              network.t0.sdk.proto.tzero.v1.payment_intent.CreatePaymentIntentResponse>(
                service, METHODID_CREATE_PAYMENT_INTENT)))
        .addMethod(
          getConfirmFundsReceivedMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              network.t0.sdk.proto.tzero.v1.payment_intent.ConfirmFundsReceivedRequest,
              network.t0.sdk.proto.tzero.v1.payment_intent.ConfirmFundsReceivedResponse>(
                service, METHODID_CONFIRM_FUNDS_RECEIVED)))
        .build();
  }

  private static abstract class PaymentIntentServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    PaymentIntentServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return network.t0.sdk.proto.tzero.v1.payment_intent.NetworkProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("PaymentIntentService");
    }
  }

  private static final class PaymentIntentServiceFileDescriptorSupplier
      extends PaymentIntentServiceBaseDescriptorSupplier {
    PaymentIntentServiceFileDescriptorSupplier() {}
  }

  private static final class PaymentIntentServiceMethodDescriptorSupplier
      extends PaymentIntentServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    PaymentIntentServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (PaymentIntentServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new PaymentIntentServiceFileDescriptorSupplier())
              .addMethod(getUpdateQuoteMethod())
              .addMethod(getGetQuoteMethod())
              .addMethod(getCreatePaymentIntentMethod())
              .addMethod(getConfirmFundsReceivedMethod())
              .build();
        }
      }
    }
    return result;
  }
}
