package network.t0.sdk.proto.tzero.v1.payment;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 **
 * This service is used by provider to interact with the Network, e.g. push quotes and initiate payments.
 * All methods of this service are idempotent, meaning they are safe to retry and multiple calls with the same parameters will have no additional effect.
 * </pre>
 */
@io.grpc.stub.annotations.GrpcGenerated
public final class NetworkServiceGrpc {

  private NetworkServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "tzero.v1.payment.NetworkService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.UpdateQuoteRequest,
      network.t0.sdk.proto.tzero.v1.payment.UpdateQuoteResponse> getUpdateQuoteMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "UpdateQuote",
      requestType = network.t0.sdk.proto.tzero.v1.payment.UpdateQuoteRequest.class,
      responseType = network.t0.sdk.proto.tzero.v1.payment.UpdateQuoteResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.UpdateQuoteRequest,
      network.t0.sdk.proto.tzero.v1.payment.UpdateQuoteResponse> getUpdateQuoteMethod() {
    io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.UpdateQuoteRequest, network.t0.sdk.proto.tzero.v1.payment.UpdateQuoteResponse> getUpdateQuoteMethod;
    if ((getUpdateQuoteMethod = NetworkServiceGrpc.getUpdateQuoteMethod) == null) {
      synchronized (NetworkServiceGrpc.class) {
        if ((getUpdateQuoteMethod = NetworkServiceGrpc.getUpdateQuoteMethod) == null) {
          NetworkServiceGrpc.getUpdateQuoteMethod = getUpdateQuoteMethod =
              io.grpc.MethodDescriptor.<network.t0.sdk.proto.tzero.v1.payment.UpdateQuoteRequest, network.t0.sdk.proto.tzero.v1.payment.UpdateQuoteResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "UpdateQuote"))
              .setIdempotent(true)
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment.UpdateQuoteRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment.UpdateQuoteResponse.getDefaultInstance()))
              .setSchemaDescriptor(new NetworkServiceMethodDescriptorSupplier("UpdateQuote"))
              .build();
        }
      }
    }
    return getUpdateQuoteMethod;
  }

  private static volatile io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.GetQuoteRequest,
      network.t0.sdk.proto.tzero.v1.payment.GetQuoteResponse> getGetQuoteMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetQuote",
      requestType = network.t0.sdk.proto.tzero.v1.payment.GetQuoteRequest.class,
      responseType = network.t0.sdk.proto.tzero.v1.payment.GetQuoteResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.GetQuoteRequest,
      network.t0.sdk.proto.tzero.v1.payment.GetQuoteResponse> getGetQuoteMethod() {
    io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.GetQuoteRequest, network.t0.sdk.proto.tzero.v1.payment.GetQuoteResponse> getGetQuoteMethod;
    if ((getGetQuoteMethod = NetworkServiceGrpc.getGetQuoteMethod) == null) {
      synchronized (NetworkServiceGrpc.class) {
        if ((getGetQuoteMethod = NetworkServiceGrpc.getGetQuoteMethod) == null) {
          NetworkServiceGrpc.getGetQuoteMethod = getGetQuoteMethod =
              io.grpc.MethodDescriptor.<network.t0.sdk.proto.tzero.v1.payment.GetQuoteRequest, network.t0.sdk.proto.tzero.v1.payment.GetQuoteResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetQuote"))
              .setSafe(true)
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment.GetQuoteRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment.GetQuoteResponse.getDefaultInstance()))
              .setSchemaDescriptor(new NetworkServiceMethodDescriptorSupplier("GetQuote"))
              .build();
        }
      }
    }
    return getGetQuoteMethod;
  }

  private static volatile io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.CreatePaymentRequest,
      network.t0.sdk.proto.tzero.v1.payment.CreatePaymentResponse> getCreatePaymentMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "CreatePayment",
      requestType = network.t0.sdk.proto.tzero.v1.payment.CreatePaymentRequest.class,
      responseType = network.t0.sdk.proto.tzero.v1.payment.CreatePaymentResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.CreatePaymentRequest,
      network.t0.sdk.proto.tzero.v1.payment.CreatePaymentResponse> getCreatePaymentMethod() {
    io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.CreatePaymentRequest, network.t0.sdk.proto.tzero.v1.payment.CreatePaymentResponse> getCreatePaymentMethod;
    if ((getCreatePaymentMethod = NetworkServiceGrpc.getCreatePaymentMethod) == null) {
      synchronized (NetworkServiceGrpc.class) {
        if ((getCreatePaymentMethod = NetworkServiceGrpc.getCreatePaymentMethod) == null) {
          NetworkServiceGrpc.getCreatePaymentMethod = getCreatePaymentMethod =
              io.grpc.MethodDescriptor.<network.t0.sdk.proto.tzero.v1.payment.CreatePaymentRequest, network.t0.sdk.proto.tzero.v1.payment.CreatePaymentResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "CreatePayment"))
              .setIdempotent(true)
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment.CreatePaymentRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment.CreatePaymentResponse.getDefaultInstance()))
              .setSchemaDescriptor(new NetworkServiceMethodDescriptorSupplier("CreatePayment"))
              .build();
        }
      }
    }
    return getCreatePaymentMethod;
  }

  private static volatile io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.ConfirmPayoutRequest,
      network.t0.sdk.proto.tzero.v1.payment.ConfirmPayoutResponse> getConfirmPayoutMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ConfirmPayout",
      requestType = network.t0.sdk.proto.tzero.v1.payment.ConfirmPayoutRequest.class,
      responseType = network.t0.sdk.proto.tzero.v1.payment.ConfirmPayoutResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.ConfirmPayoutRequest,
      network.t0.sdk.proto.tzero.v1.payment.ConfirmPayoutResponse> getConfirmPayoutMethod() {
    io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.ConfirmPayoutRequest, network.t0.sdk.proto.tzero.v1.payment.ConfirmPayoutResponse> getConfirmPayoutMethod;
    if ((getConfirmPayoutMethod = NetworkServiceGrpc.getConfirmPayoutMethod) == null) {
      synchronized (NetworkServiceGrpc.class) {
        if ((getConfirmPayoutMethod = NetworkServiceGrpc.getConfirmPayoutMethod) == null) {
          NetworkServiceGrpc.getConfirmPayoutMethod = getConfirmPayoutMethod =
              io.grpc.MethodDescriptor.<network.t0.sdk.proto.tzero.v1.payment.ConfirmPayoutRequest, network.t0.sdk.proto.tzero.v1.payment.ConfirmPayoutResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ConfirmPayout"))
              .setIdempotent(true)
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment.ConfirmPayoutRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment.ConfirmPayoutResponse.getDefaultInstance()))
              .setSchemaDescriptor(new NetworkServiceMethodDescriptorSupplier("ConfirmPayout"))
              .build();
        }
      }
    }
    return getConfirmPayoutMethod;
  }

  private static volatile io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.FinalizePayoutRequest,
      network.t0.sdk.proto.tzero.v1.payment.FinalizePayoutResponse> getFinalizePayoutMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "FinalizePayout",
      requestType = network.t0.sdk.proto.tzero.v1.payment.FinalizePayoutRequest.class,
      responseType = network.t0.sdk.proto.tzero.v1.payment.FinalizePayoutResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.FinalizePayoutRequest,
      network.t0.sdk.proto.tzero.v1.payment.FinalizePayoutResponse> getFinalizePayoutMethod() {
    io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.FinalizePayoutRequest, network.t0.sdk.proto.tzero.v1.payment.FinalizePayoutResponse> getFinalizePayoutMethod;
    if ((getFinalizePayoutMethod = NetworkServiceGrpc.getFinalizePayoutMethod) == null) {
      synchronized (NetworkServiceGrpc.class) {
        if ((getFinalizePayoutMethod = NetworkServiceGrpc.getFinalizePayoutMethod) == null) {
          NetworkServiceGrpc.getFinalizePayoutMethod = getFinalizePayoutMethod =
              io.grpc.MethodDescriptor.<network.t0.sdk.proto.tzero.v1.payment.FinalizePayoutRequest, network.t0.sdk.proto.tzero.v1.payment.FinalizePayoutResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "FinalizePayout"))
              .setIdempotent(true)
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment.FinalizePayoutRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment.FinalizePayoutResponse.getDefaultInstance()))
              .setSchemaDescriptor(new NetworkServiceMethodDescriptorSupplier("FinalizePayout"))
              .build();
        }
      }
    }
    return getFinalizePayoutMethod;
  }

  private static volatile io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.CompleteManualAmlCheckRequest,
      network.t0.sdk.proto.tzero.v1.payment.CompleteManualAmlCheckResponse> getCompleteManualAmlCheckMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "CompleteManualAmlCheck",
      requestType = network.t0.sdk.proto.tzero.v1.payment.CompleteManualAmlCheckRequest.class,
      responseType = network.t0.sdk.proto.tzero.v1.payment.CompleteManualAmlCheckResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.CompleteManualAmlCheckRequest,
      network.t0.sdk.proto.tzero.v1.payment.CompleteManualAmlCheckResponse> getCompleteManualAmlCheckMethod() {
    io.grpc.MethodDescriptor<network.t0.sdk.proto.tzero.v1.payment.CompleteManualAmlCheckRequest, network.t0.sdk.proto.tzero.v1.payment.CompleteManualAmlCheckResponse> getCompleteManualAmlCheckMethod;
    if ((getCompleteManualAmlCheckMethod = NetworkServiceGrpc.getCompleteManualAmlCheckMethod) == null) {
      synchronized (NetworkServiceGrpc.class) {
        if ((getCompleteManualAmlCheckMethod = NetworkServiceGrpc.getCompleteManualAmlCheckMethod) == null) {
          NetworkServiceGrpc.getCompleteManualAmlCheckMethod = getCompleteManualAmlCheckMethod =
              io.grpc.MethodDescriptor.<network.t0.sdk.proto.tzero.v1.payment.CompleteManualAmlCheckRequest, network.t0.sdk.proto.tzero.v1.payment.CompleteManualAmlCheckResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "CompleteManualAmlCheck"))
              .setIdempotent(true)
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment.CompleteManualAmlCheckRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  network.t0.sdk.proto.tzero.v1.payment.CompleteManualAmlCheckResponse.getDefaultInstance()))
              .setSchemaDescriptor(new NetworkServiceMethodDescriptorSupplier("CompleteManualAmlCheck"))
              .build();
        }
      }
    }
    return getCompleteManualAmlCheckMethod;
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
   * This service is used by provider to interact with the Network, e.g. push quotes and initiate payments.
   * All methods of this service are idempotent, meaning they are safe to retry and multiple calls with the same parameters will have no additional effect.
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     **
     * Used by the provider to publish pay-in and pay-out quotes (FX rates) into the network.
     * These quotes include tiered pricing bands and an expiration timestamp.
     * </pre>
     */
    default void updateQuote(network.t0.sdk.proto.tzero.v1.payment.UpdateQuoteRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.UpdateQuoteResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getUpdateQuoteMethod(), responseObserver);
    }

    /**
     * <pre>
     **
     * Request the best available quote for a payout in a specific currency, for a given amount.
     * If the payout quote exists, but the credit limit is exceeded, this quote will not be considered.
     * </pre>
     */
    default void getQuote(network.t0.sdk.proto.tzero.v1.payment.GetQuoteRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.GetQuoteResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetQuoteMethod(), responseObserver);
    }

    /**
     * <pre>
     **
     * Submit a request to create a new payment for the specified pay-out currency.
     * QuoteId is the optional parameter.
     * If the quoteID is specified, it must be a valid quoteId that was previously returned by the GetPayoutQuote method.
     * If the quoteId is not specified, the network will try to find a suitable quote for the payout currency and amount,
     * same way as GetPayoutQuote rpc.
     * </pre>
     */
    default void createPayment(network.t0.sdk.proto.tzero.v1.payment.CreatePaymentRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.CreatePaymentResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getCreatePaymentMethod(), responseObserver);
    }

    /**
     * <pre>
     **
     * Inform the network that a payout has been completed. This endpoint is called by the payout
     * provider, specifying the payment ID and payout ID, which was provided when the payout request was made to this provider.
     * </pre>
     */
    @java.lang.Deprecated
    default void confirmPayout(network.t0.sdk.proto.tzero.v1.payment.ConfirmPayoutRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.ConfirmPayoutResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getConfirmPayoutMethod(), responseObserver);
    }

    /**
     */
    default void finalizePayout(network.t0.sdk.proto.tzero.v1.payment.FinalizePayoutRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.FinalizePayoutResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getFinalizePayoutMethod(), responseObserver);
    }

    /**
     * <pre>
     **
     * Pay-out provider reports the result of manual AML check.
     * This endpoint is called after the manual AML check is completed. The network will find the new best quotes for the
     * payment and will return the updated settlement/payout amount along with the updated quotes in the response.
     * </pre>
     */
    default void completeManualAmlCheck(network.t0.sdk.proto.tzero.v1.payment.CompleteManualAmlCheckRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.CompleteManualAmlCheckResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getCompleteManualAmlCheckMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service NetworkService.
   * <pre>
   **
   * This service is used by provider to interact with the Network, e.g. push quotes and initiate payments.
   * All methods of this service are idempotent, meaning they are safe to retry and multiple calls with the same parameters will have no additional effect.
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
   * This service is used by provider to interact with the Network, e.g. push quotes and initiate payments.
   * All methods of this service are idempotent, meaning they are safe to retry and multiple calls with the same parameters will have no additional effect.
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
     * Used by the provider to publish pay-in and pay-out quotes (FX rates) into the network.
     * These quotes include tiered pricing bands and an expiration timestamp.
     * </pre>
     */
    public void updateQuote(network.t0.sdk.proto.tzero.v1.payment.UpdateQuoteRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.UpdateQuoteResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getUpdateQuoteMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     **
     * Request the best available quote for a payout in a specific currency, for a given amount.
     * If the payout quote exists, but the credit limit is exceeded, this quote will not be considered.
     * </pre>
     */
    public void getQuote(network.t0.sdk.proto.tzero.v1.payment.GetQuoteRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.GetQuoteResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetQuoteMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     **
     * Submit a request to create a new payment for the specified pay-out currency.
     * QuoteId is the optional parameter.
     * If the quoteID is specified, it must be a valid quoteId that was previously returned by the GetPayoutQuote method.
     * If the quoteId is not specified, the network will try to find a suitable quote for the payout currency and amount,
     * same way as GetPayoutQuote rpc.
     * </pre>
     */
    public void createPayment(network.t0.sdk.proto.tzero.v1.payment.CreatePaymentRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.CreatePaymentResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getCreatePaymentMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     **
     * Inform the network that a payout has been completed. This endpoint is called by the payout
     * provider, specifying the payment ID and payout ID, which was provided when the payout request was made to this provider.
     * </pre>
     */
    @java.lang.Deprecated
    public void confirmPayout(network.t0.sdk.proto.tzero.v1.payment.ConfirmPayoutRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.ConfirmPayoutResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getConfirmPayoutMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void finalizePayout(network.t0.sdk.proto.tzero.v1.payment.FinalizePayoutRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.FinalizePayoutResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getFinalizePayoutMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     **
     * Pay-out provider reports the result of manual AML check.
     * This endpoint is called after the manual AML check is completed. The network will find the new best quotes for the
     * payment and will return the updated settlement/payout amount along with the updated quotes in the response.
     * </pre>
     */
    public void completeManualAmlCheck(network.t0.sdk.proto.tzero.v1.payment.CompleteManualAmlCheckRequest request,
        io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.CompleteManualAmlCheckResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getCompleteManualAmlCheckMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service NetworkService.
   * <pre>
   **
   * This service is used by provider to interact with the Network, e.g. push quotes and initiate payments.
   * All methods of this service are idempotent, meaning they are safe to retry and multiple calls with the same parameters will have no additional effect.
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
     * Used by the provider to publish pay-in and pay-out quotes (FX rates) into the network.
     * These quotes include tiered pricing bands and an expiration timestamp.
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment.UpdateQuoteResponse updateQuote(network.t0.sdk.proto.tzero.v1.payment.UpdateQuoteRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getUpdateQuoteMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     **
     * Request the best available quote for a payout in a specific currency, for a given amount.
     * If the payout quote exists, but the credit limit is exceeded, this quote will not be considered.
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment.GetQuoteResponse getQuote(network.t0.sdk.proto.tzero.v1.payment.GetQuoteRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getGetQuoteMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     **
     * Submit a request to create a new payment for the specified pay-out currency.
     * QuoteId is the optional parameter.
     * If the quoteID is specified, it must be a valid quoteId that was previously returned by the GetPayoutQuote method.
     * If the quoteId is not specified, the network will try to find a suitable quote for the payout currency and amount,
     * same way as GetPayoutQuote rpc.
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment.CreatePaymentResponse createPayment(network.t0.sdk.proto.tzero.v1.payment.CreatePaymentRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getCreatePaymentMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     **
     * Inform the network that a payout has been completed. This endpoint is called by the payout
     * provider, specifying the payment ID and payout ID, which was provided when the payout request was made to this provider.
     * </pre>
     */
    @java.lang.Deprecated
    public network.t0.sdk.proto.tzero.v1.payment.ConfirmPayoutResponse confirmPayout(network.t0.sdk.proto.tzero.v1.payment.ConfirmPayoutRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getConfirmPayoutMethod(), getCallOptions(), request);
    }

    /**
     */
    public network.t0.sdk.proto.tzero.v1.payment.FinalizePayoutResponse finalizePayout(network.t0.sdk.proto.tzero.v1.payment.FinalizePayoutRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getFinalizePayoutMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     **
     * Pay-out provider reports the result of manual AML check.
     * This endpoint is called after the manual AML check is completed. The network will find the new best quotes for the
     * payment and will return the updated settlement/payout amount along with the updated quotes in the response.
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment.CompleteManualAmlCheckResponse completeManualAmlCheck(network.t0.sdk.proto.tzero.v1.payment.CompleteManualAmlCheckRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getCompleteManualAmlCheckMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service NetworkService.
   * <pre>
   **
   * This service is used by provider to interact with the Network, e.g. push quotes and initiate payments.
   * All methods of this service are idempotent, meaning they are safe to retry and multiple calls with the same parameters will have no additional effect.
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
     * Used by the provider to publish pay-in and pay-out quotes (FX rates) into the network.
     * These quotes include tiered pricing bands and an expiration timestamp.
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment.UpdateQuoteResponse updateQuote(network.t0.sdk.proto.tzero.v1.payment.UpdateQuoteRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getUpdateQuoteMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     **
     * Request the best available quote for a payout in a specific currency, for a given amount.
     * If the payout quote exists, but the credit limit is exceeded, this quote will not be considered.
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment.GetQuoteResponse getQuote(network.t0.sdk.proto.tzero.v1.payment.GetQuoteRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetQuoteMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     **
     * Submit a request to create a new payment for the specified pay-out currency.
     * QuoteId is the optional parameter.
     * If the quoteID is specified, it must be a valid quoteId that was previously returned by the GetPayoutQuote method.
     * If the quoteId is not specified, the network will try to find a suitable quote for the payout currency and amount,
     * same way as GetPayoutQuote rpc.
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment.CreatePaymentResponse createPayment(network.t0.sdk.proto.tzero.v1.payment.CreatePaymentRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCreatePaymentMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     **
     * Inform the network that a payout has been completed. This endpoint is called by the payout
     * provider, specifying the payment ID and payout ID, which was provided when the payout request was made to this provider.
     * </pre>
     */
    @java.lang.Deprecated
    public network.t0.sdk.proto.tzero.v1.payment.ConfirmPayoutResponse confirmPayout(network.t0.sdk.proto.tzero.v1.payment.ConfirmPayoutRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getConfirmPayoutMethod(), getCallOptions(), request);
    }

    /**
     */
    public network.t0.sdk.proto.tzero.v1.payment.FinalizePayoutResponse finalizePayout(network.t0.sdk.proto.tzero.v1.payment.FinalizePayoutRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getFinalizePayoutMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     **
     * Pay-out provider reports the result of manual AML check.
     * This endpoint is called after the manual AML check is completed. The network will find the new best quotes for the
     * payment and will return the updated settlement/payout amount along with the updated quotes in the response.
     * </pre>
     */
    public network.t0.sdk.proto.tzero.v1.payment.CompleteManualAmlCheckResponse completeManualAmlCheck(network.t0.sdk.proto.tzero.v1.payment.CompleteManualAmlCheckRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCompleteManualAmlCheckMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service NetworkService.
   * <pre>
   **
   * This service is used by provider to interact with the Network, e.g. push quotes and initiate payments.
   * All methods of this service are idempotent, meaning they are safe to retry and multiple calls with the same parameters will have no additional effect.
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
     * Used by the provider to publish pay-in and pay-out quotes (FX rates) into the network.
     * These quotes include tiered pricing bands and an expiration timestamp.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<network.t0.sdk.proto.tzero.v1.payment.UpdateQuoteResponse> updateQuote(
        network.t0.sdk.proto.tzero.v1.payment.UpdateQuoteRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getUpdateQuoteMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     **
     * Request the best available quote for a payout in a specific currency, for a given amount.
     * If the payout quote exists, but the credit limit is exceeded, this quote will not be considered.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<network.t0.sdk.proto.tzero.v1.payment.GetQuoteResponse> getQuote(
        network.t0.sdk.proto.tzero.v1.payment.GetQuoteRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetQuoteMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     **
     * Submit a request to create a new payment for the specified pay-out currency.
     * QuoteId is the optional parameter.
     * If the quoteID is specified, it must be a valid quoteId that was previously returned by the GetPayoutQuote method.
     * If the quoteId is not specified, the network will try to find a suitable quote for the payout currency and amount,
     * same way as GetPayoutQuote rpc.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<network.t0.sdk.proto.tzero.v1.payment.CreatePaymentResponse> createPayment(
        network.t0.sdk.proto.tzero.v1.payment.CreatePaymentRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getCreatePaymentMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     **
     * Inform the network that a payout has been completed. This endpoint is called by the payout
     * provider, specifying the payment ID and payout ID, which was provided when the payout request was made to this provider.
     * </pre>
     */
    @java.lang.Deprecated
    public com.google.common.util.concurrent.ListenableFuture<network.t0.sdk.proto.tzero.v1.payment.ConfirmPayoutResponse> confirmPayout(
        network.t0.sdk.proto.tzero.v1.payment.ConfirmPayoutRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getConfirmPayoutMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<network.t0.sdk.proto.tzero.v1.payment.FinalizePayoutResponse> finalizePayout(
        network.t0.sdk.proto.tzero.v1.payment.FinalizePayoutRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getFinalizePayoutMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     **
     * Pay-out provider reports the result of manual AML check.
     * This endpoint is called after the manual AML check is completed. The network will find the new best quotes for the
     * payment and will return the updated settlement/payout amount along with the updated quotes in the response.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<network.t0.sdk.proto.tzero.v1.payment.CompleteManualAmlCheckResponse> completeManualAmlCheck(
        network.t0.sdk.proto.tzero.v1.payment.CompleteManualAmlCheckRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getCompleteManualAmlCheckMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_UPDATE_QUOTE = 0;
  private static final int METHODID_GET_QUOTE = 1;
  private static final int METHODID_CREATE_PAYMENT = 2;
  private static final int METHODID_CONFIRM_PAYOUT = 3;
  private static final int METHODID_FINALIZE_PAYOUT = 4;
  private static final int METHODID_COMPLETE_MANUAL_AML_CHECK = 5;

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
          serviceImpl.updateQuote((network.t0.sdk.proto.tzero.v1.payment.UpdateQuoteRequest) request,
              (io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.UpdateQuoteResponse>) responseObserver);
          break;
        case METHODID_GET_QUOTE:
          serviceImpl.getQuote((network.t0.sdk.proto.tzero.v1.payment.GetQuoteRequest) request,
              (io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.GetQuoteResponse>) responseObserver);
          break;
        case METHODID_CREATE_PAYMENT:
          serviceImpl.createPayment((network.t0.sdk.proto.tzero.v1.payment.CreatePaymentRequest) request,
              (io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.CreatePaymentResponse>) responseObserver);
          break;
        case METHODID_CONFIRM_PAYOUT:
          serviceImpl.confirmPayout((network.t0.sdk.proto.tzero.v1.payment.ConfirmPayoutRequest) request,
              (io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.ConfirmPayoutResponse>) responseObserver);
          break;
        case METHODID_FINALIZE_PAYOUT:
          serviceImpl.finalizePayout((network.t0.sdk.proto.tzero.v1.payment.FinalizePayoutRequest) request,
              (io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.FinalizePayoutResponse>) responseObserver);
          break;
        case METHODID_COMPLETE_MANUAL_AML_CHECK:
          serviceImpl.completeManualAmlCheck((network.t0.sdk.proto.tzero.v1.payment.CompleteManualAmlCheckRequest) request,
              (io.grpc.stub.StreamObserver<network.t0.sdk.proto.tzero.v1.payment.CompleteManualAmlCheckResponse>) responseObserver);
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
              network.t0.sdk.proto.tzero.v1.payment.UpdateQuoteRequest,
              network.t0.sdk.proto.tzero.v1.payment.UpdateQuoteResponse>(
                service, METHODID_UPDATE_QUOTE)))
        .addMethod(
          getGetQuoteMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              network.t0.sdk.proto.tzero.v1.payment.GetQuoteRequest,
              network.t0.sdk.proto.tzero.v1.payment.GetQuoteResponse>(
                service, METHODID_GET_QUOTE)))
        .addMethod(
          getCreatePaymentMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              network.t0.sdk.proto.tzero.v1.payment.CreatePaymentRequest,
              network.t0.sdk.proto.tzero.v1.payment.CreatePaymentResponse>(
                service, METHODID_CREATE_PAYMENT)))
        .addMethod(
          getConfirmPayoutMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              network.t0.sdk.proto.tzero.v1.payment.ConfirmPayoutRequest,
              network.t0.sdk.proto.tzero.v1.payment.ConfirmPayoutResponse>(
                service, METHODID_CONFIRM_PAYOUT)))
        .addMethod(
          getFinalizePayoutMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              network.t0.sdk.proto.tzero.v1.payment.FinalizePayoutRequest,
              network.t0.sdk.proto.tzero.v1.payment.FinalizePayoutResponse>(
                service, METHODID_FINALIZE_PAYOUT)))
        .addMethod(
          getCompleteManualAmlCheckMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              network.t0.sdk.proto.tzero.v1.payment.CompleteManualAmlCheckRequest,
              network.t0.sdk.proto.tzero.v1.payment.CompleteManualAmlCheckResponse>(
                service, METHODID_COMPLETE_MANUAL_AML_CHECK)))
        .build();
  }

  private static abstract class NetworkServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    NetworkServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return network.t0.sdk.proto.tzero.v1.payment.NetworkProto.getDescriptor();
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
              .addMethod(getUpdateQuoteMethod())
              .addMethod(getGetQuoteMethod())
              .addMethod(getCreatePaymentMethod())
              .addMethod(getConfirmPayoutMethod())
              .addMethod(getFinalizePayoutMethod())
              .addMethod(getCompleteManualAmlCheckMethod())
              .build();
        }
      }
    }
    return result;
  }
}
