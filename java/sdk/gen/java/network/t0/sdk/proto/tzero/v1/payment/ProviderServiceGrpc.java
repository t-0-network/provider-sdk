package network.t0.sdk.proto.tzero.v1.payment;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 **
 * This service must be implemented by the provider.
 * All methods of this service must be idempotent, meaning they are safe to retry and multiple calls with the same parameters must not have additional effect.
 * </pre>
 */
@io.grpc.stub.annotations.GrpcGenerated
public final class ProviderServiceGrpc {

  private ProviderServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "tzero.v1.payment.ProviderService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.PayoutRequest,
      network.t0.sdk.proto.tzero.v1.payment.PayoutResponse> getPayOutMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "PayOut",
      requestType = network.t0.sdk.proto.tzero.v1.payment.PayoutRequest.class,
      responseType = network.t0.sdk.proto.tzero.v1.payment.PayoutResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.PayoutRequest,
      network.t0.sdk.proto.tzero.v1.payment.PayoutResponse> getPayOutMethod() {
    io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.PayoutRequest, network.t0.sdk.proto.tzero.v1.payment.PayoutResponse> getPayOutMethod;
    if ((getPayOutMethod = ProviderServiceGrpc.getPayOutMethod) == null) {
      synchronized (ProviderServiceGrpc.class) {
        if ((getPayOutMethod = ProviderServiceGrpc.getPayOutMethod) == null) {
          ProviderServiceGrpc.getPayOutMethod = getPayOutMethod =
              io.grpc.MethodDescriptor.<network.t0.sdk.proto.tzero.v1.payment.PayoutRequest, network.t0.sdk.proto.tzero.v1.payment.PayoutResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "PayOut"))
              .setIdempotent(true)
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment.PayoutRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment.PayoutResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ProviderServiceMethodDescriptorSupplier("PayOut"))
              .build();
        }
      }
    }
    return getPayOutMethod;
  }

  private static volatile io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.UpdatePaymentRequest,
      network.t0.sdk.proto.tzero.v1.payment.UpdatePaymentResponse> getUpdatePaymentMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "UpdatePayment",
      requestType = network.t0.sdk.proto.tzero.v1.payment.UpdatePaymentRequest.class,
      responseType = network.t0.sdk.proto.tzero.v1.payment.UpdatePaymentResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.UpdatePaymentRequest,
      network.t0.sdk.proto.tzero.v1.payment.UpdatePaymentResponse> getUpdatePaymentMethod() {
    io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.UpdatePaymentRequest, network.t0.sdk.proto.tzero.v1.payment.UpdatePaymentResponse> getUpdatePaymentMethod;
    if ((getUpdatePaymentMethod = ProviderServiceGrpc.getUpdatePaymentMethod) == null) {
      synchronized (ProviderServiceGrpc.class) {
        if ((getUpdatePaymentMethod = ProviderServiceGrpc.getUpdatePaymentMethod) == null) {
          ProviderServiceGrpc.getUpdatePaymentMethod = getUpdatePaymentMethod =
              io.grpc.MethodDescriptor.<network.t0.sdk.proto.tzero.v1.payment.UpdatePaymentRequest, network.t0.sdk.proto.tzero.v1.payment.UpdatePaymentResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "UpdatePayment"))
              .setIdempotent(true)
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment.UpdatePaymentRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment.UpdatePaymentResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ProviderServiceMethodDescriptorSupplier("UpdatePayment"))
              .build();
        }
      }
    }
    return getUpdatePaymentMethod;
  }

  private static volatile io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.UpdateLimitRequest,
      network.t0.sdk.proto.tzero.v1.payment.UpdateLimitResponse> getUpdateLimitMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "UpdateLimit",
      requestType = network.t0.sdk.proto.tzero.v1.payment.UpdateLimitRequest.class,
      responseType = network.t0.sdk.proto.tzero.v1.payment.UpdateLimitResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.UpdateLimitRequest,
      network.t0.sdk.proto.tzero.v1.payment.UpdateLimitResponse> getUpdateLimitMethod() {
    io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.UpdateLimitRequest, network.t0.sdk.proto.tzero.v1.payment.UpdateLimitResponse> getUpdateLimitMethod;
    if ((getUpdateLimitMethod = ProviderServiceGrpc.getUpdateLimitMethod) == null) {
      synchronized (ProviderServiceGrpc.class) {
        if ((getUpdateLimitMethod = ProviderServiceGrpc.getUpdateLimitMethod) == null) {
          ProviderServiceGrpc.getUpdateLimitMethod = getUpdateLimitMethod =
              io.grpc.MethodDescriptor.<network.t0.sdk.proto.tzero.v1.payment.UpdateLimitRequest, network.t0.sdk.proto.tzero.v1.payment.UpdateLimitResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "UpdateLimit"))
              .setIdempotent(true)
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment.UpdateLimitRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment.UpdateLimitResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ProviderServiceMethodDescriptorSupplier("UpdateLimit"))
              .build();
        }
      }
    }
    return getUpdateLimitMethod;
  }

  private static volatile io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.AppendLedgerEntriesRequest,
      network.t0.sdk.proto.tzero.v1.payment.AppendLedgerEntriesResponse> getAppendLedgerEntriesMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "AppendLedgerEntries",
      requestType = network.t0.sdk.proto.tzero.v1.payment.AppendLedgerEntriesRequest.class,
      responseType = network.t0.sdk.proto.tzero.v1.payment.AppendLedgerEntriesResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.AppendLedgerEntriesRequest,
      network.t0.sdk.proto.tzero.v1.payment.AppendLedgerEntriesResponse> getAppendLedgerEntriesMethod() {
    io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.AppendLedgerEntriesRequest, network.t0.sdk.proto.tzero.v1.payment.AppendLedgerEntriesResponse> getAppendLedgerEntriesMethod;
    if ((getAppendLedgerEntriesMethod = ProviderServiceGrpc.getAppendLedgerEntriesMethod) == null) {
      synchronized (ProviderServiceGrpc.class) {
        if ((getAppendLedgerEntriesMethod = ProviderServiceGrpc.getAppendLedgerEntriesMethod) == null) {
          ProviderServiceGrpc.getAppendLedgerEntriesMethod = getAppendLedgerEntriesMethod =
              io.grpc.MethodDescriptor.<network.t0.sdk.proto.tzero.v1.payment.AppendLedgerEntriesRequest, network.t0.sdk.proto.tzero.v1.payment.AppendLedgerEntriesResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "AppendLedgerEntries"))
              .setIdempotent(true)
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment.AppendLedgerEntriesRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment.AppendLedgerEntriesResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ProviderServiceMethodDescriptorSupplier("AppendLedgerEntries"))
              .build();
        }
      }
    }
    return getAppendLedgerEntriesMethod;
  }

  private static volatile io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.ApprovePaymentQuoteRequest,
      network.t0.sdk.proto.tzero.v1.payment.ApprovePaymentQuoteResponse> getApprovePaymentQuotesMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ApprovePaymentQuotes",
      requestType = network.t0.sdk.proto.tzero.v1.payment.ApprovePaymentQuoteRequest.class,
      responseType = network.t0.sdk.proto.tzero.v1.payment.ApprovePaymentQuoteResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.ApprovePaymentQuoteRequest,
      network.t0.sdk.proto.tzero.v1.payment.ApprovePaymentQuoteResponse> getApprovePaymentQuotesMethod() {
    io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.ApprovePaymentQuoteRequest, network.t0.sdk.proto.tzero.v1.payment.ApprovePaymentQuoteResponse> getApprovePaymentQuotesMethod;
    if ((getApprovePaymentQuotesMethod = ProviderServiceGrpc.getApprovePaymentQuotesMethod) == null) {
      synchronized (ProviderServiceGrpc.class) {
        if ((getApprovePaymentQuotesMethod = ProviderServiceGrpc.getApprovePaymentQuotesMethod) == null) {
          ProviderServiceGrpc.getApprovePaymentQuotesMethod = getApprovePaymentQuotesMethod =
              io.grpc.MethodDescriptor.<network.t0.sdk.proto.tzero.v1.payment.ApprovePaymentQuoteRequest, network.t0.sdk.proto.tzero.v1.payment.ApprovePaymentQuoteResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ApprovePaymentQuotes"))
              .setIdempotent(true)
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment.ApprovePaymentQuoteRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment.ApprovePaymentQuoteResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ProviderServiceMethodDescriptorSupplier("ApprovePaymentQuotes"))
              .build();
        }
      }
    }
    return getApprovePaymentQuotesMethod;
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
   * This service must be implemented by the provider.
   * All methods of this service must be idempotent, meaning they are safe to retry and multiple calls with the same parameters must not have additional effect.
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     **
     * Network instructs the provider to execute a payout to the recipient.
     * This method should be idempotent, meaning that multiple calls with the same parameters will have no additional effect.
     * </pre>
     */
    default void payOut(network.t0.sdk.proto.tzero.v1.payment.PayoutRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.PayoutResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getPayOutMethod(), responseObserver);
    }

    /**
     * <pre>
     **
     * Network provides an update on the status of a payment. This can be either a success or a failure.
     * This method should be idempotent, meaning that multiple calls with the same parameters will have no additional effect.
     * </pre>
     */
    default void updatePayment(network.t0.sdk.proto.tzero.v1.payment.UpdatePaymentRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.UpdatePaymentResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getUpdatePaymentMethod(), responseObserver);
    }

    /**
     * <pre>
     **
     * This rpc is used to notify the provider about the changes in credit limit and/or credit usage.
     * </pre>
     */
    default void updateLimit(network.t0.sdk.proto.tzero.v1.payment.UpdateLimitRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.UpdateLimitResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getUpdateLimitMethod(), responseObserver);
    }

    /**
     * <pre>
     **
     * Network can send all the updates about ledger entries of the provider's accounts. It can be used to
     * keep track of the provider's exposure to other participants and other important financial events. (see the list in the message below)
     * </pre>
     */
    default void appendLedgerEntries(network.t0.sdk.proto.tzero.v1.payment.AppendLedgerEntriesRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.AppendLedgerEntriesResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getAppendLedgerEntriesMethod(), responseObserver);
    }

    /**
     * <pre>
     **
     * Pay-in provider approves the final pay-out quotes.
     * This is the "Last Look" endpoint - it must be called after manual AML check completes
     * (if one was required). It allows pay-in provider to verify and approve final rates
     * before payment is executed.
     * </pre>
     */
    default void approvePaymentQuotes(network.t0.sdk.proto.tzero.v1.payment.ApprovePaymentQuoteRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.ApprovePaymentQuoteResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getApprovePaymentQuotesMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service ProviderService.
   * <pre>
   **
   * This service must be implemented by the provider.
   * All methods of this service must be idempotent, meaning they are safe to retry and multiple calls with the same parameters must not have additional effect.
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
   * This service must be implemented by the provider.
   * All methods of this service must be idempotent, meaning they are safe to retry and multiple calls with the same parameters must not have additional effect.
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
     * Network instructs the provider to execute a payout to the recipient.
     * This method should be idempotent, meaning that multiple calls with the same parameters will have no additional effect.
     * </pre>
     */
    public void payOut(network.t0.sdk.proto.tzero.v1.payment.PayoutRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.PayoutResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getPayOutMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     **
     * Network provides an update on the status of a payment. This can be either a success or a failure.
     * This method should be idempotent, meaning that multiple calls with the same parameters will have no additional effect.
     * </pre>
     */
    public void updatePayment(network.t0.sdk.proto.tzero.v1.payment.UpdatePaymentRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.UpdatePaymentResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getUpdatePaymentMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     **
     * This rpc is used to notify the provider about the changes in credit limit and/or credit usage.
     * </pre>
     */
    public void updateLimit(network.t0.sdk.proto.tzero.v1.payment.UpdateLimitRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.UpdateLimitResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getUpdateLimitMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     **
     * Network can send all the updates about ledger entries of the provider's accounts. It can be used to
     * keep track of the provider's exposure to other participants and other important financial events. (see the list in the message below)
     * </pre>
     */
    public void appendLedgerEntries(network.t0.sdk.proto.tzero.v1.payment.AppendLedgerEntriesRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.AppendLedgerEntriesResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getAppendLedgerEntriesMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     **
     * Pay-in provider approves the final pay-out quotes.
     * This is the "Last Look" endpoint - it must be called after manual AML check completes
     * (if one was required). It allows pay-in provider to verify and approve final rates
     * before payment is executed.
     * </pre>
     */
    public void approvePaymentQuotes(network.t0.sdk.proto.tzero.v1.payment.ApprovePaymentQuoteRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.ApprovePaymentQuoteResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getApprovePaymentQuotesMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service ProviderService.
   * <pre>
   **
   * This service must be implemented by the provider.
   * All methods of this service must be idempotent, meaning they are safe to retry and multiple calls with the same parameters must not have additional effect.
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
     * Network instructs the provider to execute a payout to the recipient.
     * This method should be idempotent, meaning that multiple calls with the same parameters will have no additional effect.
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment.PayoutResponse payOut(network.t0.sdk.proto.tzero.v1.payment.PayoutRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getPayOutMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     **
     * Network provides an update on the status of a payment. This can be either a success or a failure.
     * This method should be idempotent, meaning that multiple calls with the same parameters will have no additional effect.
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment.UpdatePaymentResponse updatePayment(network.t0.sdk.proto.tzero.v1.payment.UpdatePaymentRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getUpdatePaymentMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     **
     * This rpc is used to notify the provider about the changes in credit limit and/or credit usage.
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment.UpdateLimitResponse updateLimit(network.t0.sdk.proto.tzero.v1.payment.UpdateLimitRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getUpdateLimitMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     **
     * Network can send all the updates about ledger entries of the provider's accounts. It can be used to
     * keep track of the provider's exposure to other participants and other important financial events. (see the list in the message below)
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment.AppendLedgerEntriesResponse appendLedgerEntries(network.t0.sdk.proto.tzero.v1.payment.AppendLedgerEntriesRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getAppendLedgerEntriesMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     **
     * Pay-in provider approves the final pay-out quotes.
     * This is the "Last Look" endpoint - it must be called after manual AML check completes
     * (if one was required). It allows pay-in provider to verify and approve final rates
     * before payment is executed.
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment.ApprovePaymentQuoteResponse approvePaymentQuotes(network.t0.sdk.proto.tzero.v1.payment.ApprovePaymentQuoteRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getApprovePaymentQuotesMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service ProviderService.
   * <pre>
   **
   * This service must be implemented by the provider.
   * All methods of this service must be idempotent, meaning they are safe to retry and multiple calls with the same parameters must not have additional effect.
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
     * Network instructs the provider to execute a payout to the recipient.
     * This method should be idempotent, meaning that multiple calls with the same parameters will have no additional effect.
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment.PayoutResponse payOut(network.t0.sdk.proto.tzero.v1.payment.PayoutRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getPayOutMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     **
     * Network provides an update on the status of a payment. This can be either a success or a failure.
     * This method should be idempotent, meaning that multiple calls with the same parameters will have no additional effect.
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment.UpdatePaymentResponse updatePayment(network.t0.sdk.proto.tzero.v1.payment.UpdatePaymentRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getUpdatePaymentMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     **
     * This rpc is used to notify the provider about the changes in credit limit and/or credit usage.
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment.UpdateLimitResponse updateLimit(network.t0.sdk.proto.tzero.v1.payment.UpdateLimitRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getUpdateLimitMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     **
     * Network can send all the updates about ledger entries of the provider's accounts. It can be used to
     * keep track of the provider's exposure to other participants and other important financial events. (see the list in the message below)
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment.AppendLedgerEntriesResponse appendLedgerEntries(network.t0.sdk.proto.tzero.v1.payment.AppendLedgerEntriesRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getAppendLedgerEntriesMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     **
     * Pay-in provider approves the final pay-out quotes.
     * This is the "Last Look" endpoint - it must be called after manual AML check completes
     * (if one was required). It allows pay-in provider to verify and approve final rates
     * before payment is executed.
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment.ApprovePaymentQuoteResponse approvePaymentQuotes(network.t0.sdk.proto.tzero.v1.payment.ApprovePaymentQuoteRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getApprovePaymentQuotesMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service ProviderService.
   * <pre>
   **
   * This service must be implemented by the provider.
   * All methods of this service must be idempotent, meaning they are safe to retry and multiple calls with the same parameters must not have additional effect.
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
     * Network instructs the provider to execute a payout to the recipient.
     * This method should be idempotent, meaning that multiple calls with the same parameters will have no additional effect.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<network.t0.sdk.proto.tzero.v1.payment.PayoutResponse> payOut(
        network.t0.sdk.proto.tzero.v1.payment.PayoutRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getPayOutMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     **
     * Network provides an update on the status of a payment. This can be either a success or a failure.
     * This method should be idempotent, meaning that multiple calls with the same parameters will have no additional effect.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<network.t0.sdk.proto.tzero.v1.payment.UpdatePaymentResponse> updatePayment(
        network.t0.sdk.proto.tzero.v1.payment.UpdatePaymentRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getUpdatePaymentMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     **
     * This rpc is used to notify the provider about the changes in credit limit and/or credit usage.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<network.t0.sdk.proto.tzero.v1.payment.UpdateLimitResponse> updateLimit(
        network.t0.sdk.proto.tzero.v1.payment.UpdateLimitRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getUpdateLimitMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     **
     * Network can send all the updates about ledger entries of the provider's accounts. It can be used to
     * keep track of the provider's exposure to other participants and other important financial events. (see the list in the message below)
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<network.t0.sdk.proto.tzero.v1.payment.AppendLedgerEntriesResponse> appendLedgerEntries(
        network.t0.sdk.proto.tzero.v1.payment.AppendLedgerEntriesRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getAppendLedgerEntriesMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     **
     * Pay-in provider approves the final pay-out quotes.
     * This is the "Last Look" endpoint - it must be called after manual AML check completes
     * (if one was required). It allows pay-in provider to verify and approve final rates
     * before payment is executed.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<network.t0.sdk.proto.tzero.v1.payment.ApprovePaymentQuoteResponse> approvePaymentQuotes(
        network.t0.sdk.proto.tzero.v1.payment.ApprovePaymentQuoteRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getApprovePaymentQuotesMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_PAY_OUT = 0;
  private static final int METHODID_UPDATE_PAYMENT = 1;
  private static final int METHODID_UPDATE_LIMIT = 2;
  private static final int METHODID_APPEND_LEDGER_ENTRIES = 3;
  private static final int METHODID_APPROVE_PAYMENT_QUOTES = 4;

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
        case METHODID_PAY_OUT:
          serviceImpl.payOut((network.t0.sdk.proto.tzero.v1.payment.PayoutRequest) request,
              (io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.PayoutResponse>) responseObserver);
          break;
        case METHODID_UPDATE_PAYMENT:
          serviceImpl.updatePayment((network.t0.sdk.proto.tzero.v1.payment.UpdatePaymentRequest) request,
              (io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.UpdatePaymentResponse>) responseObserver);
          break;
        case METHODID_UPDATE_LIMIT:
          serviceImpl.updateLimit((network.t0.sdk.proto.tzero.v1.payment.UpdateLimitRequest) request,
              (io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.UpdateLimitResponse>) responseObserver);
          break;
        case METHODID_APPEND_LEDGER_ENTRIES:
          serviceImpl.appendLedgerEntries((network.t0.sdk.proto.tzero.v1.payment.AppendLedgerEntriesRequest) request,
              (io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.AppendLedgerEntriesResponse>) responseObserver);
          break;
        case METHODID_APPROVE_PAYMENT_QUOTES:
          serviceImpl.approvePaymentQuotes((network.t0.sdk.proto.tzero.v1.payment.ApprovePaymentQuoteRequest) request,
              (io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.ApprovePaymentQuoteResponse>) responseObserver);
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
          getPayOutMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              network.t0.sdk.proto.tzero.v1.payment.PayoutRequest,
              network.t0.sdk.proto.tzero.v1.payment.PayoutResponse>(
                service, METHODID_PAY_OUT)))
        .addMethod(
          getUpdatePaymentMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              network.t0.sdk.proto.tzero.v1.payment.UpdatePaymentRequest,
              network.t0.sdk.proto.tzero.v1.payment.UpdatePaymentResponse>(
                service, METHODID_UPDATE_PAYMENT)))
        .addMethod(
          getUpdateLimitMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              network.t0.sdk.proto.tzero.v1.payment.UpdateLimitRequest,
              network.t0.sdk.proto.tzero.v1.payment.UpdateLimitResponse>(
                service, METHODID_UPDATE_LIMIT)))
        .addMethod(
          getAppendLedgerEntriesMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              network.t0.sdk.proto.tzero.v1.payment.AppendLedgerEntriesRequest,
              network.t0.sdk.proto.tzero.v1.payment.AppendLedgerEntriesResponse>(
                service, METHODID_APPEND_LEDGER_ENTRIES)))
        .addMethod(
          getApprovePaymentQuotesMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              network.t0.sdk.proto.tzero.v1.payment.ApprovePaymentQuoteRequest,
              network.t0.sdk.proto.tzero.v1.payment.ApprovePaymentQuoteResponse>(
                service, METHODID_APPROVE_PAYMENT_QUOTES)))
        .build();
  }

  private static abstract class ProviderServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    ProviderServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return network.t0.sdk.proto.tzero.v1.payment.ProviderProto.getDescriptor();
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
              .addMethod(getPayOutMethod())
              .addMethod(getUpdatePaymentMethod())
              .addMethod(getUpdateLimitMethod())
              .addMethod(getAppendLedgerEntriesMethod())
              .addMethod(getApprovePaymentQuotesMethod())
              .build();
        }
      }
    }
    return result;
  }
}
