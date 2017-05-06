import com.google.common.base.Stopwatch;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.Charset;

/**
 * @author Diego Urrutia Astorga <durrutia@ucn.cl>
 * @version 20170330130700
 */
public class ProcessRequestRunnable implements Runnable {

    /**
     * Logger de la clase
     */
    private static final Logger log = LoggerFactory.getLogger(ProcessRequestRunnable.class);

    /**
     * Socket asociado al cliente.
     */
    private Socket socket;

    /**
     * Constructor
     *
     * @param socket
     */
    public ProcessRequestRunnable(final Socket socket) {
        this.socket = socket;
    }


    /**
     * When an object implementing interface <code>Runnable</code> is used
     * to create a thread, starting the thread causes the object's
     * <code>run</code> method to be called in that separately executing
     * thread.
     * <p>
     * The general contract of the method <code>run</code> is that it may
     * take any action whatsoever.
     *
     * @see Thread#run()
     */
    @Override
    public void run() {

        // Cronometro ..
        final Stopwatch stopWatch = Stopwatch.createStarted();

        log.debug("Connection from {} in port {}.", socket.getInetAddress(), socket.getPort());


        try {
            processRequest(this.socket);
        } catch (Exception ex) {
            log.error("Error", ex);
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // Nothing here
            }
        }

