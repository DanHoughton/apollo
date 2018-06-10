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
import com.sun.xml.internal.messaging.saaj.packaging.mime.internet.ContentDisposition;
import com.sun.xml.internal.messaging.saaj.packaging.mime.internet.ParseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
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
        //Most implementations on a browser should include a "Content-Disposition" header that will contain
        // useful information about what the file name is. This is defined in the MIME RFCs however it is also
        // commonly used in HTTP requests containing binary data or multi-part form data.
        Map<String, String> headers = req.headers();

        //Check that a file was actually uploaded.
        if (req.payload().isPresent()) {
            byte[] payloadBytes = req.payload().get().toByteArray();
            String fileName = UUID.nameUUIDFromBytes(payloadBytes).toString(); //Create a UUID for the time being.
            String contentDispositionString = headers.get("Content-Disposition");

            if(contentDispositionString != null && !contentDispositionString.isEmpty()) {
                try {
                    ContentDisposition contentDisposition = new ContentDisposition(contentDispositionString);
                    fileName = contentDisposition.getParameter("filename");
                } catch (ParseException e) {
                    throw new RuntimeException("The content disposition header is improperly formatted.");
                }
            }

            try {
                Files.write(Paths.get(
                        fileSaveDirectory + fileName), //The file and directory we are saving to.
                        payloadBytes, //The bytes of the file that was uploaded.
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.CREATE); //The open options.
            } catch (IOException e) {
                return Response.forStatus(Status.INTERNAL_SERVER_ERROR).withPayload("We failed to save the file," +
                        " please try again.");
            }
            return Response.forStatus(Status.CREATED).withPayload("The file has been saved successfully.");
        } else {
            return Response.forStatus(Status.BAD_REQUEST).withPayload("No file data has been included in the request!");
        }
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
}
