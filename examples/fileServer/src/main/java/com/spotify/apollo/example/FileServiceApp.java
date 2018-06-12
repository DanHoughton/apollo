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
import org.apache.commons.fileupload.MultipartStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * This example demonstrates a simple file upload service.
 * <p>
 * It uses a synchronous route to accept and save a file that has been uploaded. It also includes a synchronous
 * middleware that translates uncaught exceptions into error code 500.
 * <p>
 * Try it by using a rest client tool and including a file as the body of a multipart/form-data POST request.
 *
 * The directory to save the files can be defined in the services configuration file.
 */
final class FileServiceApp {

    //The directory we want to save the uploaded files to.
    static String fileSaveDirectory = "./";
    static final String FILE_UPLOAD_PATH = "server.file.upload.dir";

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
        fileSaveDirectory = environment.config().getString(FILE_UPLOAD_PATH);

        //Create our request handler.
        SyncHandler<Response<String>> uploadHandler = context -> upload(context.request());

        //Create our routing engine entry
        environment.routingEngine().registerAutoRoute(
                Route.with(exceptionHandler(), //Catch thrown runtime exceptions with this handler.
                        "POST", //The HTTP method for the endpoint.
                        "/upload",  //The endpoint path.
                        uploadHandler)); //The actual method handling the upload.
    }

    /**
     * A request handling method meant to handle a file upload. Will currently handle a multipart/form-data message
     * following the RFC standard for mutlipart/form-data messages in HTTP. Outlined in RFC2388.
     *
     * @param req The {@link Request} object that has been sent to the server from the client.
     * @return An {@link Response} from the server containing a status code and short description of the action that
     * has occurred as a result of the clients request.
     */
    static Response upload(Request req) {
        //Check that a file was actually uploaded.
        if (req.payload().isPresent()) {
            Optional<String> fileName = Optional.empty(); //Create an empty optional for the time being.

            try {
                //Get the boundary string.
                Optional<String> boundary = parseHeader(req.header("Content-Type").get(), "boundary",
                        Optional.of(";"));

                //If we have one, this is probably a multipart/form-data message.
                if (boundary.isPresent()) {
                    ByteArrayInputStream payloadInputStream = new ByteArrayInputStream(req.payload().get().toByteArray());
                    MultipartStream payloadStream =
                            new MultipartStream(payloadInputStream, boundary.orElse("").getBytes());

                    //Use the stream parser to read the stream
                    boolean nextPart = payloadStream.skipPreamble(); //Skip the first boundary
                    while (nextPart) {
                        String header = payloadStream.readHeaders(); //Read the part form data header.
                        fileName = parseHeader(header, "filename", Optional.of(";|\n|\r\n"));

                        ByteArrayOutputStream fileBytes = new ByteArrayOutputStream();
                        payloadStream.readBodyData(fileBytes); //Read the file/form data into the byte stream.

                        saveFile(fileBytes.toByteArray(), fileName);

                        fileBytes.close();
                        nextPart = payloadStream.readBoundary();
                    }

                    payloadInputStream.close();
                } else {
                    //Just save the body as a file?
                    byte[] payloadBytes = req.payload().get().toByteArray();

                    saveFile(payloadBytes, fileName);
                }
            } catch (IOException e) {
                return Response.forStatus(Status.INTERNAL_SERVER_ERROR).withPayload("We failed to save the file, please try again.");
            }

            return Response.forStatus(Status.CREATED).withPayload("The file has been saved successfully.");
        } else {
            return Response.forStatus(Status.BAD_REQUEST).withPayload("No file data has been included in the request!");
        }
    }

    /**
     * Save the provided file bytes to disk.
     *
     * @param payloadBytes The bytes we are writing to disk.
     * @param fileName The name of the file we are saving. Will default to a UUID based on the payload bytes if the optional
     *                 isn't present.
     * @throws IOException Thrown if the file couldn't be written, if the folder isn't accessible.
     */
    private static void saveFile(byte[] payloadBytes, Optional<String> fileName) throws IOException {
        Files.write(Paths.get(
                fileSaveDirectory + fileName.orElse(UUID.nameUUIDFromBytes(payloadBytes).toString())), //The file and directory we are saving to.
                payloadBytes, //The bytes of the file that was uploaded.
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE); //The open options. Essentially create or overwrite.
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
     * Parse and retrieve a specific field from the provided multi value header passed into this function.
     *
     * @param multiValueHeader     A header containing multiple values.
     * @param fieldToRetrieve      The field we are trying to retrieve. This is a case insensitive argument.
     * @param delimitingTokenRegex A regular expression containing delimiting token(s) used to split up values in the
     *                             provided multi value header. (Will default to ';' if provided an empty optional)
     * @return an {@link Optional} containing the value for the provided field name if present. Otherwise the return
     * value will be an {@linkt Optional#empty}
     */
    private static Optional<String> parseHeader(String multiValueHeader, String fieldToRetrieve,
                                                Optional<String> delimitingTokenRegex) {
        Objects.requireNonNull(multiValueHeader, "A header must to be supplied to this method.");
        Objects.requireNonNull(fieldToRetrieve, "A field name must be supplied to this method.");

        String delimitingToken = delimitingTokenRegex.orElse(";");

        String[] contentDispositionArray = multiValueHeader.split(delimitingToken);
        String returnString = null; //Default to null.

        for (int i = 0; i < contentDispositionArray.length; i++) {
            if (contentDispositionArray[i].toLowerCase().contains(fieldToRetrieve)) {
                returnString = contentDispositionArray[i];
                //Start after the = sign and opening of the string and chop off the last quotation mark.
                //Kind of hacky
                returnString = returnString.substring(returnString.indexOf("="));
                returnString = returnString
                        .replaceAll("=", "")
                        .replaceAll(delimitingToken, "")
                        .replaceAll("\"", "");
            }
        }

        return Optional.ofNullable(returnString);
    }
}
