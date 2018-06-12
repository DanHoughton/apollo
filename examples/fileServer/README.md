## A simple file upload service

### Build
`mvn package`

### Run
`java -jar target/file-service.jar`

***Note that the directory for the image uploads must exist before the service will actually save the files.***

### Call
```
curl --request POST \
  --url http://localhost:8080/upload \
  --header 'content-disposition: form-data; name="uploadField"; filename="myFileName.jpg"' \
  --header 'content-type: multipart/form-data; boundary=---011000010111000001101001' \
  --form myFile="/complete/path/to/your/file"

The file has been saved successfully.
```