## A simple file upload service

### Build
`mvn package`

### Run
`java -jar target/file-service.jar`

### Call
```
curl --request PUT \
  --url http://localhost:8080/upload \
  --header 'content-disposition: form-data; name="uploadField"; filename="myFileName.jpg"' \
  --header 'content-type: multipart/form-data; boundary=---011000010111000001101001' \
  --form myFile="/complete/path/to/your/file"

The file has been saved successfully.
```