        log.debug("Request procesed in {}.", stopWatch);

    }

    /**
     * Procesar peticion
     *
     * @param socket
     */
    private static void processRequest(final Socket socket) throws IOException {

        // Iterador de la peticion
        final LineIterator lineIterator = IOUtils.lineIterator(socket.getInputStream(), Charset.defaultCharset());

        //Buffered Reader
        final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        // Peticion
        final String request = getRequest(bufferedReader);
        log.debug("Request detected: {}", request);

        // Output
        final OutputStream outputStream = IOUtils.buffer(socket.getOutputStream());

        log.debug("Writing data for: {}", request);

        // HTTP header
        writeHeader(outputStream);

        // Dividir el request

        final String[] request3 = StringUtils.split(request, " ");

        //Cada componente
        final String verb = request3[0];
        final String file = request3[1];
        final String version = request3[2];

        //Si es el chat
        if(StringUtils.startsWith(file, "/chat")){
            if(StringUtils.contains(verb, "post")||StringUtils.contains(verb, "POST")){
                final String payload = request3[3];
                writeChat(outputStream, verb, file, payload);
            }else{
                writeChat(outputStream, verb, file, "");
            }
        }else{
            // HTTP Body
            writeBody(outputStream, request);
        }
        //cierro el buffered reader
        //bufferedReader.close();
        // Cierro el stream
        IOUtils.closeQuietly(outputStream);

    }



    /**
     * Obtengo la linea de peticion de request.
     *
     * @param lineIterator
     * @return the request.
     */
    private static String getRequest(LineIterator lineIterator) {

        String request = null;
        int n = 0;

        while (lineIterator.hasNext()) {

            // Leo linea a linea
            final String line = lineIterator.nextLine();
            log.debug("Line {}: {}", ++n, line);

            // Guardo la peticion de la primera linea
            if (n == 1) {
                request = line;
            }

            // Termine la peticion si llegue al final de la peticion
            if (StringUtils.isEmpty(line)) {
                break;
            }

        }




        return request;
    }

    private static String getRequest(BufferedReader bufferedReader)throws IOException{
        String request = null;
        int n = 0;

        //code to read and print headers
        String headerLine = null;
        while((headerLine = bufferedReader.readLine()).length() != 0){
            log.debug("Line {}: {}", ++n, headerLine);

            // Guardo la peticion de la primera linea
            if (n == 1) {
                request = headerLine;
            }
            // Termine la peticion si llegue al final de la peticion
            if (StringUtils.isEmpty(headerLine)) {
                break;
            }

        }

        //code to read the post payload data
        StringBuilder payload = new StringBuilder();
        while(bufferedReader.ready()){
            payload.append((char) bufferedReader.read());
        }
        log.debug("Payload data is: {}", payload);

        request =  request+" "+payload.toString();

        return request;
    }
    /**
     * Escribe el encabezado del protocolo HTTP.
     *
     * @param outputStream
     * @throws IOException
     */
    private static void writeHeader(OutputStream outputStream) throws IOException {

        // Header
        IOUtils.write("HTTP/1.0 200 OK\r\n", outputStream, Charset.defaultCharset());
        IOUtils.write("Content-type: text/html\r\n", outputStream, Charset.defaultCharset());

        // end-header
        IOUtils.write("\r\n", outputStream, Charset.defaultCharset());

    }

    /**
     * Escribe el body del encabezado.
     *
     * @param outputStream
     * @param request
     */
    private static void writeBody(OutputStream outputStream, String request) throws IOException {

        // Body
        final String body = "<html><head><title>WebServer v1.0</title></head><body><h3>Result:</h3><pre>CONTENT</pre></body></html>";

        final String random = RandomStringUtils.randomAlphabetic(100);

        final String result = StringUtils.replace(body, "CONTENT", random);

        IOUtils.write(result + "\r\n", outputStream, Charset.defaultCharset());

    }

    private static void writeChat(OutputStream outputStream, String verb, String file, String payload) throws IOException {
        // archivo con la pagina principal del chat
        FileInputStream indexFile =  new FileInputStream(new File("C:\\Projects\\02.WebServer.Multithread\\WebServer\\src\\main\\resources\\index.html"));
        // obtener cuerpo de la pagina a partir del archivo
        final String indexBody = IOUtils.toString(indexFile, "UTF-8");
        //agregar ultimo mensaje al chat
        AddToChatLog(payload);
        //construir y obtener chat
        final String chatLog =  getChatLog();
        //construir body
        final String result = StringUtils.replace(indexBody, "<!--ChatLog-->", chatLog);
        IOUtils.write(result, outputStream, Charset.defaultCharset());
    }
    private static String getChatLog()throws IOException{
        String chatBody = "";
        // archivo con historial del chat
        FileReader historyFile =  new FileReader("C:\\Projects\\02.WebServer.Multithread\\WebServer\\src\\main\\resources\\chatLog.txt");
        BufferedReader bReader = new BufferedReader(historyFile);
        String line;
        int lineCounter = 0;
        while ((line = bReader.readLine())!=null){
            String userMsg = line;
            if(userMsg!=null&&lineCounter>0) {
                chatBody = chatBody
                        +"<div class='bubble-container'><span class='bubble'><img class='bubble-avatar' src='' /><div class='bubble-text'><p>"
                        +line
                        +"</p></div><span class='bubble-quote' /></span></div>";

            }
            lineCounter++;
        }



        return chatBody;

    }
    private static void AddToChatLog(String file) throws IOException{
        if(file!=null&&file!=""){
            String [] fileSplited = StringUtils.split(file,"&");
            if(fileSplited.length>1){
                String [] msg = StringUtils.split(fileSplited[1],"=");
                String message = URLDecoder.decode(msg[1], Charset.defaultCharset().name());
                String user = URLDecoder.decode(StringUtils.split(fileSplited[0],"=")[1], Charset.defaultCharset().name());
                File historyFile = new File ("C:\\Projects\\02.WebServer.Multithread\\WebServer\\src\\main\\resources\\chatLog.txt");
                // archivo con historial del chat
                FileInputStream historyFileStream =  new FileInputStream(historyFile);
                final String historyLog = IOUtils.toString(historyFileStream, "UTF-8");
                String newHistoryLog = historyLog+"\n"+user+" : "+message;
                FileOutputStream newHistoryFile = new FileOutputStream(historyFile);
                byte[] newHistoryLogBytes = newHistoryLog.getBytes();
                newHistoryFile.write(newHistoryLogBytes);
                newHistoryFile.flush();
                newHistoryFile.close();
            }
        }
    }

}
