package com.spotify.apollo.example;

import com.spotify.apollo.Environment;
import com.spotify.apollo.Request;
import com.spotify.apollo.Response;
import com.spotify.apollo.Status;
import com.spotify.apollo.httpservice.HttpService;
import com.spotify.apollo.httpservice.LoadingException;
import com.spotify.apollo.route.AsyncHandler;
import com.spotify.apollo.route.Middleware;
import com.spotify.apollo.route.Route;
import com.spotify.apollo.route.SyncHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * This example demonstrates a simple file upload service.
 * <p>
 * It uses a synchronous route to accept and save a file that has been uploaded. It also includes a synchronous
 * middleware that translates uncaught exceptions into error code 500.
 * <p>
 * Try it by using a rest client tool and including a file as the body of a PUT request.
 * Note that this code is not currently reading any additional headers that will state the file type or file name.
 */
final class FileServiceApp {

    //The directory we want to save the uploaded files to.
    static String fileSaveDirectory = "./";

    public static void main(String... args) throws LoadingException {
        HttpService.boot(FileServiceApp::init, "file-service", args);
    }

    /**
     * Initialize the service endpoints.
     *
     * @param environment the environmental arguments that are provided from the configuration file.
     */
    static void init(Environment environment) {
        //Get the file save directory from the file-service.conf file that is located in the resources folder.
        fileSaveDirectory = environment.config().getString("server.file.upload.dir");

        //Create our request handler.
        SyncHandler<Response<String>> uploadHandler = context -> upload(context.request());

        //Create our routing engine entry
        environment.routingEngine().registerAutoRoute(
                Route.with(exceptionHandler(), //Catch thrown runtime exceptions with this handler.
                        "PUT", //The HTTP method for the endpoint.
                        "/upload",  //The endpoint path.
                        uploadHandler)); //The actual method handling the upload.
    }

    /**
     * A request handling method meant to handle a file upload. Currently the file is just saved with the provided file
     * name or we generate a basic file UUID name, this could then be mapped to some kind of data storage tool to be
     * handled more elegantly.
     *
     * @param req The {@link Request} object that has been sent to the server from the client.
     * @return An {@link Response} from the server containing a status code and short description of the action that
     * has occured as a result of the clients reuqest.
     */
    static Response upload(Request req) {
        //Check that a file was actually uploaded. And get the content disposition if it's present.
        if (req.payload().isPresent()) {
            byte[] payloadBytes = req.payload().get().toByteArray();
            Optional<String> fileName = Optional.empty(); //Create a UUID for the time being.

            //Check for a content disposition header.
            Optional<String> contentDispositionString = req.header("content-disposition");
            if(contentDispositionString.isPresent()) {
                //Parse the content disposition.
                //Unfortunately there is no current support for this particular header, so it will need to be
                //parsed manually.
                fileName = tokenizableHeader(contentDispositionString.get(), "filename", ";");
            }

            try {
                String payloadString = Arrays.toString(payloadBytes);
                String[] payloadParts = null;
                Optional<String> boundry = tokenizableHeader(req.header("Content-Type").get(), "boundry", ";");

                if(boundry.isPresent()) {
                    payloadParts = payloadString.split(boundry.get());
                }

                //Need to handle all the parts
                if(payloadParts != null) {
                    for(int i = 0; i < payloadParts.length; i++) {
                        String currentPart = payloadParts[i];
                        currentPart = currentPart.replaceAll(boundry.get(), ""); //Remove the bounds
                        String partHeader = currentPart.substring(0, currentPart.indexOf("\r\n\r\n"));//grab all text before the first empty line
                        fileName = tokenizableHeader(partHeader, "filename", ";");;
                    }
                } else {



                    saveFile(payloadBytes, fileName);
                }
            } catch (IOException e) {
                return Response.forStatus(Status.INTERNAL_SERVER_ERROR).withPayload("We failed to save the file," +
                        " please try again.");
            }
            return Response.forStatus(Status.CREATED).withPayload("The file has been saved successfully.");
        } else {
            return Response.forStatus(Status.BAD_REQUEST).withPayload("No file data has been included in the request!");
        }
    }

    private static void saveFile(byte[] payloadBytes, Optional<String> fileName) throws IOException {
        Files.write(Paths.get(
                fileSaveDirectory + fileName.orElse(UUID.nameUUIDFromBytes(payloadBytes).toString())), //The file and directory we are saving to.
                payloadBytes, //The bytes of the file that was uploaded.
                StandardOpenOption.CREATE_NEW, StandardOpenOption.CREATE); //The open options.
    }

    /**
     * A generic middleware that maps uncaught exceptions to error code 500
     */
    static <T> Middleware<SyncHandler<Response<T>>, SyncHandler<Response<T>>> exceptionMiddleware() {
        return handler -> requestContext -> {
            try {
                return handler.invoke(requestContext);
            } catch (RuntimeException e) {
                return Response.forStatus(Status.INTERNAL_SERVER_ERROR);
            }
        };
    }

    /**
     * Async version of {@link #exceptionMiddleware()}
     */
    static <T> Middleware<SyncHandler<Response<T>>, AsyncHandler<Response<T>>> exceptionHandler() {
        return FileServiceApp.<T>exceptionMiddleware().and(Middleware::syncToAsync);
    }

    /**
     * Parse and retrieve a specific filed from the content disposition header passed into this function.
     *
     * @param multiValueHeader The content dispotions following the format  outlined in EFC 6266
     * @param fieldToRetrieve The filed we are trying to retrieve. This is a case insensitive argument.
     * @return an {@link Optional} containing the value for the provided field name if present. Otherwise the return
     * value will be an {@linkt Optional#empty}
     */
    static Optional<String> tokenizableHeader(String multiValueHeader, String fieldToRetrieve, String delimitingToken) {
        Objects.requireNonNull(multiValueHeader, "A Content-Disposition header needs to be supplied to this method.");
        Objects.requireNonNull(fieldToRetrieve, "A content disposition field name must be supplied to this method.");

        String[] contentDispositionArray = multiValueHeader.split(delimitingToken);
        String returnString = null; //Default to null.

        for(int i = 0; i < contentDispositionArray.length; i++) {
            if(contentDispositionArray[i].toLowerCase().contains(fieldToRetrieve)) {
                returnString = contentDispositionArray[i];
                //Start after the = sign and opening of the string and chop off the last quotation mark.
                returnString = returnString.substring(returnString.indexOf("="));
                returnString = returnString.replaceAll("=", "").replaceAll(delimitingToken, "");
            }
        }

        return Optional.ofNullable(returnString);
    }
}
