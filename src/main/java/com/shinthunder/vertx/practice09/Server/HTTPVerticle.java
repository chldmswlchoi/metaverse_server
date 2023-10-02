package com.shinthunder.vertx.practice09.Server;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.shinthunder.vertx.practice09.Object.ChatItem;
import io.vertx.core.*;
import io.vertx.core.eventbus.Message;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static com.shinthunder.vertx.practice09.Server.MainServer.*;

public class HTTPVerticle extends AbstractVerticle {
    // -------------------------- CONSTANTS --------------------------
    private static final Logger logger = LoggerFactory.getLogger(HTTPVerticle.class);

    // -------------------------- MEMBER VARIABLES --------------------------
    private boolean awaitingImage = false;
    private Integer roomAwaitingImage = null;

    // -------------------------- HTTP HANDLER (HTTPVerticle <-> Client) --------------------------
    @Override
    public void start() {
        configureHttpServer();
        setupImageHandler();
    }

    private void configureHttpServer() {
        vertx.createHttpServer().requestHandler(this::httpHandler).listen(HTTP_PORT, res -> {
            if (res.succeeded()) {
                logger.info("HTTP server running on port {}", HTTP_PORT);
            } else {
                logger.error("Failed to start HTTP server", res.cause());
            }
        });
    }

    private void httpHandler(HttpServerRequest request) {
        // Add CORS headers
//        HttpServerResponse response = request.response();
        request.response().putHeader("Access-Control-Allow-Origin", "*");
        request.response().putHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        request.response().putHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        // Handle CORS preflight requests
        if (request.method() == HttpMethod.OPTIONS) {
            request.response().setStatusCode(204).end();
            return;
        }

        logger.info("{} '{}' {}", request.method(), request.path(), request.remoteAddress());

        if (request.path().equals("/upload") && request.method() == HttpMethod.POST) {
            upload(request);
        } else if (request.path().startsWith("/download/")) {
            String sanitizedPath = request.path().substring(10).replaceAll("/", "");
            System.out.println("11111111111111111111 11111111111111111111 ");
            download(sanitizedPath, request);
        } else {
            System.out.println("22222222222222222222 22222222222222222222 ");
            request.response().setStatusCode(404).end();
        }
    }

    //
//    private void upload(HttpServerRequest request) {
//        request.setExpectMultipart(true);
//        request.uploadHandler(upload -> {
//            String fileType = upload.filename().substring(upload.filename().lastIndexOf("."));
//            String filename = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + fileType;
//
//            if (!SUPPORTED_FILE_TYPES.contains(fileType.toLowerCase())) {
//                request.response().setStatusCode(400).end("Unsupported file type. Supported types: " + SUPPORTED_FILE_TYPES);
//                return;
//            }
//
//            if (upload.size() > MAX_FILE_SIZE) {
//                request.response().setStatusCode(413).end("File too large.");
//                return;
//            }
//            System.out.println("==================================================");
//            System.out.println("==================================================");
//            System.out.println("==================================================");
//            System.out.println("==================================================");
//            System.out.println("==================================================");
//            System.out.println("==================================================\n");
//            System.out.println("    filename    : " + filename);
//            System.out.println("==================================================");
//            System.out.println("==================================================");
//            System.out.println("==================================================");
//            System.out.println("==================================================");
//            System.out.println("==================================================");
//            System.out.println("==================================================");
//            upload.streamToFileSystem(uploadDirectory + filename);
//            upload.endHandler(v -> {
//                request.response().setStatusCode(200).setStatusMessage(filename).end("File uploaded successfully as " + filename);
//            }).exceptionHandler(e -> {
//                request.response().setStatusCode(500).end("File upload failed.");
//                System.out.println("error : " + e.getMessage());
//                this.awaitingImage = false;
//                this.roomAwaitingImage = null;
//            });
//        });
//    }

