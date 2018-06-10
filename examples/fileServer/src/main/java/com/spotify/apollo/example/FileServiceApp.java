package com.spotify.apollo.example;

import com.google.protobuf250.DescriptorProtos;
import com.spotify.apollo.*;
import com.spotify.apollo.httpservice.HttpService;
import com.spotify.apollo.httpservice.LoadingException;
import com.spotify.apollo.route.AsyncHandler;
import com.spotify.apollo.route.Middleware;
import com.spotify.apollo.route.Route;
import com.spotify.apollo.route.SyncHandler;
import okio.ByteString;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * This example demonstrates a simple calculator service.
 *
 * It uses a synchronous route to evaluate addition and a middleware
 * that translates uncaught exceptions into error code 418.
 *
 * Try it by calling it with query parameters that are not parsable integers.
 */
final class CalculatorApp {

  public static void main(String... args) throws LoadingException {
    HttpService.boot(CalculatorApp::init, "calculator-service", args);
  }

  static void init(Environment environment) {
    SyncHandler<Response<Integer>> addHandler = context -> add(context.request());

    SyncHandler<Response<String>>  uploadHandler = context -> upload(context.request());

    environment.routingEngine()
        .registerAutoRoute(Route.with(exceptionHandler(), "GET", "/add", addHandler))
        .registerAutoRoute(Route.sync("GET", "/unsafeadd", addHandler));

    environment.routingEngine().registerAutoRoute(Route.with(exceptionHandler(), "PUT", "/upload", uploadHandler));
  }

  static Response upload(Request req) {
    Map<String, String> headers = req.headers();

    //Check that a file was actually uploaded.
    if(req.payload().isPresent()) {
      ByteString payload = req.payload().get();
      byte[] jpegFile = payload.toByteArray();
      try {
        Files.write(Paths.get(
                "D:\\uploadFiles\\" + UUID.nameUUIDFromBytes(jpegFile)), //The file and directory we are saving to.
                jpegFile, //The bytes of the file that was uploaded.
                StandardOpenOption.CREATE_NEW, StandardOpenOption.CREATE); //The open options.
      } catch (IOException e) {
        return Response.forStatus(Status.INTERNAL_SERVER_ERROR).withPayload("We failed to save the file, please try again.");
      }
      return Response.forStatus(Status.CREATED).withPayload("Thanks, come again.");
    } else {
      return Response.forStatus(Status.BAD_REQUEST).withPayload("No file data has been included in the request!");
    }
  }

  /**
   * A simple adder of request parameters {@code t1} and {@code t2}
   *
   * @param request  The request to handle the addition for
   * @return A response of an integer representing the sum
   */
  static Response<Integer> add(Request request) {
    Optional<String> t1 = request.parameter("t1");
    Optional<String> t2 = request.parameter("t2");
    if (t1.isPresent() && t2.isPresent()) {
      int result = Integer.valueOf(t1.get()) + Integer.valueOf(t2.get());
      return Response.forPayload(result);
    } else {
      return Response.forStatus(Status.BAD_REQUEST);
    }
  }

  /**
   * A generic middleware that maps uncaught exceptions to error code 418
   */
  static <T> Middleware<SyncHandler<Response<T>>, SyncHandler<Response<T>>> exceptionMiddleware() {
    return handler -> requestContext -> {
      try {
        return handler.invoke(requestContext);
      } catch (RuntimeException e) {
        return Response.forStatus(Status.IM_A_TEAPOT);
      }
    };
  }

  /**
   * Async version of {@link #exceptionMiddleware()}
   */
  static <T> Middleware<SyncHandler<Response<T>>, AsyncHandler<Response<T>>> exceptionHandler() {
    return CalculatorApp.<T>exceptionMiddleware().and(Middleware::syncToAsync);
  }
}
