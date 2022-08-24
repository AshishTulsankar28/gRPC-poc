package com.example.grpc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.example.grpc.GreetingServiceOuterClass.HelloRequest;
import com.example.grpc.GreetingServiceOuterClass.HelloResponse;

import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

public class GreetingServiceImpl extends GreetingServiceGrpc.GreetingServiceImplBase {
	private static Logger log = LogManager.getLogger(GreetingServiceImpl.class);

	@Override
	public void greeting(GreetingServiceOuterClass.HelloRequest request,
			StreamObserver<GreetingServiceOuterClass.HelloResponse> responseObserver) {

		log.info("Received~ {}",request);

		// You must use a builder to construct a new Protobuffer object as response
		GreetingServiceOuterClass.HelloResponse response = GreetingServiceOuterClass.HelloResponse.newBuilder()
				.setGreeting("Hello, " + request.getName())
				.build();
		
		// Use responseObserver to send a single response back
		log.info("Sending~ {}",response);		
		responseObserver.onNext(response);

		// When you are done, you must call onCompleted.
		responseObserver.onCompleted();
	}

	@Override
	public void responsiveGreets(GreetingServiceOuterClass.HelloRequest request,
			StreamObserver<GreetingServiceOuterClass.HelloResponse> responseObserver) {

		log.info("Received~ {}",request);

		GreetingServiceOuterClass.HelloResponse response = GreetingServiceOuterClass.HelloResponse.newBuilder()
				.setGreeting("Hey, " + request.getName())
				.build();


		// Clients may invoke onNext at most once for server streaming calls, but may receive many onNext callback.
		log.info("Sending~ {}",response);		
		responseObserver.onNext(response);
		log.info("Sending~ {}",response);
		responseObserver.onNext(response);
		log.info("Sending~ {}",response);
		responseObserver.onNext(response);

		// When you are done, you must call onCompleted.
		responseObserver.onCompleted();
	}

	@Override
	public io.grpc.stub.StreamObserver<com.example.grpc.GreetingServiceOuterClass.HelloRequest> bidirGreets(
			io.grpc.stub.StreamObserver<com.example.grpc.GreetingServiceOuterClass.HelloResponse> responseObserver) {

		final ServerCallStreamObserver<HelloResponse> serverCallStreamObserver =(ServerCallStreamObserver<HelloResponse>) responseObserver;
		// Swaps to manual flow control
		serverCallStreamObserver.disableAutoRequest();

		/**
		 *Set up a back-pressure-aware consumer for the request stream. The onReadyHandler will be invoked
		 *when the consuming side has enough buffer space to receive more messages.
		 *Note: the onReadyHandler's invocation is serialized on the same thread pool as the incoming StreamObserver's
		 *onNext(), onError(), and onComplete() handlers. Blocking the onReadyHandler will prevent additional messages
		 *from being processed by the incoming StreamObserver. The onReadyHandler must return in a timely manner or
		 *else message processing throughput will suffer.
		 */
		class OnReadyHandler implements Runnable {

			/**
			 * TODO understanding the manual flow 
			 *Guard against spurious onReady() calls caused by a race between onNext() and onReady(). If the transport
			 *toggles isReady() from false to true while onNext() is executing, but before onNext() checks isReady(),
			 *request(1) would be called twice - once by onNext() and once by the onReady() scheduled during onNext()'s
			 *execution.
			 */
			private boolean wasReady = false;

			@Override
			public void run() {
				if (serverCallStreamObserver.isReady() && !wasReady) {
					wasReady = true;
					log.info("Ready to stream");
					// Signal the request sender to send one message. This happens when isReady() turns true, signaling that
					// the receive buffer has enough free space to receive more messages. Calling request() serves to prime
					// the message pump.
					serverCallStreamObserver.request(1);
				}
			}
		}

		final OnReadyHandler onReadyHandler = new OnReadyHandler();
		serverCallStreamObserver.setOnReadyHandler(onReadyHandler);

		return new StreamObserver<GreetingServiceOuterClass.HelloRequest>() {

			@Override
			public void onNext(HelloRequest request) {
				// Process the request and send a response or an error.
				try {
					// Accept and enqueue the request.
					String name = request.getName();
					log.info("Received~ {} ",request);

					// Simulate server "work"
					Thread.sleep(100);

					// Send a response.
					String message = "Hi " + name;
					HelloResponse reply = HelloResponse.newBuilder().setGreeting(message).build();
					log.info("Sending~ {}",reply);
					responseObserver.onNext(reply);

					// Check the provided ServerCallStreamObserver to see if it is still ready to accept more messages.
					if (serverCallStreamObserver.isReady()) {
						// Signal the sender to send another request. As long as isReady() stays true, the server will keep
						// cycling through the loop of onNext() -> request(1)...onNext() -> request(1)... until the client runs
						// out of messages and ends the loop (via onCompleted()).
						//
						// If request() was called here with the argument of more than 1, the server might runs out of receive
						// buffer space, and isReady() will turn false. When the receive buffer has sufficiently drained,
						// isReady() will turn true, and the serverCallStreamObserver's onReadyHandler will be called to restart
						// the message pump.
						serverCallStreamObserver.request(1);
					} else {
						// If not, note that back-pressure has begun.
						onReadyHandler.wasReady = false;
					}
				} catch (Throwable throwable) {
					throwable.printStackTrace();
					responseObserver.onError(
							Status.UNKNOWN.withDescription("Error handling request").withCause(throwable).asException());
				}
			}

			@Override
			public void onError(Throwable t) {
				// End the response stream if the client presents an error.
				log.trace("onError~ {}",t);
				responseObserver.onCompleted();
			}

			@Override
			public void onCompleted() {
				// Signal the end of work when the client ends the request stream.
				log.info("Streaming done");
				responseObserver.onCompleted();
			}

		};
	}

	@Override
	public io.grpc.stub.StreamObserver<com.example.grpc.GreetingServiceOuterClass.HelloRequest> requestingGreets(
			io.grpc.stub.StreamObserver<com.example.grpc.GreetingServiceOuterClass.HelloResponse> responseObserver) {
			
		StringBuilder sb=new StringBuilder(50);
		return new StreamObserver<GreetingServiceOuterClass.HelloRequest>() {

			@Override
			public void onNext(HelloRequest value) {
				sb.append(value.getName());
				log.info("Received~ {} "+value);
			}

			@Override
			public void onError(Throwable t) {
				log.trace("onError~ {}",t);
				responseObserver.onCompleted();
			}

			@Override
			public void onCompleted() {
				
				GreetingServiceOuterClass.HelloResponse response = GreetingServiceOuterClass.HelloResponse.newBuilder()
						.setGreeting(sb.toString())
						.build();
				log.info("Sending~ {} ",response);
				responseObserver.onNext(response);
				responseObserver.onCompleted();
			}
		};
	}
}