    private void upload(HttpServerRequest request) {
        request.setExpectMultipart(true);
        request.uploadHandler(upload -> {
            ByteArrayOutputStream bufferStream = new ByteArrayOutputStream();

            String fileType = upload.filename().substring(upload.filename().lastIndexOf('.'));
            String filename = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + fileType;
            String key = "chat_images/" + filename;
//            String accessKeyId = System.getenv("accessKeyId");
//            String secretAccessKey = System.getenv("secretAccessKey");
            String accessKeyId = "AKIAXHRNW3CUZCO2QGD3";
            String secretAccessKey = "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";

            BasicAWSCredentials awsCreds = new BasicAWSCredentials(accessKeyId, secretAccessKey);
            AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                    .withRegion("ap-northeast-2")
                    .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
                    .build();
            String mediaType = getMediaType(filename);

            upload.handler(buffer -> {
                try {
                    bufferStream.write(buffer.getBytes());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                logger.debug("Reading from buffer. Bytes available: {}", buffer.length());
            });

            upload.endHandler(v -> {
                logger.debug("Upload end handler invoked.");
                System.out.println("1");
                InputStream uploadStream = new ByteArrayInputStream(bufferStream.toByteArray());
                System.out.println("2");
                ObjectMetadata meta = new ObjectMetadata();
                meta.setContentType(mediaType);
                System.out.println("3");
                meta.setContentLength(bufferStream.size());
                System.out.println("4");

                PutObjectRequest putObjectRequest = new PutObjectRequest("linktown-contents", key, uploadStream, meta);
                System.out.println("5");
                s3Client.putObject(putObjectRequest);
                System.out.println("6");

                request.response().setStatusCode(200).setStatusMessage(filename).end("File uploaded successfully as " + filename);
                logger.debug("S3 upload successful. Filename: {}", filename);
            });

            upload.exceptionHandler(e -> {
                request.response().setStatusCode(500).end("File upload failed.");
                logger.error("Upload exception handler invoked. Error: {}", e.getMessage(), e);
            });
        });
    }

    private void download(String path, HttpServerRequest request) {
        String filePath = uploadDirectory + path;
//        String filePath =  path;
        System.out.println("    download filePath : " + filePath);
        //    download filePath : /home/teamnova0/workspace/testbed/shinji/chat-v3-image-upload-directory/$%7BfileName%7D
        if (!vertx.fileSystem().existsBlocking(filePath)) {
            request.response().setStatusCode(404).end();
            System.out.println("return 404");
            return;
        }
        OpenOptions opts = new OpenOptions().setRead(true);
        vertx.fileSystem().open(filePath, opts, ar -> {
            if (ar.succeeded()) {
                downloadFilePipe(ar.result(), request);
            } else {
                logger.error("Read failed", ar.cause());
                request.response().setStatusCode(500).end();
            }
        });
    }

    private void downloadFilePipe(AsyncFile file, HttpServerRequest request) {
        HttpServerResponse response = request.response();
        // Add CORS headers
        response.putHeader("Access-Control-Allow-Origin", "*");
        response.putHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.putHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        response.setStatusCode(200)
                .putHeader("Content-Type", getMediaType(request.path()))
                .setChunked(true);
        file.pipeTo(response);
    }

    private static String getMediaType(String filename) {
        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "txt" -> "text/plain";
            case "aac" -> "audio/aac";
            case "mp3" -> "audio/mpeg";
//            case "mp3" -> "audio/mp3";
            case "mp4" -> "video/mp4";
            case "mov" -> "video/quicktime";
            case "webm" -> "video/webm";
            default -> "application/octet-stream";
        };
    }

    // -------------------------- EVENTBUS HANDLER (EventBus <-> HTTPVerticle) --------------------------
    private void setupImageHandler() {
        vertx.eventBus().consumer(ADDRESS_IMAGE_ACTION, this::handleImageActions);
    }

    private void handleImageActions(Message<JsonObject> message) {
        String action = message.body().getString("action");
        switch (action) {
            case PREPARE_IMAGE_UPLOAD: // 이미지 업로드 준비 로직
                handlePrepareImageUpload(message);
                break;
            case IMAGE_UPLOADED: // 이미지가 업로드된 후의 처리 로직
                handleImageUploaded(message);
                break;
            default:
                message.reply(new JsonObject().put("error", "Unknown action"));
                break;
        }
    }

    private void handlePrepareImageUpload(Message<JsonObject> message) {

        if (message.body().getInteger("roomId") > 0) {
            this.awaitingImage = true;
            this.roomAwaitingImage = message.body().getInteger("roomId");
            logger.info("Server is now awaiting an image for room: {}", roomAwaitingImage);
            message.reply(new JsonObject().put(STATUS, SUCCESS).put("message", roomAwaitingImage));
        } else {
            message.reply(new JsonObject().put(STATUS, ERROR).put("message", roomAwaitingImage));
        }
    }

    private void handleImageUploaded(Message<JsonObject> message) {
        System.out.println("!!!! PPPP ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");

        ChatItem chatItem = Json.decodeValue(message.body().getString("chatItem"), ChatItem.class);

//        String roomName = chatItem.getRoomName();
        Integer roomId = chatItem.getRoomId();
        System.out.println("roomId : " + roomId);
        System.out.println("roomAwaitingImage : " + roomAwaitingImage);
        if (awaitingImage && roomId.equals(roomAwaitingImage)) {
            // 클라이언트에게 이미지 업로드가 서버에 의해 성공적으로 수신되었음을 알림
            logger.info("Server received image upload completion notice for room: {}", roomId);
            this.awaitingImage = false; // 리셋
            this.roomAwaitingImage = null; // 리셋

            // 여기서 '이미지 다운로드 시작'이라는 액션을 지닌 메시지를 전송함
            System.out.println("!!!! +++++++ chatItem : " + chatItem);
            ChatItem chatImageDownloadStartItem = new ChatItem();
            chatImageDownloadStartItem.setUserId(chatItem.getUserId());
            chatImageDownloadStartItem.setSenderEmail(chatItem.getSenderEmail());
//            chatImageDownloadStartItem.setRoomName(roomName);
            chatImageDownloadStartItem.setRoomId(roomId);
            chatImageDownloadStartItem.setAction(START_IMAGE_DOWNLOAD);
            chatImageDownloadStartItem.setMessage(chatItem.getMessage()); // 서버 상 파일의 이름이 담김
            // 이미지를 저장 및 처리하는 로직 (예: DB에 저장, 이미지 변환 등)
            String chatItemJson = Json.encode(chatImageDownloadStartItem);
            message.reply(new JsonObject().put(STATUS, SUCCESS).put("message", chatItemJson));
        } else {
            logger.warn("Unexpected image upload completion notice for room: {}", roomId);
        }
    }
}
